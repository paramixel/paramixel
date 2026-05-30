/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.paramixel.api.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import nonapi.org.paramixel.FrameworkException;
import nonapi.org.paramixel.Scheduler;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.support.Arguments;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.ThrowingConsumer;

/**
 * Composite action that executes all direct children concurrently with bounded admission.
 *
 * <p>This implementation limits how many direct child branches are unfinished at once. Each direct child
 * consumes one branch slot from this Parallel, regardless of whether that child is a Step, Sequence, or
 * nested Parallel. Nested Parallel actions enforce their own limits inside their own execution state.</p>
 *
 * <p>{@link #parallelism()} is an upper bound, not a guarantee. Actual concurrency may be lower due to
 * global scheduler limits ({@code paramixel.parallelism}), nested branch contention, and normal task
 * timing.</p>
 *
 * <p>This prevents large Parallel actions from flooding the scheduler ready queue while avoiding the
 * over-aggressive branch-weighting behavior that caused child Parallel actions to reserve multiple parent
 * slots and accidentally cap overall execution.</p>
 *
 * <p>Thread-safety: instances are immutable and safe for concurrent use. Execution state is managed by
 * the internal {@code ParallelState}, which coordinates scheduling via the shared {@link Scheduler}.</p>
 *
 * <p><b>Cancellation and interruptibility:</b> When a child fails and causes the Parallel to cancel,
 * {@link Thread#interrupt()} is called on all scheduled children's executing threads. This provides
 * best-effort cancellation. Actions that do not call interruptible methods (e.g., compute-bound
 * {@link Step} actions) will continue executing until they naturally complete. Long-running leaf
 * actions should periodically check {@link Thread#isInterrupted()} or use interruptible operations
 * to support timely cancellation.</p>
 *
 * @param <T> the type accepted by child consumers
 */
public final class Parallel<T> implements Action<T> {

    private static final String KIND = "Parallel";
    private static final int UNSPECIFIED_PARALLELISM = Integer.MAX_VALUE;

    private final String name;
    private final List<Action<?>> children;
    private final int parallelism;
    private final boolean parallelismConfigured;

    private Parallel(
            final String name,
            final int parallelism,
            final List<Action<?>> children,
            final boolean parallelismConfigured) {
        Objects.requireNonNull(name, "name is null");
        this.name = Arguments.requireNonBlank(name, "name is blank");
        this.children = validateChildren(children, this);
        this.parallelism = parallelism;
        this.parallelismConfigured = parallelismConfigured;
    }

    /**
     * Creates a new spec for a Parallel action with the given name.
     *
     * @param name the action name (must not be {@code null} or blank)
     * @param <T> the type accepted by child consumers
     * @return a new spec
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static <T> Spec<T> of(final String name) {
        return new Spec<>(name);
    }

    /**
     * Returns the action name.
     *
     * @return the name, never {@code null}
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the action kind, which is always {@code "Parallel"}.
     *
     * @return the kind
     */
    @Override
    public String kind() {
        return KIND;
    }

    /**
     * Returns the declared direct-child parallelism value.
     *
     * <p>When {@link Spec#parallelism(int)} is not called, this method returns {@link
     * Integer#MAX_VALUE} as an internal sentinel. In that case, execution-time admission inherits
     * the scheduler parallelism (derived from {@code paramixel.parallelism}).
     *
     * <p>When explicitly configured, this value is the requested maximum number of direct child
     * branches that may be in-flight simultaneously, capped by scheduler parallelism
     * ({@code paramixel.parallelism}). Global scheduler parallelism is enforced separately at
     * leaf-action execution time. At the root parallel node, runner-level top-level admission may
     * also be throttled by {@code paramixel.parallelism}.</p>
     *
     * <p>This value is a cap only. Runtime concurrency is best-effort and may be lower depending on
     * global and nested contention.</p>
     *
     * @return the parallelism limit
     */
    public int parallelism() {
        return parallelism;
    }

    @Override
    public List<Action<?>> children() {
        return children;
    }

    /**
     * Executes all direct children concurrently with bounded admission.
     *
     * <p>When the mode is not {@link Mode#RUN}, children are dispatched according to
     * non-run mode semantics and no parallel scheduling occurs. Otherwise, children are
     * admitted up to the parallelism limit and the calling thread blocks until all children complete.</p>
     *
     * @param context the execution context (must not be {@code null})
     * @throws NullPointerException if {@code context} is {@code null}
     * @throws IllegalArgumentException if {@code context} is not a {@code ConcreteContext}
     */
    @Override
    public void execute(final Context context) {
        Objects.requireNonNull(context, "context is null");
        var descriptor = context.descriptor();
        var listener = context.listener();
        listener.onBeforeExecution(descriptor);
        context.setStatus(Status.RUNNING);
        try {
            var mode = descriptor.metadata().mode();
            if (mode != Mode.RUN) {
                runChildren(context, mode);
                context.setStatus(mode.toStatus());
            } else {
                context.setStatus(run(context));
            }
        } catch (Throwable t) {
            context.setStatus(Status.fromThrowable(FrameworkException.wrap(t)));
        }
        listener.onAfterExecution(descriptor);
    }

    private Status run(final Context context) {
        if (!(context instanceof ConcreteContext concreteContext)) {
            throw new IllegalArgumentException("context must be a ConcreteContext");
        }

        var descriptor = Arguments.requireInstanceOf(
                context.descriptor(), MutableDescriptor.class, "descriptor must be a MutableDescriptor");
        var children = getConcreteChildren(descriptor);
        var scheduler = concreteContext.scheduler();
        var topLevelThrottleEnabled = descriptor.parent().isEmpty() && concreteContext.hasTopLevelParallelThrottle();

        var completion = new CompletableFuture<Descriptor>();
        var state = new ParallelState(
                resolveEffectiveParallelism(scheduler),
                concreteContext,
                completion,
                children,
                scheduler,
                topLevelThrottleEnabled);

        try {
            state.drain();
            scheduler.managedJoin(completion);
        } catch (CompletionException e) {
            var cause = e.getCause();
            if (cause instanceof Error err) {
                throw err;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        }

        return Status.aggregate(descriptor.children());
    }

    private static List<MutableDescriptor> getConcreteChildren(final MutableDescriptor descriptor) {
        var children = descriptor.children();
        var result = new ArrayList<MutableDescriptor>(children.size());
        for (var child : children) {
            if (child instanceof MutableDescriptor c) {
                result.add(c);
            } else {
                throw new IllegalArgumentException("child must be MutableDescriptor");
            }
        }
        return result;
    }

    private static void runChildren(final Context context, final Mode mode) {
        if (context instanceof ConcreteContext concrete) {
            concrete.runChildren(mode);
        } else {
            throw new IllegalArgumentException("context must be a ConcreteContext");
        }
    }

    private int resolveEffectiveParallelism(final Scheduler scheduler) {
        var schedulerParallelism = scheduler.parallelism();
        if (!parallelismConfigured) {
            return schedulerParallelism;
        }
        return Math.min(parallelism, schedulerParallelism);
    }

    /**
     * Mutable execution state for a single Parallel run.
     *
     * <p>Coordinates admission, scheduling, and completion of child branches. Thread-safety is provided
     * by {@code synchronized} blocks on {@code this} combined with {@link AtomicInteger} counters for
     * lock-free reads in hot paths.</p>
     */
    private static final class ParallelState {

        private final int effectiveParallelism;
        private final ConcreteContext context;
        private final CompletableFuture<Descriptor> completion;
        private final List<MutableDescriptor> children;
        private final Scheduler scheduler;
        private final boolean topLevelThrottleEnabled;

        private final AtomicInteger scheduledCount = new AtomicInteger(0);
        private final AtomicInteger completedCount = new AtomicInteger(0);

        private volatile boolean done = false;
        private volatile boolean cancelled = false;
        private volatile Throwable firstError;
        private final List<ParallelExecutionCallback> scheduledCallbacks = new CopyOnWriteArrayList<>();

        ParallelState(
                final int configuredParallelism,
                final ConcreteContext context,
                final CompletableFuture<Descriptor> completion,
                final List<MutableDescriptor> children,
                final Scheduler scheduler,
                final boolean topLevelThrottleEnabled) {
            this.context = context;
            this.completion = completion;
            this.children = children;
            this.scheduler = scheduler;
            this.topLevelThrottleEnabled = topLevelThrottleEnabled;
            this.effectiveParallelism = Math.max(1, configuredParallelism);
        }

        void drain() {
            int startIndex;
            int scheduleCount;

            synchronized (this) {
                if (done || cancelled) {
                    return;
                }

                var currentScheduled = scheduledCount.get();
                var currentCompleted = completedCount.get();
                var inFlight = currentScheduled - currentCompleted;
                var availableSlots = effectiveParallelism - inFlight;
                var remainingChildren = children.size() - currentScheduled;
                var canSchedule = Math.min(availableSlots, remainingChildren);

                if (canSchedule <= 0) {
                    checkCompletionLocked();
                    return;
                }

                startIndex = currentScheduled;
                scheduleCount = canSchedule;
                scheduledCount.addAndGet(canSchedule);
            }

            for (var i = 0; i < scheduleCount; i++) {
                var child = children.get(startIndex + i);
                var mode = child.metadata().mode();
                var callback = new ParallelExecutionCallback(child, topLevelThrottleEnabled);
                scheduledCallbacks.add(callback);
                if (topLevelThrottleEnabled) {
                    try {
                        context.acquireTopLevelParallelPermit();
                        callback.markTopLevelPermitAcquired();
                    } catch (InterruptedException interruptedException) {
                        failScheduling(interruptedException);
                        return;
                    }
                }
                try {
                    scheduler
                            .schedule(child, mode, context, callback)
                            .whenComplete((ignored, error) -> callback.onFutureComplete(error));
                } catch (Throwable t) {
                    callback.onFutureComplete(t);
                    failScheduling(t);
                    return;
                }
            }

            checkCompletion();
        }

        private final class ParallelExecutionCallback implements Scheduler.ExecutionCallback {

            private final MutableDescriptor child;
            private final boolean usesTopLevelPermit;
            private final AtomicBoolean executionStarted = new AtomicBoolean(false);
            private final AtomicBoolean completionNotified = new AtomicBoolean(false);
            private final AtomicBoolean topLevelPermitAcquired = new AtomicBoolean(false);
            private final AtomicBoolean topLevelPermitReleased = new AtomicBoolean(false);

            ParallelExecutionCallback(final MutableDescriptor child, final boolean usesTopLevelPermit) {
                this.child = child;
                this.usesTopLevelPermit = usesTopLevelPermit;
            }

            void markTopLevelPermitAcquired() {
                if (usesTopLevelPermit) {
                    topLevelPermitAcquired.set(true);
                }
            }

            void releaseTopLevelPermitIfAcquired() {
                releaseTopLevelPermitIfNeeded();
            }

            @Override
            public void onExecutionStart() {
                executionStarted.set(true);
            }

            @Override
            public void onExecutionComplete(final Throwable error) {
                releaseTopLevelPermitIfNeeded();
                if (completionNotified.compareAndSet(false, true)) {
                    if (isCancelled() && isFrameworkInterrupted(error)) {
                        scheduledCallbacks.remove(this);
                        handleCompletion(new CancellationException("Parent Parallel was cancelled"));
                    } else {
                        scheduledCallbacks.remove(this);
                        handleCompletion(resolveCompletionError(error));
                    }
                }
            }

            void onFutureComplete(final Throwable error) {
                releaseTopLevelPermitIfNeeded();
                if (completionNotified.compareAndSet(false, true)) {
                    if (isCancelled() && isFrameworkInterrupted(error)) {
                        scheduledCallbacks.remove(this);
                        handleCompletion(new CancellationException("Parent Parallel was cancelled"));
                    } else {
                        scheduledCallbacks.remove(this);
                        handleCompletion(resolveCompletionError(error));
                    }
                }
            }

            private static boolean isFrameworkInterrupted(final Throwable error) {
                if (error instanceof InterruptedException) {
                    return true;
                }
                if (error instanceof FrameworkException fe && fe.getCause() instanceof InterruptedException) {
                    return false;
                }
                return false;
            }

            void interruptChild() {
                if (child instanceof ConcreteDescriptor concrete) {
                    concrete.interruptExecutingThread();
                }
            }

            private void releaseTopLevelPermitIfNeeded() {
                if (!usesTopLevelPermit || !topLevelPermitAcquired.get()) {
                    return;
                }
                if (topLevelPermitReleased.compareAndSet(false, true)) {
                    context.releaseTopLevelParallelPermit();
                }
            }

            private Throwable resolveCompletionError(final Throwable error) {
                if (error == null) {
                    return null;
                }
                if (!executionStarted.get()) {
                    return error;
                }
                return child.metadata().status().isTerminal() ? null : error;
            }
        }

        private void handleCompletion(final Throwable t) {
            completedCount.incrementAndGet();

            if (t != null) {
                var unwrapped = unwrap(t);
                if (unwrapped instanceof OutOfMemoryError || unwrapped instanceof StackOverflowError) {
                    completion.completeExceptionally(unwrapped);
                    done = true;
                    return;
                }
                if (firstError == null) {
                    firstError = unwrapped;
                    cancelled = true;
                    interruptScheduledChildren();
                }
            }

            checkCompletion();
            drain();
        }

        private void interruptScheduledChildren() {
            for (var callback : scheduledCallbacks) {
                callback.interruptChild();
            }
        }

        boolean isCancelled() {
            return cancelled;
        }

        private void failScheduling(final Throwable throwable) {
            if (throwable instanceof InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                var wrapped =
                        new InterruptedException("Interrupted while acquiring top-level parallel throttle permit");
                wrapped.initCause(interruptedException);
                synchronized (this) {
                    done = true;
                }
                completion.completeExceptionally(wrapped);
                return;
            }
            synchronized (this) {
                done = true;
            }
            completion.completeExceptionally(unwrap(throwable));
        }

        private void checkCompletion() {
            synchronized (this) {
                checkCompletionLocked();
            }
        }

        private void checkCompletionLocked() {
            if (done || completedCount.get() < children.size()) {
                return;
            }
            done = true;
            if (firstError != null) {
                completion.completeExceptionally(firstError);
            } else {
                completion.complete(context.descriptor());
            }
        }

        private static Throwable unwrap(final Throwable t) {
            if (t instanceof CompletionException && t.getCause() != null) {
                return unwrap(t.getCause());
            }
            if (t instanceof RuntimeException rt && rt.getCause() != null) {
                var cause = rt.getCause();
                if (UnrecoverableErrors.isUnrecoverable(cause)
                        || cause instanceof Error
                        || cause instanceof InterruptedException) {
                    return cause;
                }
            }
            return t;
        }
    }

    private static List<Action<?>> validateChildren(final List<Action<?>> children, final Action<?> self) {
        Objects.requireNonNull(children, "children is null");
        var validated = new ArrayList<Action<?>>(children.size());
        for (Action<?> child : children) {
            Objects.requireNonNull(child, "children contains null element");
            Arguments.requireTrue(child != self, "action must not add itself as a child");
            validated.add(child);
        }
        return List.copyOf(validated);
    }

    /**
     * Accumulating spec for {@link Parallel} actions.
     *
     * <p>Each spec may produce at most one {@code Parallel} instance; subsequent calls to
     * {@link #resolve()} throw {@link IllegalStateException}.</p>
     *
     * @param <T> the type accepted by child consumers
     */
    public static final class Spec<T> implements org.paramixel.api.action.Spec<T> {

        private final String name;
        private final List<Action<?>> children = new ArrayList<>();
        private int parallelism = UNSPECIFIED_PARALLELISM;
        private boolean parallelismConfigured;
        private boolean resolved;

        private Spec(final String name) {
            Objects.requireNonNull(name, "name is null");
            Arguments.requireNonBlank(name, "name is blank");
            this.name = name;
        }

        /**
         * Sets the maximum number of in-flight direct child branches.
         *
         * <p>When this method is not called, direct-child admission inherits scheduler parallelism
         * at execution time (derived from {@code paramixel.parallelism}). When configured, the
         * requested value is capped by scheduler parallelism. Global scheduler parallelism is
         * enforced separately at leaf-action execution time. At the root parallel node, runner-level
         * top-level admission may also be throttled by {@code paramixel.parallelism}.</p>
         *
         * @param parallelism the parallelism limit (must be positive)
         * @return this spec
         * @throws IllegalArgumentException if {@code parallelism} is not positive
         */
        public Spec<T> parallelism(final int parallelism) {
            ensureNotResolved();
            Arguments.requirePositive(parallelism, "parallelism must be positive, was: " + parallelism);
            this.parallelism = parallelism;
            parallelismConfigured = true;
            return this;
        }

        /**
         * Adds a child action by resolving the given spec and appending the result.
         *
         * @param spec the child action spec (must not be {@code null})
         * @return this spec
         * @throws NullPointerException if {@code spec} is {@code null}
         */
        public Spec<T> child(final org.paramixel.api.action.Spec<?> spec) {
            ensureNotResolved();
            children.add(Objects.requireNonNull(spec, "spec is null").resolve());
            return this;
        }

        /**
         * Adds a child Step action with the given name and consumer.
         *
         * @param name the child step name (must not be {@code null})
         * @param consumer the child step consumer (must not be {@code null})
         * @return this spec
         * @throws NullPointerException if any argument is {@code null}
         * <p>The consumer receives the fixture instance when this action is wrapped in an
         * {@link Instance}, or the execution {@link Context} when standalone.
         */
        public Spec<T> child(final String name, final ThrowingConsumer<? super T> consumer) {
            return child(Step.of(name, consumer));
        }

        /**
         * Adds a child Step action with a custom kind, given name, and consumer.
         *
         * @param name the child step name; must not be {@code null} or blank
         * @param kind the action kind; must not be {@code null} or blank
         * @param consumer the child step consumer; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name}, {@code kind}, or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} or {@code kind} is blank
         * <p>The consumer receives the fixture instance when this action is wrapped in an
         * {@link Instance}, or the execution {@link Context} when standalone.
         */
        public Spec<T> child(final String name, final String kind, final ThrowingConsumer<? super T> consumer) {
            return child(Step.of(name, kind, consumer));
        }

        /**
         * Adds a child action for each item in the iterable by applying the supplied mapper function.
         *
         * <p>This is a convenience method that produces the same tree as calling
         * {@link #child(org.paramixel.api.action.Spec) child(Spec)} in a for-loop. The mapper is called for
         * each item at spec-building time ({@link #resolve()}), not at execution time. An empty
         * iterable adds no children.</p>
         *
         * @param <U> the type of items in the iterable
         * @param items the items to iterate over; must not be {@code null}
         * @param mapper the function that maps each item to a child action spec; must not be
         *     {@code null}
         * @return this spec
         * @throws NullPointerException if {@code items} or {@code mapper} is {@code null}
         * @throws IllegalStateException if this spec has already been resolved
         */
        public <U> Spec<T> each(final Iterable<U> items, final Function<U, org.paramixel.api.action.Spec<?>> mapper) {
            ensureNotResolved();
            Objects.requireNonNull(items, "items is null");
            Objects.requireNonNull(mapper, "mapper is null");
            for (U item : items) {
                child(mapper.apply(item));
            }
            return this;
        }

        /**
         * Adds a child action for each item in the stream by applying the supplied mapper function.
         *
         * <p>The stream is materialized to a list immediately and then delegated to
         * {@link #each(Iterable, Function)}. The mapper is called for each item at spec-building time
         * ({@link #resolve()}), not at execution time. An empty stream adds no children.</p>
         *
         * @param <U> the type of items in the stream
         * @param items the items to iterate over; must not be {@code null}
         * @param mapper the function that maps each item to a child action spec; must not be
         *     {@code null}
         * @return this spec
         * @throws NullPointerException if {@code items} or {@code mapper} is {@code null}
         * @throws IllegalStateException if this spec has already been resolved
         */
        public <U> Spec<T> each(final Stream<U> items, final Function<U, org.paramixel.api.action.Spec<?>> mapper) {
            Objects.requireNonNull(items, "items is null");
            return each(items.toList(), mapper);
        }

        /**
         * Builds an immutable {@link Parallel} action from this spec's configuration.
         *
         * @return a new Parallel action
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Parallel<T> resolve() {
            ensureNotResolved();
            resolved = true;
            return new Parallel<>(name, parallelism, List.copyOf(children), parallelismConfigured);
        }

        private void ensureNotResolved() {
            if (resolved) {
                throw new IllegalStateException("spec already resolved");
            }
        }
    }
}

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Status;
import org.paramixel.api.ThrowingConsumer;
import org.paramixel.api.internal.AsyncScheduler;
import org.paramixel.api.internal.ConcreteExecutionContext;
import org.paramixel.api.internal.action.MutableDescriptor;
import org.paramixel.api.internal.support.Arguments;
import org.paramixel.spi.action.ExecutionContext;
import org.paramixel.spi.action.Mode;

/**
 * Composite action that executes all direct children concurrently with bounded admission.
 *
 * <p>This implementation limits how many direct child branches are unfinished at once. Each direct child
 * consumes one branch slot from this Parallel, regardless of whether that child is a Step, Sequence, or
 * nested Parallel. Nested Parallel actions enforce their own limits inside their own execution state.</p>
 *
 * <p>This prevents large Parallel actions from flooding the scheduler ready queue while avoiding the
 * over-aggressive branch-weighting behavior that caused child Parallel actions to reserve multiple parent
 * slots and accidentally cap overall execution.</p>
 *
 * <p>Thread-safety: instances are immutable and safe for concurrent use. Execution state is managed by
 * the internal {@code ParallelState}, which coordinates scheduling via the shared {@link AsyncScheduler}.</p>
 *
 * @param <T> the type accepted by child consumers
 */
public final class Parallel<T> implements Action<T> {

    private static final String KIND = "Parallel";

    private final String name;
    private final List<Action<?>> children;
    private final int parallelism;

    private Parallel(final String name, final int parallelism, final List<Action<?>> children) {
        this.name = Arguments.requireValidName(name);
        this.children = validateChildren(children, this);
        this.parallelism = parallelism;
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
     * Returns the configured parallelism limit.
     *
     * <p>This is the maximum number of direct child branches that may be in-flight simultaneously.
     * The effective parallelism at runtime is further bounded by the scheduler's global parallelism.</p>
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
     * @throws IllegalArgumentException if {@code context} is not a {@code ConcreteExecutionContext}
     */
    @Override
    public void execute(final ExecutionContext context) {
        Objects.requireNonNull(context, "context must not be null");
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
            context.setStatus(Status.fromThrowable(t));
        }
        listener.onAfterExecution(descriptor);
    }

    private Status run(final ExecutionContext context) {
        if (!(context instanceof ConcreteExecutionContext concreteContext)) {
            throw new IllegalArgumentException("context must be a ConcreteExecutionContext");
        }

        var descriptor = (MutableDescriptor) context.descriptor();
        var children = getConcreteChildren(descriptor);
        var scheduler = concreteContext.scheduler();

        var completion = new CompletableFuture<Descriptor>();
        var state = new ParallelState(parallelism, concreteContext, completion, children, scheduler);

        state.drain();

        try {
            scheduler.managedJoin(completion);
        } catch (CompletionException e) {
            var cause = e.getCause();
            if (cause instanceof Error err) {
                throw err;
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

    private static void runChildren(final ExecutionContext context, final Mode mode) {
        if (context instanceof ConcreteExecutionContext concrete) {
            concrete.runChildren(mode);
        } else {
            throw new IllegalArgumentException("context must be a ConcreteExecutionContext");
        }
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
        private final ConcreteExecutionContext context;
        private final CompletableFuture<Descriptor> completion;
        private final List<MutableDescriptor> children;
        private final AsyncScheduler scheduler;

        private final AtomicInteger scheduledCount = new AtomicInteger(0);
        private final AtomicInteger completedCount = new AtomicInteger(0);
        private final AtomicInteger admittedCount = new AtomicInteger(0);
        private final AsyncScheduler.ExecutionCallback executionCallback = new ParallelExecutionCallback();

        private volatile boolean done = false;

        ParallelState(
                final int configuredParallelism,
                final ConcreteExecutionContext context,
                final CompletableFuture<Descriptor> completion,
                final List<MutableDescriptor> children,
                final AsyncScheduler scheduler) {
            this.context = context;
            this.completion = completion;
            this.children = children;
            this.scheduler = scheduler;
            this.effectiveParallelism = Math.max(1, Math.min(configuredParallelism, scheduler.parallelism()));
        }

        void drain() {
            int startIndex;
            int scheduleCount;

            synchronized (this) {
                if (done) {
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
                scheduler.schedule(child, mode, context, executionCallback);
            }

            checkCompletion();
        }

        private final class ParallelExecutionCallback implements AsyncScheduler.ExecutionCallback {
            @Override
            public void onAdmitted() {
                admittedCount.incrementAndGet();
            }

            @Override
            public void onExecutionStart() {
                // Retained for monitoring compatibility
            }

            @Override
            public void onExecutionComplete(final Throwable error) {
                admittedCount.decrementAndGet();
                handleCompletion(error);
            }
        }

        private void handleCompletion(final Throwable t) {
            completedCount.incrementAndGet();

            if (t != null) {
                var unwrapped = unwrap(t);
                if (unwrapped instanceof OutOfMemoryError || unwrapped instanceof StackOverflowError) {
                    completion.completeExceptionally(unwrapped);
                    return;
                }
            }

            checkCompletion();
            drain();
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
            completion.complete(context.descriptor());
        }

        private static Throwable unwrap(final Throwable t) {
            if (t instanceof CompletionException && t.getCause() != null) {
                return t.getCause();
            }
            if (t instanceof RuntimeException rt && rt.getCause() != null) {
                return rt.getCause();
            }
            return t;
        }
    }

    private static List<Action<?>> validateChildren(final List<Action<?>> children, final Action<?> self) {
        Objects.requireNonNull(children, "children must not be null");
        var validated = new ArrayList<Action<?>>(children.size());
        for (Action<?> child : children) {
            Objects.requireNonNull(child, "children must not contain null elements");
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
        private int parallelism = Integer.MAX_VALUE;
        private boolean resolved;

        private Spec(final String name) {
            Objects.requireNonNull(name, "name must not be null");
            Arguments.requireNonBlank(name, "name must not be blank");
            this.name = name;
        }

        /**
         * Sets the maximum number of in-flight direct child branches.
         *
         * <p>Defaults to {@code Integer.MAX_VALUE} (unbounded). The effective parallelism at runtime
         * is further capped by the scheduler's global parallelism.</p>
         *
         * @param parallelism the parallelism limit (must be positive)
         * @return this spec
         * @throws IllegalArgumentException if {@code parallelism} is not positive
         */
        public Spec<T> parallelism(final int parallelism) {
            ensureNotResolved();
            Arguments.requirePositive(parallelism, "parallelism must be positive, was: " + parallelism);
            this.parallelism = parallelism;
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
            children.add(Objects.requireNonNull(spec, "spec must not be null").resolve());
            return this;
        }

        /**
         * Adds a child Step action with the given name and consumer.
         *
         * @param name the child step name (must not be {@code null})
         * @param consumer the child step consumer (must not be {@code null})
         * @return this spec
         * @throws NullPointerException if any argument is {@code null}
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
         */
        public Spec<T> child(final String name, final String kind, final ThrowingConsumer<? super T> consumer) {
            return child(Step.of(name, kind, consumer));
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
            return new Parallel<>(name, parallelism, List.copyOf(children));
        }

        private void ensureNotResolved() {
            if (resolved) {
                throw new IllegalStateException("spec already resolved");
            }
        }
    }
}

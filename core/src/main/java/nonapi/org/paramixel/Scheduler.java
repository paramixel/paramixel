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

package nonapi.org.paramixel;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.action.SchedulerPriorityKey;
import nonapi.org.paramixel.action.StatusAccumulator;
import nonapi.org.paramixel.exception.UserCodeException;
import nonapi.org.paramixel.listener.Listeners;
import nonapi.org.paramixel.support.Arguments;
import nonapi.org.paramixel.support.StackTracePruner;
import nonapi.org.paramixel.support.Throwables;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Assert;
import org.paramixel.api.action.Conditional;
import org.paramixel.api.action.Delay;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Repeat;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Static;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;
import org.paramixel.api.action.Until;
import org.paramixel.api.exception.FailException;

/**
 * Bounded priority scheduler for Paramixel action execution.
 *
 * <p>Execution is backed by one fixed-size worker pool bounded by
 * {@code paramixel.parallelism}. Terminal action execution is additionally bounded by permits so only
 * leaf actions consume global work capacity.
 *
 * <p>The global ready queue is ordered by immutable descriptor tree keys
 * ({@link SchedulerPriorityKey}), yielding deterministic DFS-like subtree preference
 * (continuations before lateral siblings) while preserving global and local parallelism caps.
 */
public final class Scheduler implements AutoCloseable {

    /**
     * Callback interface for lifecycle events during scheduled action execution.
     *
     * <p>Implementations are invoked by the scheduler at defined points during admission, execution
     * start, and execution completion. Callbacks are invoked on the scheduler worker thread that
     * executes the action.
     */
    public interface ExecutionCallback {

        /**
         * Called when the action has been admitted into the scheduler's running set.
         *
         * <p>This is invoked exactly once per scheduled action, before execution begins. The default
         * implementation does nothing.
         */
        default void onAdmitted() {
            // Intentionally empty
        }

        /**
         * Called just before the action's {@code perform} method is invoked.
         */
        void onExecutionStart();

        /**
         * Called after the action's execution has completed, regardless of success or failure.
         *
         * @param error the throwable that caused execution failure, or {@code null} if execution
         *     succeeded
         */
        void onExecutionComplete(Throwable error);
    }

    private static final String THREAD_NAME = "paramixel-scheduler";
    private static final long THREAD_KEEP_ALIVE_SECONDS = 30L;
    private static final long EXECUTOR_TERMINATION_TIMEOUT_SECONDS = 5L;
    private static final int DEFAULT_QUEUE_CAPACITY = 1024;
    private static final Comparator<Runnable> PRIORITY_COMPARATOR = (left, right) -> {
        if (left instanceof PrioritizedTask leftTask && right instanceof PrioritizedTask rightTask) {
            var keyComparison = leftTask.priorityKey.compareTo(rightTask.priorityKey);
            if (keyComparison != 0) {
                return keyComparison;
            }
            return Long.compare(leftTask.sequence, rightTask.sequence);
        }
        throw new ClassCastException("PRIORITY_COMPARATOR requires PrioritizedTask, got: "
                + (left != null ? left.getClass().getName() : "null")
                + " and "
                + (right != null ? right.getClass().getName() : "null"));
    };

    private final int queueCapacity;
    private final Semaphore queuePermits;
    private final Semaphore leafPermits;
    private final int parallelism;
    private final boolean allowCoreThreadTimeout;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger workerThreadCounter = new AtomicInteger();
    private final AtomicInteger runningLeafCounter = new AtomicInteger();
    private final AtomicLong taskSequence = new AtomicLong(0);
    private final ThreadLocal<Boolean> schedulerWorker = ThreadLocal.withInitial(() -> false);

    private volatile boolean closing;
    private volatile boolean failFast;
    private volatile boolean failureOccurred;
    private final ReentrantLock closingLock = new ReentrantLock();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Creates a scheduler with the given parallelism.
     *
     * <p>{@code parallelism} is the maximum number of concurrently executing leaf actions.
     *
     * @param parallelism the maximum concurrent leaf execution count (must be positive)
     * @throws IllegalArgumentException if {@code parallelism} is not positive
     */
    public Scheduler(final int parallelism) {
        this(parallelism, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Creates a scheduler with the given parallelism and queue capacity.
     *
     * <p>{@code queueCapacity} sets the initial array size for the global executor's internal
     * priority queue and determines the number of global admission permits. The actual bound on
     * queued tasks is enforced by a semaphore — {@link java.util.concurrent.PriorityBlockingQueue}
     * is unbounded and never rejects elements. If the semaphore is exhausted, subsequent submissions
     * are rejected immediately.
     *
     * @param parallelism the maximum concurrent leaf execution count (must be positive)
     * @param queueCapacity global admission permit count and initial queue allocation (must be
     *     positive)
     * @throws IllegalArgumentException if {@code parallelism} or {@code queueCapacity} is not
     *     positive
     */
    Scheduler(final int parallelism, final int queueCapacity) {
        this(parallelism, queueCapacity, true);
    }

    /**
     * Creates a scheduler with the given parallelism, queue capacity, and core thread timeout
     * policy.
     *
     * <p>When {@code allowCoreThreadTimeout} is {@code true} (the default), idle scheduler worker
     * threads are terminated after a timeout, freeing resources during quiet periods. Subsequent
     * bursts pay thread recreation cost. When {@code false}, core threads remain alive, reducing
     * burst latency at the cost of holding threads during idle periods.
     *
     * @param parallelism the maximum concurrent leaf execution count (must be positive)
     * @param queueCapacity global admission permit count and initial queue allocation (must be
     *     positive)
     * @param allowCoreThreadTimeout whether idle scheduler threads may be terminated
     * @throws IllegalArgumentException if {@code parallelism} or {@code queueCapacity} is not
     *     positive
     */
    Scheduler(final int parallelism, final int queueCapacity, final boolean allowCoreThreadTimeout) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be positive, was: " + parallelism);
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive, was: " + queueCapacity);
        }
        this.parallelism = parallelism;
        this.queueCapacity = queueCapacity;
        this.allowCoreThreadTimeout = allowCoreThreadTimeout;
        this.queuePermits = new Semaphore(queueCapacity);
        this.leafPermits = new Semaphore(parallelism);
        this.executor = createExecutor();
    }

    /**
     * Sets the fail-fast policy for this scheduler.
     *
     * <p>When {@code true} and a failure or abort occurs during execution,
     * subsequent {@link #schedule} calls for direct children of the root
     * descriptor are skipped.
     *
     * @param failFast whether to enable fail-fast behavior
     */
    void setFailFast(final boolean failFast) {
        this.failFast = failFast;
    }

    /**
     * Schedules a descriptor for execution with no lifecycle callback.
     *
     * @param descriptor the descriptor to schedule (must not be {@code null})
     * @param mode the execution mode (must not be {@code null})
     * @param parentContext the parent execution context (must not be {@code null})
     * @return a future that completes with the descriptor on success, or completes exceptionally on
     *     failure
     * @throws NullPointerException if any required argument is {@code null}
     * @see #schedule(MutableDescriptor, ExecutionMode, ConcreteContext, ExecutionCallback)
     */
    public CompletableFuture<Descriptor> schedule(
            final MutableDescriptor descriptor, final ExecutionMode mode, final ConcreteContext parentContext) {
        return schedule(descriptor, mode, parentContext, null);
    }

    /**
     * Schedules a descriptor for execution with an optional lifecycle callback.
     *
     * <p>If the scheduler is closing, returns a failed future immediately.
     *
     * @param descriptor the descriptor to schedule (must not be {@code null})
     * @param mode the execution mode (must not be {@code null})
     * @param parentContext the parent execution context (must not be {@code null})
     * @param callback optional lifecycle callback, may be {@code null}
     * @return a future that completes with the descriptor on success, or completes exceptionally on
     *     failure
     * @throws NullPointerException if {@code descriptor}, {@code mode}, or
     *     {@code parentContext} is {@code null}
     */
    public CompletableFuture<Descriptor> schedule(
            final MutableDescriptor descriptor,
            final ExecutionMode mode,
            final ConcreteContext parentContext,
            final ExecutionCallback callback) {
        Objects.requireNonNull(descriptor, "descriptor is null");
        Objects.requireNonNull(mode, "mode is null");
        Objects.requireNonNull(parentContext, "parentContext is null");

        // Freeze is idempotent and does not interact with the scheduler's closing state,
        // so it can be done outside the lock to reduce contention.
        if (!descriptor.isFrozen()) {
            descriptor.freeze();
        }

        final CompletableFuture<Descriptor> future;
        closingLock.lock();
        try {
            if (closing) {
                return CompletableFuture.failedFuture(new IllegalStateException("Scheduler is closing"));
            }

            try {
                future = descriptor.markScheduled();
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        } finally {
            closingLock.unlock();
        }

        var context = new ConcreteContext(
                parentContext.configuration(),
                parentContext.listener(),
                descriptor,
                this,
                parentContext.instanceHolder());

        if (!queuePermits.tryAcquire()) {
            var rejected = new RejectedExecutionException(
                    "Scheduler queue capacity exceeded (capacity=" + queueCapacity + ")");
            ensureTerminalStatus(context, rejected);
            completeExceptionally(descriptor, future, rejected);
            notifyRejectedCompletion(callback, rejected);
            return future;
        }

        closingLock.lock();
        try {
            if (closing) {
                queuePermits.release();
                var closingException = new IllegalStateException("Scheduler is closing");
                ensureTerminalStatus(context, closingException);
                completeExceptionally(descriptor, future, closingException);
                notifyRejectedCompletion(callback, closingException);
                return future;
            }
        } finally {
            closingLock.unlock();
        }

        var priorityKey = descriptor.schedulerPriorityKey();
        var sequence = taskSequence.getAndIncrement();
        try {
            executor.execute(new PrioritizedTask(
                    priorityKey, sequence, descriptor, context, mode, future, callback, queuePermits, this));
        } catch (RejectedExecutionException rejected) {
            queuePermits.release();
            ensureTerminalStatus(context, rejected);
            completeExceptionally(descriptor, future, rejected);
            notifyRejectedCompletion(callback, rejected);
        } catch (Throwable t) {
            queuePermits.release();
            UnrecoverableErrors.rethrowIfUnrecoverable(t);
            ensureTerminalStatus(context, t);
            completeExceptionally(descriptor, future, t);
            notifyRejectedCompletion(callback, t);
        }

        return future;
    }

    /**
     * Returns the configured global leaf parallelism.
     *
     * <p>This is the maximum number of leaf actions that may execute concurrently across all branches.
     *
     * @return the parallelism level, always positive
     */
    public int parallelism() {
        return parallelism;
    }

    /**
     * Returns the current number of leaf actions executing.
     *
     * @return the running leaf count, always non-negative
     */
    public int running() {
        return runningLeafCounter.get();
    }

    /**
     * Returns the global admission permit count, which also sets the initial array size for the
     * executor's priority queue.
     *
     * <p>This is not a hard queue bound — admission is controlled by a semaphore with this many
     * permits.
     *
     * @return configured admission permit count, always positive
     */
    public int queueCapacity() {
        return queueCapacity;
    }

    /**
     * Returns the number of queued tasks in the global executor.
     *
     * @return non-negative total queued task count
     */
    public int readyQueueSize() {
        return executor.getQueue().size();
    }

    /**
     * Returns the fair re-entrant lock associated with the given name, creating it
     * if it does not already exist.
     *
     * <p>The lock is used by {@link org.paramixel.api.action.Isolated} actions to
     * serialize execution of actions that share the same lock name.
     *
     * @param lockName the lock name; must not be {@code null}
     * @return a fair re-entrant lock for the given name; never {@code null}
     * @throws NullPointerException if {@code lockName} is {@code null}
     */
    ReentrantLock getLock(final String lockName) {
        Objects.requireNonNull(lockName, "lockName is null");
        return locks.computeIfAbsent(lockName, k -> new ReentrantLock(true));
    }

    /**
     * Blocks until the given future completes.
     *
     * @param <T> the future result type
     * @param future the future to wait on (must not be {@code null})
     * @return the result of the future
     * @throws NullPointerException if {@code future} is {@code null}
     * @throws CompletionException if the future completed exceptionally
     */
    public <T> T managedJoin(final CompletableFuture<T> future) {
        Objects.requireNonNull(future, "future is null");
        while (!future.isDone()) {
            var task = executor.getQueue().poll();
            if (task == null) {
                LockSupport.parkNanos(1_000);
            } else {
                runQueuedTask(task);
            }
        }
        return future.join();
    }

    /**
     * Blocks until any one of the given futures completes, while work-stealing from the executor
     * queue.
     *
     * <p>This method is used by the rolling-window parallel scheduler to replace completed futures
     * immediately rather than waiting for an entire batch. After this method returns, callers
     * should iterate the collection to find completed futures.
     *
     * @param <T> the future result type
     * @param futures the futures to wait on; must not be {@code null} or empty
     * @throws NullPointerException if {@code futures} is {@code null}
     * @throws IllegalArgumentException if {@code futures} is empty
     */
    public <T> void managedJoinWaitAny(final java.util.Collection<CompletableFuture<T>> futures) {
        Objects.requireNonNull(futures, "futures is null");
        if (futures.isEmpty()) {
            throw new IllegalArgumentException("futures must not be empty");
        }
        @SuppressWarnings("unchecked")
        var anyFuture = (CompletableFuture<T>) CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]));
        managedJoin(anyFuture);
    }

    /**
     * Shuts down the scheduler and waits for executor termination.
     *
     * <p>Tasks already submitted may continue running until interrupted by shutdown escalation.
     */
    @Override
    public void close() {
        closingLock.lock();
        try {
            closing = true;
            leafPermits.release(parallelism);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
                throw new IllegalStateException("Interrupted while closing scheduler", e);
            }
        } finally {
            closingLock.unlock();
        }
    }

    /**
     * Executes a descriptor synchronously on the calling thread.
     *
     * @param descriptor the descriptor to execute; must not be {@code null}
     * @param context the execution context; must not be {@code null}
     * @param mode the execution mode; must not be {@code null}
     */
    public void executeDescriptor(
            final MutableDescriptor descriptor, final ConcreteContext context, final ExecutionMode mode) {
        executeDescriptorWithCompletion(descriptor, context, mode, null, null);
    }

    private void executeScheduledDescriptor(
            final MutableDescriptor descriptor,
            final ConcreteContext context,
            final ExecutionMode mode,
            final CompletableFuture<Descriptor> future,
            final ExecutionCallback callback) {
        executeDescriptorWithCompletion(descriptor, context, mode, future, callback);
    }

    private void executeDescriptorWithCompletion(
            final MutableDescriptor descriptor,
            final ConcreteContext context,
            final ExecutionMode mode,
            final CompletableFuture<Descriptor> future,
            final ExecutionCallback callback) {
        Objects.requireNonNull(mode, "mode is null");
        Throwable executionError = null;
        Throwable callbackFailure = null;
        var leafAction = descriptor.isLeafAction();
        var acquiredPermit = false;
        var executed = false;

        try {
            if (leafAction) {
                leafPermits.acquire();
                acquiredPermit = true;
                runningLeafCounter.incrementAndGet();
            }

            if (callback != null) {
                callback.onAdmitted();
                callback.onExecutionStart();
            }

            if (descriptor instanceof ConcreteDescriptor concrete) {
                concrete.setExecutingThread(Thread.currentThread());
            }

            executed = true;
            var originalThreadName = Thread.currentThread().getName();
            try {
                Thread.currentThread().setName(Listeners.formatIdPath(descriptor));
                context.listener().onBeforeExecution(descriptor);
                descriptor.setStatus(Status.RUNNING);
                var skipExecution = false;
                if (failFast
                        && context.descriptor()
                                .parent()
                                .filter(p -> p.parent().isEmpty())
                                .isPresent()) {
                    closingLock.lock();
                    try {
                        if (failureOccurred) {
                            descriptor.setStatus(Status.skipped("fail fast"));
                            skipExecution = true;
                        }
                    } finally {
                        closingLock.unlock();
                    }
                }
                if (!skipExecution) {
                    try {
                        descriptor.setStatus(executeAction(descriptor, context, mode));
                    } catch (Throwable t) {
                        var cause = Throwables.unwrap(t);
                        if (cause instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        UnrecoverableErrors.rethrowIfUnrecoverable(cause);
                        StackTracePruner.prune(cause);
                        descriptor.setStatus(Status.fromThrowable(UserCodeException.wrap(cause)));
                    }
                }
                context.listener().onAfterExecution(descriptor);
            } finally {
                Thread.currentThread().setName(originalThreadName);
            }
        } catch (Throwable t) {
            executionError = Throwables.unwrap(t);
            if (executionError instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            UnrecoverableErrors.rethrowIfUnrecoverable(executionError);
        } finally {
            if (descriptor instanceof ConcreteDescriptor concrete) {
                concrete.setExecutingThread(null);
            }
            if (!executed) {
                ensureTerminalStatus(context, executionError);
            }
            if (acquiredPermit) {
                runningLeafCounter.decrementAndGet();
                leafPermits.release();
            }
        }

        if (executionError == null) {
            executionError = extractPostExecutionError(context);
        }

        if (failFast && executionError != null) {
            closingLock.lock();
            try {
                failureOccurred = true;
            } finally {
                closingLock.unlock();
            }
        }

        if (callback != null) {
            try {
                callback.onExecutionComplete(executionError);
            } catch (Throwable t) {
                callbackFailure = Throwables.unwrap(t);
            }
        }

        if (executionError == null) {
            completeSuccessfully(descriptor, future);
        } else {
            completeExceptionally(descriptor, future, executionError);
        }

        if (callbackFailure != null) {
            UnrecoverableErrors.rethrowIfUnrecoverable(callbackFailure);
            if (callbackFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (callbackFailure instanceof Error error) {
                throw error;
            }
            throw new CompletionException(callbackFailure);
        }
    }

    private static Status executeAction(
            final MutableDescriptor descriptor, final ConcreteContext context, final ExecutionMode mode) {
        var status =
                switch (mode) {
                    case RUN -> runAction(descriptor, context);
                    case SKIP -> skipAction(context);
                    case ABORT -> {
                        context.runChildren(ExecutionMode.ABORT);
                        yield Status.ABORTED;
                    }
                };
        if (status == null) {
            throw new IllegalStateException("Action returned null status");
        }
        if (status.isPending() || status.isRunning()) {
            throw new IllegalStateException("Action returned non-terminal status: " + status.name());
        }
        return status;
    }

    private static Status runAction(final MutableDescriptor descriptor, final ConcreteContext context) {
        var action = descriptor.action();
        if (action instanceof Step step) {
            return runStep(step, context);
        }
        if (action instanceof Assert assert_) {
            return runAssert(assert_, context);
        }
        if (action instanceof Delay delay) {
            return runDelay(delay, context);
        }
        if (action instanceof Parallel parallel) {
            return runParallel(parallel, context);
        }
        if (action instanceof Timeout timeout) {
            return runTimeout(timeout, context);
        }
        if (action instanceof Sequence sequence) {
            return runDependentChildren(context, sequence.isDependent());
        }
        if (action instanceof Repeat) {
            return runRepeat(context);
        }
        if (action instanceof Until) {
            return runUntil(context);
        }
        if (action instanceof Scope) {
            return runLifecycle(context);
        }
        if (action instanceof Static) {
            return runLifecycle(context);
        }
        if (action instanceof Instance) {
            return runInstance(context);
        }
        if (action instanceof Isolated) {
            return runIsolated(context);
        }
        if (action instanceof Conditional conditional) {
            return runConditional(conditional, context);
        }
        return runDependentChildren(context, true);
    }

    private static Status runStep(final Step step, final ConcreteContext context) {
        try {
            step.throwableConsumer().accept(context);
            return Status.PASSED;
        } catch (Throwable t) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (t instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(t);
        }
    }

    private static Status runAssert(final Assert assertAction, final ConcreteContext context) {
        try {
            assertAction.throwableConsumer().accept(context);
            return Status.PASSED;
        } catch (Throwable t) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (t instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(t);
        }
    }

    private static Status runDelay(final Delay delay, final ConcreteContext context) {
        try {
            delay.throwableConsumer().accept(context);
            return Status.PASSED;
        } catch (Throwable t) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (t instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(t);
        }
    }

    private static Status skipAction(final ConcreteContext context) {
        context.runChildren(ExecutionMode.SKIP);
        return Status.SKIPPED;
    }

    private static Status runDependentChildren(final ConcreteContext context, final boolean dependent) {
        var aggregated = new StatusAccumulator();
        var mode = ExecutionMode.RUN;

        for (var descriptor : context.descriptor().children()) {
            var childResult = context.runChild(descriptor, mode);
            aggregated.include(childResult);

            if (mode == ExecutionMode.RUN && dependent && !childResult.isPassed() && !childResult.isAborted()) {
                mode = ExecutionMode.SKIP;
            }
        }

        return aggregated.status();
    }

    private static Status runLifecycle(final ConcreteContext context) {
        var descriptor = context.descriptor();
        var aggregated = new StatusAccumulator();
        var runChildren = true;

        try {
            if (descriptor.before().isPresent()) {
                var beforeResult = context.runChild(descriptor.before().get());
                aggregated.include(beforeResult);
                if (!beforeResult.isPassed()) {
                    runChildren = false;
                }
            }

            if (runChildren) {
                descriptor.children().forEach(child -> aggregated.include(context.runChild(child)));
            } else {
                descriptor.children().forEach(child -> {
                    aggregated.include(context.runChild(child, ExecutionMode.SKIP));
                });
            }

        } finally {
            descriptor.after().ifPresent(afterDescriptor -> aggregated.include(context.runChild(afterDescriptor)));
        }

        return aggregated.status();
    }

    private static Status runInstance(final ConcreteContext context) {
        return runLifecycle(context.withInstanceHolder(new InstanceHolder()));
    }

    private static Status runIsolated(final ConcreteContext context) {
        var isolated = (Isolated) context.descriptor().action();
        var lock = context.scheduler().getLock(isolated.lockName());
        lock.lock();
        try {
            var child = Arguments.requireInstanceOf(
                    context.descriptor().children().get(0),
                    MutableDescriptor.class,
                    "child descriptor must be a MutableDescriptor");
            context.runChild(child, ExecutionMode.RUN);
            return child.status();
        } finally {
            lock.unlock();
        }
    }

    private static Status runConditional(final Conditional conditional, final ConcreteContext context) {
        boolean conditionResult;
        try {
            conditionResult = conditional.condition().test(context);
        } catch (Throwable t) {
            context.descriptor().setStatus(Status.failed("condition evaluation failed: " + t.getMessage(), t));
            context.runChildren(ExecutionMode.SKIP);
            return Status.FAILED;
        }

        if (!conditionResult) {
            context.descriptor().setStatus(Status.skipped(conditional.reason()));
            context.runChildren(ExecutionMode.SKIP);
            return Status.SKIPPED;
        }

        var child = Arguments.requireInstanceOf(
                context.descriptor().children().get(0),
                MutableDescriptor.class,
                "child descriptor must be a MutableDescriptor");
        context.runChild(child, ExecutionMode.RUN);
        return child.status();
    }

    private static Status runRepeat(final ConcreteContext context) {
        var aggregated = new StatusAccumulator();
        for (var child : context.descriptor().children()) {
            aggregated.include(context.runChild(child));
        }
        return aggregated.status();
    }

    private static Status runUntil(final ConcreteContext context) {
        var descriptor = context.descriptor();
        var untilAction = (Until) descriptor.action();
        var children = descriptor.children();

        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            var childResult = context.runChild(child, ExecutionMode.RUN);

            if (childResult.isAborted()) {
                for (int j = i + 1; j < children.size(); j++) {
                    context.runChild(children.get(j), ExecutionMode.SKIP);
                }
                return Status.ABORTED;
            }

            boolean satisfied;
            if (untilAction.until().isPresent()) {
                satisfied = evaluateUntilPredicate(untilAction.until().get(), context);
            } else {
                satisfied = childResult.isPassed();
            }

            if (satisfied) {
                for (int j = i + 1; j < children.size(); j++) {
                    context.runChild(children.get(j), ExecutionMode.SKIP);
                }
                return Status.PASSED;
            }
        }

        return Status.FAILED;
    }

    private static boolean evaluateUntilPredicate(
            final java.util.function.Predicate<org.paramixel.api.Context> predicate, final ConcreteContext context) {
        try {
            return predicate.test(context);
        } catch (Throwable t) {
            return false;
        }
    }

    private static Status runParallel(final Parallel parallel, final ConcreteContext context) {
        var descriptor = context.descriptor();
        var children = descriptor.children();
        if (children.isEmpty()) {
            return Status.PASSED;
        }
        var effectiveParallelism =
                Math.min(parallel.parallelism(), context.scheduler().parallelism());
        effectiveParallelism = Math.max(1, effectiveParallelism);

        var activeFutures = new ArrayDeque<CompletableFuture<Descriptor>>();
        var childIterator = children.iterator();

        // Fill initial window
        for (var i = 0; i < effectiveParallelism && childIterator.hasNext(); i++) {
            var child = Arguments.requireInstanceOf(
                    childIterator.next(), MutableDescriptor.class, "child must be a MutableDescriptor");
            activeFutures.add(context.scheduleAsync(child));
        }

        // Rolling window: replace completed futures immediately
        while (!activeFutures.isEmpty()) {
            try {
                context.scheduler().managedJoinWaitAny(activeFutures);
            } catch (CompletionException ignored) {
                // At least one future completed exceptionally — find it below
            }
            // Find any completed future (successfully or exceptionally) and remove it
            CompletableFuture<Descriptor> completedFuture = null;
            for (var future : activeFutures) {
                if (future.isDone()) {
                    completedFuture = future;
                    break;
                }
            }
            if (completedFuture == null) {
                // Should not happen — managedJoinWaitAny returned, so a future must be done
                break;
            }
            activeFutures.remove(completedFuture);

            // Schedule next child if available
            if (childIterator.hasNext()) {
                var child = Arguments.requireInstanceOf(
                        childIterator.next(), MutableDescriptor.class, "child must be a MutableDescriptor");
                activeFutures.add(context.scheduleAsync(child));
            }
        }

        // Aggregate statuses from all children (read descriptor stored status, not future)
        var aggregated = new StatusAccumulator();
        for (var child : children) {
            aggregated.include(child);
        }
        return aggregated.status();
    }

    private static Status runTimeout(final Timeout timeout, final ConcreteContext context) {
        var childDescriptor = Arguments.requireInstanceOf(
                context.descriptor().children().get(0),
                MutableDescriptor.class,
                "child descriptor must be a MutableDescriptor");

        var childFuture = context.scheduleAsync(childDescriptor);
        var timedFuture = childFuture.orTimeout(timeout.timeout().toMillis(), TimeUnit.MILLISECONDS);

        try {
            context.scheduler().managedJoin(timedFuture);
            return childDescriptor.status();
        } catch (CompletionException e) {
            var cause = e.getCause();
            if (cause instanceof TimeoutException) {
                return handleTimeout(timeout, childDescriptor);
            }
            if (cause instanceof Error err) {
                throw err;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        }
    }

    private static Status handleTimeout(final Timeout timeout, final MutableDescriptor childDescriptor) {
        if (!childDescriptor.isCompleted()) {
            childDescriptor.interruptExecutingThread();
            childDescriptor.setStatus(
                    Status.failed("timed out after " + timeout.timeout().toMillis() + " ms"));
            childDescriptor.completeFuture();
        }
        return Status.FAILED;
    }

    private static void ensureTerminalStatus(final ConcreteContext context, final Throwable throwable) {
        try {
            var descriptor = context.descriptor();
            var status = descriptor.status();
            if (status.isPending()) {
                descriptor.setStatus(Status.RUNNING);
                status = descriptor.status();
            }
            if (status.isPending() || status.isRunning()) {
                descriptor.setStatus(failedStatus(throwable));
            }
        } catch (Exception ignored) {
        }
    }

    private static Status failedStatus(final Throwable throwable) {
        var failure = throwable != null ? throwable : new IllegalStateException("execution failed before action work");
        StackTracePruner.prune(failure);
        return Status.failed(failure.getMessage() != null ? failure.getMessage() : "action failed", failure);
    }

    private static Throwable extractPostExecutionError(final ConcreteContext context) {
        var status = context.descriptor().status();
        if (status.isFailed() || status.isAborted()) {
            return status.throwable().orElse(new FailException(status.message().orElse("action failed")));
        }
        return null;
    }

    private static void completeSuccessfully(
            final MutableDescriptor descriptor, final CompletableFuture<Descriptor> future) {
        if (future != null) {
            future.complete(descriptor);
        } else {
            descriptor.completeFuture();
        }
    }

    private static void completeExceptionally(
            final MutableDescriptor descriptor, final CompletableFuture<Descriptor> future, final Throwable throwable) {
        if (future != null) {
            future.completeExceptionally(throwable);
        } else {
            descriptor.completeFutureExceptionally(throwable);
        }
    }

    private static void notifyRejectedCompletion(final ExecutionCallback callback, final Throwable error) {
        if (callback == null) {
            return;
        }
        try {
            callback.onExecutionComplete(error);
        } catch (Throwable callbackFailure) {
            UnrecoverableErrors.rethrowIfUnrecoverable(Throwables.unwrap(callbackFailure));
        }
    }

    private ThreadPoolExecutor createExecutor() {
        var executor = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(queueCapacity, PRIORITY_COMPARATOR),
                runnable -> {
                    Runnable lifecycleTrackedRunnable = () -> {
                        schedulerWorker.set(true);
                        try {
                            runnable.run();
                        } finally {
                            schedulerWorker.remove();
                        }
                    };
                    var thread = new Thread(
                            lifecycleTrackedRunnable, THREAD_NAME + "-" + workerThreadCounter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(allowCoreThreadTimeout);
        return executor;
    }

    private void runQueuedTask(final Runnable task) {
        var wasSchedulerWorker = Boolean.TRUE.equals(schedulerWorker.get());
        schedulerWorker.set(true);
        try {
            task.run();
        } finally {
            if (wasSchedulerWorker) {
                schedulerWorker.set(true);
            } else {
                schedulerWorker.remove();
            }
        }
    }

    /**
     * Wrapper that applies descriptor-key ordering to a task and holds execution fields directly
     * to avoid per-task lambda allocation.
     */
    private static final class PrioritizedTask implements Runnable {
        private final SchedulerPriorityKey priorityKey;
        private final long sequence;
        private final MutableDescriptor descriptor;
        private final ConcreteContext context;
        private final ExecutionMode mode;
        private final CompletableFuture<Descriptor> future;
        private final ExecutionCallback callback;
        private final Semaphore queuePermits;
        private final Scheduler scheduler;

        PrioritizedTask(
                final SchedulerPriorityKey priorityKey,
                final long sequence,
                final MutableDescriptor descriptor,
                final ConcreteContext context,
                final ExecutionMode mode,
                final CompletableFuture<Descriptor> future,
                final ExecutionCallback callback,
                final Semaphore queuePermits,
                final Scheduler scheduler) {
            this.priorityKey = Objects.requireNonNull(priorityKey, "priorityKey is null");
            this.sequence = sequence;
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor is null");
            this.context = Objects.requireNonNull(context, "context is null");
            this.mode = Objects.requireNonNull(mode, "mode is null");
            this.future = future;
            this.callback = callback;
            this.queuePermits = Objects.requireNonNull(queuePermits, "queuePermits is null");
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null");
        }

        @Override
        public void run() {
            queuePermits.release();
            scheduler.executeScheduledDescriptor(descriptor, context, mode, future, callback);
        }
    }
}

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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.action.SchedulerPriorityKey;
import nonapi.org.paramixel.support.Throwables;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Mode;
import org.paramixel.api.exception.FailException;

/**
 * Bounded depth-aware scheduler for Paramixel action execution.
 *
 * <p>Each descriptor depth is backed by a fixed-size worker pool bounded by
 * {@code paramixel.parallelism}. Global leaf execution is additionally bounded by permits so only
 * leaf actions consume global work capacity. This prevents unbounded worker growth while allowing
 * nested {@code Parallel} nodes to progress without same-depth starvation.
 *
 * <p>Within each depth queue, ready tasks are ordered by immutable descriptor tree keys
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
         * Called just before the action's {@code execute} method is invoked.
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

    private final ConcurrentHashMap<Integer, ThreadPoolExecutor> depthExecutors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Semaphore> depthQueuePermits = new ConcurrentHashMap<>();
    private final int queueCapacity;
    private final Semaphore leafPermits;
    private final int parallelism;
    private final AtomicInteger workerThreadCounter = new AtomicInteger();
    private final AtomicInteger activeThreadCounter = new AtomicInteger();
    private final AtomicInteger runningLeafCounter = new AtomicInteger();
    private final AtomicLong taskSequence = new AtomicLong(0);

    private volatile boolean closing;
    private final ReentrantLock closingLock = new ReentrantLock();

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
     * Creates a scheduler with the given parallelism.
     *
     * <p>{@code queueCapacity} sets the initial array size for each depth executor's internal
     * priority queue and determines the number of admission permits per depth. The actual bound on
     * queued tasks is enforced by a semaphore — {@link java.util.concurrent.PriorityBlockingQueue}
     * is unbounded and never rejects elements. If the semaphore is exhausted, subsequent submissions
     * for that depth are rejected immediately.
     *
     * @param parallelism the maximum concurrent leaf execution count (must be positive)
     * @param queueCapacity per-depth admission permit count and initial queue allocation (must be
     *     positive)
     * @throws IllegalArgumentException if {@code parallelism} or {@code queueCapacity} is not
     *     positive
     */
    Scheduler(final int parallelism, final int queueCapacity) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be positive, was: " + parallelism);
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive, was: " + queueCapacity);
        }
        this.parallelism = parallelism;
        this.queueCapacity = queueCapacity;
        this.leafPermits = new Semaphore(parallelism);
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
     * @see #schedule(MutableDescriptor, Mode, ConcreteContext, ExecutionCallback)
     */
    public CompletableFuture<Descriptor> schedule(
            final MutableDescriptor descriptor, final Mode mode, final ConcreteContext parentContext) {
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
     * @throws NullPointerException if {@code descriptor}, {@code mode}, or {@code parentContext} is
     *     {@code null}
     */
    public CompletableFuture<Descriptor> schedule(
            final MutableDescriptor descriptor,
            final Mode mode,
            final ConcreteContext parentContext,
            final ExecutionCallback callback) {
        Objects.requireNonNull(descriptor, "descriptor is null");
        Objects.requireNonNull(mode, "mode is null");
        Objects.requireNonNull(parentContext, "parentContext is null");

        final CompletableFuture<Descriptor> future;
        closingLock.lock();
        try {
            if (closing) {
                return CompletableFuture.failedFuture(new IllegalStateException("Scheduler is closing"));
            }

            if (!descriptor.isFrozen()) {
                descriptor.freeze();
            }

            try {
                future = descriptor.markScheduled(mode);
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

        var depth = descriptor.depth();
        @SuppressWarnings("resource")
        var depthExecutor = depthExecutor(depth);
        var queuePermit = depthQueuePermit(depth);
        var acquiredPermit = false;
        if (!queuePermit.tryAcquire()) {
            var rejected = new RejectedExecutionException(
                    "Scheduler queue capacity exceeded at depth " + depth + " (capacity=" + queueCapacity + ")");
            ensureTerminalStatus(context, rejected);
            completeExceptionally(descriptor, future, rejected);
            notifyRejectedCompletion(callback, rejected);
            return future;
        }
        acquiredPermit = true;

        var priorityKey = descriptor.schedulerPriorityKey();
        var sequence = taskSequence.getAndIncrement();
        try {
            depthExecutor.execute(new PrioritizedTask(
                    () -> {
                        queuePermit.release();
                        executeScheduledDescriptor(descriptor, context, future, callback);
                    },
                    priorityKey,
                    sequence));
        } catch (RejectedExecutionException rejected) {
            if (acquiredPermit) {
                queuePermit.release();
            }
            ensureTerminalStatus(context, rejected);
            completeExceptionally(descriptor, future, rejected);
            notifyRejectedCompletion(callback, rejected);
        } catch (Throwable t) {
            if (acquiredPermit) {
                queuePermit.release();
            }
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
     * Returns the per-depth admission permit count, which also sets the initial array size for each
     * depth executor's priority queue.
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
     * Returns the number of queued tasks across all depth executors.
     *
     * @return non-negative total queued task count
     */
    public int readyQueueSize() {
        return depthExecutors.values().stream()
                .mapToInt(executor -> executor.getQueue().size())
                .sum();
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
        return future.join();
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
            List<ExecutorService> executors = List.copyOf(depthExecutors.values());
            leafPermits.release(parallelism);
            executors.forEach(ExecutorService::shutdown);
            var executorsList = executors;
            try {
                var allTerminated = true;
                for (var executor : executorsList) {
                    if (!executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        allTerminated = false;
                    }
                }
                if (!allTerminated) {
                    executorsList.forEach(ExecutorService::shutdownNow);
                    for (var executor : executorsList) {
                        executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executorsList.forEach(ExecutorService::shutdownNow);
                throw new IllegalStateException("Interrupted while closing scheduler", e);
            } finally {
                depthExecutors.clear();
                depthQueuePermits.clear();
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
     */
    public void executeDescriptor(final MutableDescriptor descriptor, final ConcreteContext context) {
        executeDescriptorWithCompletion(descriptor, context, null, null);
    }

    private void executeScheduledDescriptor(
            final MutableDescriptor descriptor,
            final ConcreteContext context,
            final CompletableFuture<Descriptor> future,
            final ExecutionCallback callback) {
        executeDescriptorWithCompletion(descriptor, context, future, callback);
    }

    private void executeDescriptorWithCompletion(
            final MutableDescriptor descriptor,
            final ConcreteContext context,
            final CompletableFuture<Descriptor> future,
            final ExecutionCallback callback) {
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
            descriptor.action().execute(context);
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

    private static void ensureTerminalStatus(final ConcreteContext context, final Throwable throwable) {
        try {
            var status = context.descriptor().metadata().status();
            if (status.isPending()) {
                context.setStatus(Status.RUNNING);
                status = context.descriptor().metadata().status();
            }
            if (!status.isTerminal()) {
                context.setStatus(failedStatus(throwable));
            }
        } catch (Exception ignored) {
        }
    }

    private static Status failedStatus(final Throwable throwable) {
        var failure =
                throwable != null ? throwable : new IllegalStateException("execution failed before action.execute()");
        return Status.failed(failure.getMessage() != null ? failure.getMessage() : "action failed", failure);
    }

    private static Throwable extractPostExecutionError(final ConcreteContext context) {
        var status = context.descriptor().metadata().status();
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

    private ThreadPoolExecutor depthExecutor(final int depth) {
        if (closing) {
            throw new IllegalStateException("Scheduler is closing");
        }
        return depthExecutors.computeIfAbsent(depth, this::createDepthExecutor);
    }

    private Semaphore depthQueuePermit(final int depth) {
        return depthQueuePermits.computeIfAbsent(depth, ignored -> new Semaphore(queueCapacity));
    }

    private ThreadPoolExecutor createDepthExecutor(final int depth) {
        var executor = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(queueCapacity, PRIORITY_COMPARATOR),
                runnable -> {
                    Runnable lifecycleTrackedRunnable = () -> {
                        activeThreadCounter.incrementAndGet();
                        try {
                            runnable.run();
                        } finally {
                            activeThreadCounter.decrementAndGet();
                        }
                    };
                    var thread = new Thread(
                            lifecycleTrackedRunnable,
                            THREAD_NAME + "-d" + depth + "-" + workerThreadCounter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * Wrapper that applies descriptor-key ordering to a task.
     */
    private static final class PrioritizedTask implements Runnable {
        private final Runnable delegate;
        private final SchedulerPriorityKey priorityKey;
        private final long sequence;

        PrioritizedTask(final Runnable delegate, final SchedulerPriorityKey priorityKey, final long sequence) {
            this.delegate = delegate;
            this.priorityKey = Objects.requireNonNull(priorityKey, "priorityKey is null");
            this.sequence = sequence;
        }

        @Override
        public void run() {
            delegate.run();
        }
    }
}

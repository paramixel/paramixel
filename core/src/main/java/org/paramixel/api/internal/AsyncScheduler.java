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

package org.paramixel.api.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.action.Descriptor;
import org.paramixel.api.internal.action.MutableDescriptor;
import org.paramixel.api.internal.action.SchedulerPriorityKey;
import org.paramixel.api.internal.support.UnrecoverableErrors;
import org.paramixel.spi.action.Mode;

/**
 * Bounded work-stealing scheduler for Paramixel action execution.
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li>Global execution admission is controlled by {@code running < parallelism}.</li>
 *   <li>Ready work is ordered by descriptor document path.</li>
 *   <li>Scheduler workers waiting in {@link #managedJoin(CompletableFuture)} cooperatively execute ready work.</li>
 *   <li>Inline cooperative work invokes {@link ExecutionCallback#onAdmitted()} exactly like executor-admitted work.</li>
 *   <li>Inline cooperative work preserves the original logical-admission accounting model:
 *       {@code pollReadyForCurrentWorker()} increments {@code running} and
 *       {@code executeInlineTask(...)} decrements it.</li>
 *   <li>Executor submissions happen outside the scheduler monitor.</li>
 * </ul>
 *
 * <p>Thread-safety: all public methods are safe for concurrent use. Internal state is guarded by
 * {@code synchronized} blocks on {@code this} and {@code volatile} fields where appropriate.</p>
 */
public final class AsyncScheduler implements AutoCloseable {

    private static final String INTERRUPTED_MANAGED_JOIN_MESSAGE = "Interrupted while waiting for scheduler work";

    /**
     * Callback interface for lifecycle events during scheduled action execution.
     *
     * <p>Implementations are invoked by the scheduler at defined points during admission, execution
     * start, and execution completion. Callbacks are invoked on the scheduler worker thread that
     * executes the action.</p>
     */
    public interface ExecutionCallback {

        /**
         * Called when the action has been admitted into the scheduler's running set.
         *
         * <p>This is invoked exactly once per scheduled action, before execution begins. The default
         * implementation does nothing.</p>
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
    private static final long EXECUTOR_TERMINATION_TIMEOUT_SECONDS = 5L;
    private static final int DEFAULT_QUEUE_CAPACITY = 1024;

    private final ThreadPoolExecutor executorService;
    private final int parallelism;
    private final int queueCapacity;
    private final ThreadLocal<Boolean> schedulerWorker = ThreadLocal.withInitial(() -> false);

    private volatile boolean closing;

    private int running = 0;
    private long sequence = 0L;
    private final PriorityQueue<RunnableTask> ready = new PriorityQueue<>();

    /**
     * Creates a scheduler with the given parallelism and default queue capacity.
     *
     * @param parallelism the maximum number of concurrent executions (must be positive)
     * @throws IllegalArgumentException if {@code parallelism} is not positive
     */
    public AsyncScheduler(final int parallelism) {
        this(parallelism, DEFAULT_QUEUE_CAPACITY);
    }

    AsyncScheduler(final int parallelism, final int queueCapacity) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be positive, was: " + parallelism);
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive, was: " + queueCapacity);
        }
        this.parallelism = parallelism;
        this.queueCapacity = queueCapacity;
        this.executorService = createExecutorService(parallelism);
    }

    /**
     * A schedulable task wrapping a descriptor, its execution context, and callback.
     *
     * @param descriptor the concrete descriptor to execute
     * @param context the execution context for this task
     * @param future the future to complete when execution finishes
     * @param priority the precomputed descriptor priority key for ordering
     * @param sequence the monotonic sequence number for FIFO tie-breaking
     * @param callback optional lifecycle callback, may be {@code null}
     */
    private record RunnableTask(
            MutableDescriptor descriptor,
            ConcreteExecutionContext context,
            CompletableFuture<Descriptor> future,
            SchedulerPriorityKey priority,
            long sequence,
            ExecutionCallback callback)
            implements Comparable<RunnableTask> {

        @Override
        public int compareTo(final RunnableTask other) {
            var priorityComparison = priority.compareTo(other.priority);
            if (priorityComparison != 0) {
                return priorityComparison;
            }
            return Long.compare(sequence, other.sequence);
        }
    }

    /**
     * Schedules a descriptor for execution with no lifecycle callback.
     *
     * @param descriptor the descriptor to schedule (must not be {@code null})
     * @param mode the execution mode (must not be {@code null})
     * @param parentContext the parent execution context (must not be {@code null})
     * @return a future that completes with the descriptor on success, or completes exceptionally on failure
     * @throws NullPointerException if any required argument is {@code null}
     * @see #schedule(MutableDescriptor, Mode, ConcreteExecutionContext, ExecutionCallback)
     */
    public CompletableFuture<Descriptor> schedule(
            final MutableDescriptor descriptor, final Mode mode, final ConcreteExecutionContext parentContext) {
        return schedule(descriptor, mode, parentContext, null);
    }

    /**
     * Schedules a descriptor for execution with an optional lifecycle callback.
     *
     * <p>If the scheduler is closing, returns a failed future immediately. When the scheduler is
     * single-threaded and the caller is already a scheduler worker, the descriptor is executed inline
     * on the calling thread.</p>
     *
     * @param descriptor the descriptor to schedule (must not be {@code null})
     * @param mode the execution mode (must not be {@code null})
     * @param parentContext the parent execution context (must not be {@code null})
     * @param callback optional lifecycle callback, may be {@code null}
     * @return a future that completes with the descriptor on success, or completes exceptionally on failure
     * @throws NullPointerException if {@code descriptor}, {@code mode}, or {@code parentContext} is {@code null}
     */
    public CompletableFuture<Descriptor> schedule(
            final MutableDescriptor descriptor,
            final Mode mode,
            final ConcreteExecutionContext parentContext,
            final ExecutionCallback callback) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(parentContext, "parentContext must not be null");

        if (closing) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scheduler is closing"));
        }

        if (!descriptor.isFrozen()) {
            descriptor.freeze();
        }

        final CompletableFuture<Descriptor> future;
        try {
            future = descriptor.markScheduled(mode);
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }

        var context = new ConcreteExecutionContext(
                parentContext.configuration(),
                parentContext.listener(),
                descriptor,
                this,
                parentContext.instanceHolder());

        if (parallelism == 1 && schedulerWorker.get()) {
            executeDescriptorInline(descriptor, context, future, callback);
            return future;
        }

        try {
            var toSubmit = enqueueAndDrain(new RunnableTask(
                    descriptor, context, future, descriptor.schedulerPriorityKey(), nextSequence(), callback));
            submitAdmittedTasks(toSubmit);
        } catch (CompletionException e) {
            future.completeExceptionally(e.getCause() != null ? e.getCause() : e);
        } catch (Throwable t) {
            UnrecoverableErrors.rethrowIfUnrecoverable(t);
            future.completeExceptionally(t);
        }

        return future;
    }

    /**
     * Returns the configured global scheduler parallelism.
     *
     * <p>This is the maximum number of actions that may execute concurrently across all branches.
     * Parallel actions use this value to compute effective branch capacity.</p>
     *
     * @return the parallelism level, always positive
     */
    public int parallelism() {
        return parallelism;
    }

    /**
     * Returns the number of actions currently executing.
     *
     * @return the running count, always non-negative
     */
    public synchronized int running() {
        return running;
    }

    /**
     * Returns the maximum number of tasks the ready queue can hold before backpressure is applied.
     *
     * @return the queue capacity, always positive
     */
    public int queueCapacity() {
        return queueCapacity;
    }

    /**
     * Returns the number of tasks waiting in the ready queue for admission.
     *
     * @return the ready queue size, always non-negative
     */
    public synchronized int readyQueueSize() {
        return ready.size();
    }

    /**
     * Returns the number of tasks queued in the underlying executor's work queue.
     *
     * @return the executor queue size, always non-negative
     */
    public int executorQueueSize() {
        return executorService.getQueue().size();
    }

    private List<RunnableTask> enqueueAndDrain(final RunnableTask task) {
        synchronized (this) {
            while (ready.size() >= queueCapacity && !closing) {
                /*
                 * Non-worker callers can safely wait for ready queue capacity.
                 * Worker callers should normally not reach this path because Parallel now applies backpressure.
                 */
                waitForReadyCapacity();
            }

            if (closing) {
                task.future().completeExceptionally(new IllegalStateException("Scheduler is closing"));
                return List.of();
            }

            ready.add(task);
            if (running >= parallelism) {
                notifyAll();
            }
            return drainLocked();
        }
    }

    private List<RunnableTask> drainLocked() {
        if (running >= parallelism || ready.isEmpty()) {
            return List.of();
        }

        var admitted = new ArrayList<RunnableTask>();
        while (running < parallelism && !ready.isEmpty()) {
            var task = ready.remove();
            running++;
            admitted.add(task);
        }

        notifyAll();
        return admitted;
    }

    private void submitAdmittedTasks(final List<RunnableTask> tasks) {
        for (var task : tasks) {
            notifyAdmitted(task);
            try {
                executorService.execute(() -> executeTask(task));
            } catch (RejectedExecutionException e) {
                handleRejectedAdmittedTask(task, e);
            }
        }
    }

    private void handleRejectedAdmittedTask(final RunnableTask task, final RejectedExecutionException rejected) {
        task.future().completeExceptionally(rejected);

        List<RunnableTask> toSubmit;
        synchronized (this) {
            running--;
            toSubmit = drainLocked();
        }
        submitAdmittedTasks(toSubmit);
    }

    private void executeTask(final RunnableTask task) {
        schedulerWorker.set(true);
        try {
            executeDescriptorWithCompletion(task.descriptor(), task.context(), task.future(), task.callback());
        } finally {
            schedulerWorker.remove();

            List<RunnableTask> toSubmit;
            synchronized (this) {
                running--;
                toSubmit = drainLocked();
            }
            submitAdmittedTasks(toSubmit);
        }
    }

    /**
     * Blocks until the given future completes, cooperatively executing ready work on the calling thread
     * if it is a scheduler worker.
     *
     * <p>Non-scheduler-worker threads block via {@link CompletableFuture#join()}. Scheduler-worker threads
     * poll the ready queue and execute tasks inline to avoid deadlock and improve throughput.</p>
     *
     * @param <T> the future result type
     * @param future the future to wait on (must not be {@code null})
     * @return the result of the future
     * @throws NullPointerException if {@code future} is {@code null}
     * @throws CompletionException if the future completed exceptionally or the thread was interrupted
     */
    public <T> T managedJoin(final CompletableFuture<T> future) {
        Objects.requireNonNull(future, "future must not be null");

        if (!schedulerWorker.get()) {
            return future.join();
        }

        future.whenComplete((ignored, error) -> signalManagedJoinWaiters());

        while (!future.isDone()) {
            var task = pollReadyForCurrentWorker();
            if (task != null) {
                executeInlineTask(task);
            } else {
                waitForManagedJoinSignal(future);
            }
        }

        return future.join();
    }

    /**
     * Shuts down the scheduler, cancelling all queued tasks and terminating the executor.
     *
     * <p>Queued tasks are completed exceptionally with an {@link IllegalStateException}. The executor is
     * given up to 5 seconds to terminate gracefully before being forced shutdown. If termination fails
     * or the thread is interrupted, an {@link IllegalStateException} is thrown.</p>
     */
    @Override
    public void close() {
        List<RunnableTask> cancelled;
        synchronized (this) {
            closing = true;
            cancelled = new ArrayList<>(ready);
            ready.clear();
            notifyAll();
        }

        for (var task : cancelled) {
            task.future().completeExceptionally(new IllegalStateException("Scheduler is closing"));
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Scheduler executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
            throw new IllegalStateException("Interrupted while closing scheduler", e);
        }
    }

    void executeDescriptor(final MutableDescriptor descriptor, final ConcreteExecutionContext context) {
        executeDescriptorWithCompletion(descriptor, context, null, null);
    }

    private void executeInlineTask(final RunnableTask task) {
        try {
            executeDescriptorWithCompletion(task.descriptor(), task.context(), task.future(), task.callback());
        } finally {
            List<RunnableTask> toSubmit;
            synchronized (this) {
                running--;
                toSubmit = drainLocked();
            }
            submitAdmittedTasks(toSubmit);
        }
    }

    private RunnableTask pollReadyForCurrentWorker() {
        RunnableTask task;
        synchronized (this) {
            task = ready.poll();
            if (task == null) {
                return null;
            }
            running++;
            notifyAll();
        }

        notifyAdmitted(task);
        return task;
    }

    private void notifyAdmitted(final RunnableTask task) {
        if (task.callback() != null) {
            task.callback().onAdmitted();
        }
    }

    private void executeDescriptorWithCompletion(
            final MutableDescriptor descriptor,
            final ConcreteExecutionContext context,
            final CompletableFuture<Descriptor> future,
            final ExecutionCallback callback) {
        Throwable executionError = null;
        boolean started = false;
        try {
            if (callback != null) {
                callback.onExecutionStart();
                started = true;
            }
            descriptor.action().execute(context);
            descriptor.completeFuture();
            if (future != null) {
                future.complete(descriptor);
            }
        } catch (Throwable t) {
            executionError = t;
            var failure = unwrap(t);
            UnrecoverableErrors.rethrowIfUnrecoverable(failure);
            descriptor.completeFutureExceptionally(failure);
            if (future != null) {
                future.completeExceptionally(failure);
            }
        } finally {
            if (started && callback != null) {
                callback.onExecutionComplete(executionError);
            }
        }
    }

    private void executeDescriptorInline(
            final MutableDescriptor descriptor,
            final ConcreteExecutionContext context,
            final CompletableFuture<Descriptor> future,
            final ExecutionCallback callback) {
        if (callback != null) {
            callback.onAdmitted();
        }
        executeDescriptorWithCompletion(descriptor, context, future, callback);
    }

    private synchronized long nextSequence() {
        return sequence++;
    }

    private void waitForManagedJoinSignal(final CompletableFuture<?> future) {
        synchronized (this) {
            while (!future.isDone() && ready.isEmpty()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    var interrupted = new InterruptedException(INTERRUPTED_MANAGED_JOIN_MESSAGE);
                    interrupted.initCause(e);
                    Thread.currentThread().interrupt();
                    throw new CompletionException(interrupted);
                }
            }
        }
        if (Thread.interrupted()) {
            Thread.currentThread().interrupt();
            throw new CompletionException(new InterruptedException(INTERRUPTED_MANAGED_JOIN_MESSAGE));
        }
    }

    private void waitForReadyCapacity() {
        try {
            wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        }
    }

    private void signalManagedJoinWaiters() {
        synchronized (this) {
            notifyAll();
        }
    }

    private static Throwable unwrap(final Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        if (throwable instanceof RuntimeException rt && rt.getCause() != null) {
            return rt.getCause();
        }
        return throwable;
    }

    private static ThreadPoolExecutor createExecutorService(final int parallelism) {
        var counter = new AtomicInteger();
        var executor = new ThreadPoolExecutor(
                parallelism, parallelism, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
                    var thread = new Thread(runnable, THREAD_NAME + "-" + counter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                });
        executor.prestartAllCoreThreads();
        return executor;
    }
}

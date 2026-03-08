/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates execution tasks using bounded queues and priority-based scheduling.
 *
 * <p>This coordinator implements the producer/consumer model with prioritized
 * execution of argument tasks over new class tasks.
 *
 * <p><b>Thread safety</b>
 * <p>This class is thread-safe.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class ArgumentExecutionCoordinator implements AutoCloseable {

    /**
     * Logger used for coordination diagnostics.
     */
    private static final Logger LOGGER = Logger.getLogger(ArgumentExecutionCoordinator.class.getName());

    /**
     * Default queue sizes.
     */
    private static final int CLASS_QUEUE_SIZE = Runtime.getRuntime().availableProcessors();

    private static final int ARGUMENT_QUEUE_SIZE = CLASS_QUEUE_SIZE * 4; // Allow backlog for running classes

    /**
     * Worker pool manager for task execution.
     */
    private final WorkerPoolManager workerPool;

    /**
     * Queue for class execution tasks.
     */
    private final BlockingQueue<ExecutionTask.ClassExecutionTask> classQueue;

    /**
     * Per-class argument task queues.
     */
    private final Map<Class<?>, BlockingQueue<ExecutionTask.ArgumentExecutionTask>> argumentQueues;

    /**
     * Completion tracker for synchronization.
     */
    private final CompletionTracker completionTracker;

    /**
     * Worker threads for processing tasks.
     */
    private final List<Thread> workers;

    /**
     * Flag indicating whether the coordinator is shutting down.
     */
    private volatile boolean shutdown;

    /**
     * Creates a new coordinator sized to the current machine.
     *
     * @return a new coordinator; never {@code null}
     */
    public static ArgumentExecutionCoordinator createDefault() {
        return new ArgumentExecutionCoordinator(CLASS_QUEUE_SIZE, ARGUMENT_QUEUE_SIZE);
    }

    /**
     * Creates a new coordinator with custom queue sizes.
     *
     * @param classQueueSize the maximum number of classes to queue
     * @param argumentQueueSize the maximum arguments per class queue
     * @throws IllegalArgumentException if either size is {@code <= 0}
     */
    public ArgumentExecutionCoordinator(final int classQueueSize, final int argumentQueueSize) {
        if (classQueueSize <= 0 || argumentQueueSize <= 0) {
            throw new IllegalArgumentException("Queue sizes must be positive");
        }

        this.workerPool = WorkerPoolManager.createDefault();
        this.classQueue = new LinkedBlockingQueue<>(classQueueSize);
        this.argumentQueues = new HashMap<>();
        this.completionTracker = new CompletionTracker();
        this.workers = new ArrayList<>();

        startWorkers();
        LOGGER.info("ArgumentExecutionCoordinator started with classQueue=" + classQueueSize + ", argumentQueue="
                + argumentQueueSize);
    }

    /**
     * Starts worker threads for processing tasks.
     */
    private void startWorkers() {
        final int workerCount = workerPool.getCoreCount();

        for (int i = 0; i < workerCount; i++) {
            final Thread worker = new Thread(this::workerLoop, "ParamixelCoordinatorWorker-" + i);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }
    }

    /**
     * Main worker loop that processes tasks.
     */
    private void workerLoop() {
        while (!shutdown) {
            try {
                // Prioritize argument tasks from running classes
                ExecutionTask task = pollArgumentTask();

                if (task == null) {
                    // No argument tasks available, try class tasks
                    task = pollClassTask();
                }

                if (task == null) {
                    // No tasks available, wait briefly
                    Thread.sleep(100);
                    continue;
                }

                // Submit task to worker pool
                workerPool.submit(task);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Worker loop error", e);
            }
        }
    }

    /**
     * Polls for argument tasks from running classes.
     *
     * @return an argument task, or {@code null} if none available
     */
    private ExecutionTask pollArgumentTask() {
        // Check each class's argument queue
        synchronized (argumentQueues) {
            for (BlockingQueue<ExecutionTask.ArgumentExecutionTask> queue : argumentQueues.values()) {
                final ExecutionTask.ArgumentExecutionTask task = queue.poll();
                if (task != null) {
                    return task;
                }
            }
        }
        return null;
    }

    /**
     * Polls for class tasks.
     *
     * @return a class task, or {@code null} if none available
     */
    private ExecutionTask pollClassTask() {
        try {
            return classQueue.poll(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Submits a test class for execution.
     *
     * @param classTask the class execution task; never {@code null}
     * @return {@code true} if submitted successfully, {@code false} if queue is full
     */
    public boolean submitClass(final ExecutionTask.ClassExecutionTask classTask) {
        Objects.requireNonNull(classTask, "classTask must not be null");

        try {
            final boolean offered = classQueue.offer(classTask, 1, TimeUnit.SECONDS);
            if (!offered) {
                LOGGER.warning("Class queue full, unable to submit class: "
                        + classTask.getClass().getSimpleName());
            }
            return offered;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Submits an argument for execution.
     *
     * @param classType the test class type; never {@code null}
     * @param argumentTask the argument execution task; never {@code null}
     * @return {@code true} if submitted successfully, {@code false} if queue is full
     */
    public boolean submitArgument(final Class<?> classType, final ExecutionTask.ArgumentExecutionTask argumentTask) {
        Objects.requireNonNull(classType, "classType must not be null");
        Objects.requireNonNull(argumentTask, "argumentTask must not be null");

        synchronized (argumentQueues) {
            BlockingQueue<ExecutionTask.ArgumentExecutionTask> queue =
                    argumentQueues.computeIfAbsent(classType, k -> new LinkedBlockingQueue<>(ARGUMENT_QUEUE_SIZE));

            try {
                final boolean offered = queue.offer(argumentTask, 1, TimeUnit.SECONDS);
                if (!offered) {
                    LOGGER.warning("Argument queue full for class " + classType.getName());
                }
                return offered;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /**
     * Registers a test class with the completion tracker.
     *
     * @param testClass the test class; never {@code null}
     * @param argumentCount the number of arguments
     */
    public void registerClass(final Class<?> testClass, final int argumentCount) {
        completionTracker.registerClass(testClass, argumentCount);
    }

    /**
     * Marks an argument as completed.
     *
     * @param testClass the test class; never {@code null}
     * @param argumentIndex the argument index
     * @param failure the failure that occurred, or {@code null} if successful
     */
    public void markArgumentCompleted(final Class<?> testClass, final int argumentIndex, final Throwable failure) {
        completionTracker.markArgumentCompleted(testClass, argumentIndex, failure);
    }

    /**
     * Waits for all arguments of a test class to complete.
     *
     * @param testClass the test class; never {@code null}
     * @return the first failure encountered, or {@code null} if all succeeded
     * @throws InterruptedException if the wait is interrupted
     */
    public Throwable waitForClassCompletion(final Class<?> testClass) throws InterruptedException {
        return completionTracker.waitForClassCompletion(testClass);
    }

    /**
     * Cleans up resources for a completed test class.
     *
     * @param testClass the test class; never {@code null}
     */
    public void unregisterClass(final Class<?> testClass) {
        synchronized (argumentQueues) {
            argumentQueues.remove(testClass);
        }
        completionTracker.unregisterClass(testClass);
        LOGGER.fine("Cleaned up resources for class " + testClass.getName());
    }

    /**
     * Returns statistics about current execution.
     *
     * @return execution statistics
     */
    public ArgumentExecutionCoordinator.Statistics getStatistics() {
        final Statistics stats = new Statistics();
        stats.classQueueSize = classQueue.size();
        stats.totalArgumentQueues = argumentQueues.size();
        stats.totalArgumentTasks = 0;

        synchronized (argumentQueues) {
            for (BlockingQueue<?> queue : argumentQueues.values()) {
                stats.totalArgumentTasks += queue.size();
            }
        }

        return stats;
    }

    @Override
    public void close() {
        shutdown = true;

        // Interrupt worker threads
        for (Thread worker : workers) {
            worker.interrupt();
        }

        // Wait for worker threads to terminate
        for (Thread worker : workers) {
            try {
                worker.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Close worker pool
        workerPool.close();

        LOGGER.info("ArgumentExecutionCoordinator closed");
    }

    /**
     * Statistics about coordinator state.
     */
    public static final class Statistics {
        /**
         * Size of the class queue.
         */
        public int classQueueSize;

        /**
         * Number of argument queues (one per running class).
         */
        public int totalArgumentQueues;

        /**
         * Total argument tasks waiting in queues.
         */
        public int totalArgumentTasks;

        @Override
        public String toString() {
            return "Statistics{classQueueSize=" + classQueueSize + ", totalArgumentQueues="
                    + totalArgumentQueues + ", totalArgumentTasks="
                    + totalArgumentTasks + "}";
        }
    }
}

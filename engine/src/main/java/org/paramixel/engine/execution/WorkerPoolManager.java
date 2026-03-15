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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.paramixel.engine.util.JavaVersionUtil;

/**
 * Manages the worker pool for execution tasks.
 *
 * <p>This manager provides dynamic executor creation based on Java version
 * compatibility, using virtual threads for Java 21+ and platform threads
 * for Java 17-20.
 *
 * <p><b>Thread safety</b>
 * <p>This class is thread-safe.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class WorkerPoolManager implements AutoCloseable {

    /**
     * Logger used for lifecycle and execution diagnostics.
     */
    private static final Logger LOGGER = Logger.getLogger(WorkerPoolManager.class.getName());

    /**
     * Default timeout for graceful executor shutdown.
     */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    /**
     * The executor service used for task execution.
     */
    private final ExecutorService executor;

    /**
     * The core count used to size the executor.
     */
    private final int coreCount;

    /**
     * Whether this manager uses virtual threads.
     */
    private final boolean usesVirtualThreads;

    /**
     * Creates a new worker pool manager sized by the current machine.
     *
     * @return a new manager sized to {@code availableProcessors}; never {@code null}
     */
    public static WorkerPoolManager createDefault() {
        return new WorkerPoolManager(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates a new worker pool manager with the specified core count.
     *
     * @param coreCount the core count used to size the executor; must be {@code >= 1}
     * @throws IllegalArgumentException if {@code coreCount < 1}
     */
    public WorkerPoolManager(final int coreCount) {
        if (coreCount < 1) {
            throw new IllegalArgumentException("coreCount must be >= 1");
        }
        this.coreCount = coreCount;
        this.usesVirtualThreads = JavaVersionUtil.supportsVirtualThreads();
        this.executor = createExecutor();
        LOGGER.info("WorkerPoolManager created: cores=" + coreCount + ", virtualThreads=" + usesVirtualThreads);
    }

    /**
     * Creates the appropriate executor based on Java version.
     *
     * @return the configured executor service
     */
    private ExecutorService createExecutor() {
        if (usesVirtualThreads) {
            try {
                // Use reflection to avoid compilation issues with Java < 21
                final var executorsClass = Executors.class;
                final var method = executorsClass.getMethod("newVirtualThreadPerTaskExecutor");
                return (ExecutorService) method.invoke(null);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create virtual thread executor", e);
            }
        } else {
            return Executors.newFixedThreadPool(coreCount, new WorkerThreadFactory());
        }
    }

    /**
     * Creates an executor service sized to the specified parallelism.
     *
     * @param parallelism the parallelism; must be {@code >= 1}
     * @return an executor service; never {@code null}
     */
    public static ExecutorService createExecutor(final int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be >= 1");
        }
        if (JavaVersionUtil.supportsVirtualThreads()) {
            try {
                final var executorsClass = Executors.class;
                final var method = executorsClass.getMethod("newVirtualThreadPerTaskExecutor");
                return (ExecutorService) method.invoke(null);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create virtual thread executor", e);
            }
        } else {
            return Executors.newFixedThreadPool(parallelism, new WorkerThreadFactory());
        }
    }

    /**
     * Returns the core count used by this manager.
     *
     * @return the core count
     */
    public int getCoreCount() {
        return coreCount;
    }

    /**
     * Returns whether this manager uses virtual threads.
     *
     * @return {@code true} if using virtual threads, {@code false} otherwise
     */
    public boolean usesVirtualThreads() {
        return usesVirtualThreads;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("WorkerPoolManager closed");
    }

    /**
     * Custom thread factory for platform thread pools.
     */
    private static final class WorkerThreadFactory implements ThreadFactory {

        private static final AtomicInteger threadCounter = new AtomicInteger(0);

        @Override
        public Thread newThread(final Runnable r) {
            final Thread thread = new Thread(r);
            thread.setName("ParamixelWorker-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}

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

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * Enhanced execution runtime that uses the new queue-based architecture.
 *
 * <p>This runtime replaces the semaphore-based concurrency model with
 * a producer/consumer queue model for better resource utilization.
 *
 * <p><b>Thread safety</b>
 * <p>This class is thread-safe.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class EnhancedParamixelExecutionRuntime implements AutoCloseable {

    /**
     * Logger used for lifecycle and execution diagnostics.
     */
    private static final Logger LOGGER = Logger.getLogger(EnhancedParamixelExecutionRuntime.class.getName());

    /**
     * Default timeout for graceful shutdown.
     */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    /**
     * The coordinator managing queues and workers.
     */
    private final ArgumentExecutionCoordinator coordinator;

    /**
     * Creates a runtime sized to the current machine.
     *
     * @return a new runtime sized to {@code availableProcessors}; never {@code null}
     */
    public static EnhancedParamixelExecutionRuntime createDefault() {
        return new EnhancedParamixelExecutionRuntime(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates a runtime using the specified core count.
     *
     * @param cores the core count used to size queues; must be {@code >= 1}
     * @throws IllegalArgumentException if {@code cores < 1}
     */
    public EnhancedParamixelExecutionRuntime(final int cores) {
        this.coordinator = ArgumentExecutionCoordinator.createDefault();
    }

    /**
     * Returns the underlying coordinator.
     *
     * @return the coordinator; never {@code null}
     */
    public ArgumentExecutionCoordinator coordinator() {
        return coordinator;
    }

    /**
     * Submits a class for execution.
     *
     * @param classTask the class execution task; never {@code null}
     * @return {@code true} if submitted successfully, {@code false} if queue is full
     */
    public boolean submitClass(final ExecutionTask.ClassExecutionTask classTask) {
        Objects.requireNonNull(classTask, "classTask must not be null");
        return coordinator.submitClass(classTask);
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
        return coordinator.submitArgument(classType, argumentTask);
    }

    /**
     * Submits a runnable and temporarily sets the executing thread name (compatibility method).
     *
     * <p>This method is maintained for API compatibility but delegates to the coordinator.
     *
     * @param threadName the thread name to use for the duration of the task; never {@code null}
     * @param runnable the task to execute; never {@code null}
     * @return a future representing task completion; never {@code null}
     */
    public Future<?> submitNamed(final String threadName, final Runnable runnable) {
        Objects.requireNonNull(threadName, "threadName");
        Objects.requireNonNull(runnable, "runnable");

        // For backward compatibility, we'll implement this properly later
        throw new UnsupportedOperationException("submitNamed not yet implemented for enhanced runtime");
    }

    /**
     * Submits a callable and temporarily sets the executing thread name (compatibility method).
     *
     * <p>This method is maintained for API compatibility but delegates to the coordinator.
     *
     * @param threadName the thread name to use for the duration of the task; never {@code null}
     * @param callable the task to execute; never {@code null}
     * @param <T> the callable result type
     * @return a future representing task completion and result; never {@code null}
     */
    public <T> Future<T> submitNamed(final String threadName, final Callable<T> callable) {
        Objects.requireNonNull(threadName, "threadName");
        Objects.requireNonNull(callable, "callable");

        // For backward compatibility, we'll implement this properly later
        throw new UnsupportedOperationException("submitNamed not yet implemented for enhanced runtime");
    }

    /**
     * Runs a runnable while temporarily setting the current thread name.
     *
     * <p>This method is maintained for API compatibility.
     *
     * @param threadName the thread name to set; may be {@code null}
     * @param runnable the runnable to execute; may be {@code null}
     */
    public static void runWithThreadName(final String threadName, final Runnable runnable) {
        ParamixelExecutionRuntime.runWithThreadName(threadName, runnable);
    }

    /**
     * Calls a callable while temporarily setting the current thread name.
     *
     * <p>This method is maintained for API compatibility.
     *
     * @param threadName the thread name to set; may be {@code null}
     * @param callable the callable to execute; must not be {@code null}
     * @param <T> the callable result type
     * @return the callable result
     * @throws Exception when {@code callable.call()} throws
     */
    public static <T> T callWithThreadName(final String threadName, final Callable<T> callable) throws Exception {
        return ParamixelExecutionRuntime.callWithThreadName(threadName, callable);
    }

    @Override
    public void close() {
        coordinator.close();
        LOGGER.info("EnhancedParamixelExecutionRuntime closed");
    }
}

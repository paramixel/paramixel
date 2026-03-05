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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Provides an executor and concurrency limiter for engine execution.
 *
 * <p>This runtime owns:
 * <ul>
 *   <li>a virtual-thread-per-task {@link ExecutorService} for scheduling work</li>
 *   <li>a {@link ParamixelConcurrencyLimiter} that enforces global and per-category limits</li>
 * </ul>
 *
 * <p><b>Thread safety</b>
 * <p>This class is thread-safe. The underlying executor is thread-safe and the limiter uses
 * thread-safe primitives.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public final class ParamixelExecutionRuntime implements AutoCloseable {

    /**
     * Default timeout for graceful executor shutdown.
     *
     * <p>The value is {@code 30 seconds}.
     */
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Owned executor used for task submission; immutable reference.
     */
    private final ExecutorService executor;

    /**
     * Owned concurrency limiter used for permit acquisition; immutable reference.
     */
    private final ParamixelConcurrencyLimiter limiter;

    /**
     * Creates a runtime sized to the current machine.
     *
     * @return a new runtime sized to {@code availableProcessors}; never {@code null}
     * @since 0.0.1
     */
    public static ParamixelExecutionRuntime createDefault() {
        return new ParamixelExecutionRuntime(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates a runtime using the specified core count.
     *
     * <p>The {@code cores} value configures the concurrency limiter.
     *
     * @param cores the core count used to size permits; must be {@code >= 1}
     * @throws IllegalArgumentException if {@code cores < 1}
     * @since 0.0.1
     */
    public ParamixelExecutionRuntime(final int cores) {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.limiter = new ParamixelConcurrencyLimiter(cores);
    }

    /**
     * Returns the underlying executor.
     *
     * <p>Callers should treat the returned executor as owned by this runtime and should not shut
     * it down directly.
     *
     * @return the executor; never {@code null}
     * @since 0.0.1
     */
    public ExecutorService executor() {
        return executor;
    }

    /**
     * Returns the global concurrency limiter.
     *
     * @return the limiter; never {@code null}
     * @since 0.0.1
     */
    public ParamixelConcurrencyLimiter limiter() {
        return limiter;
    }

    /**
     * Submits a runnable and temporarily sets the executing thread name.
     *
     * <p>This method wraps the runnable in {@link #runWithThreadName(String, Runnable)}.
     *
     * @param threadName the thread name to use for the duration of the task; never {@code null}
     * @param runnable the task to execute; never {@code null}
     * @return a future representing task completion; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @since 0.0.1
     */
    public Future<?> submitNamed(final String threadName, final Runnable runnable) {
        Objects.requireNonNull(threadName, "threadName");
        Objects.requireNonNull(runnable, "runnable");
        return executor.submit(() -> runWithThreadName(threadName, runnable));
    }

    /**
     * Submits a callable and temporarily sets the executing thread name.
     *
     * <p>This method wraps the callable in {@link #callWithThreadName(String, Callable)}.
     *
     * @param threadName the thread name to use for the duration of the task; never {@code null}
     * @param callable the task to execute; never {@code null}
     * @param <T> the callable result type
     * @return a future representing task completion and result; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @since 0.0.1
     */
    public <T> Future<T> submitNamed(final String threadName, final Callable<T> callable) {
        Objects.requireNonNull(threadName, "threadName");
        Objects.requireNonNull(callable, "callable");
        return executor.submit(() -> callWithThreadName(threadName, callable));
    }

    /**
     * Runs a runnable while temporarily setting the current thread name.
     *
     * <p>This method restores the previous thread name in a {@code finally} block.
     *
     * @param threadName the thread name to set; may be {@code null} (delegated behavior of {@link Thread#setName(String)})
     * @param runnable the runnable to execute; may be {@code null} (throws {@link NullPointerException})
     * @since 0.0.1
     */
    public static void runWithThreadName(final String threadName, final Runnable runnable) {
        final Thread current = Thread.currentThread();
        final String previous = current.getName();
        current.setName(threadName);
        try {
            runnable.run();
        } finally {
            current.setName(previous);
        }
    }

    /**
     * Calls a callable while temporarily setting the current thread name.
     *
     * <p>This method restores the previous thread name in a {@code finally} block.
     *
     * @param threadName the thread name to set; may be {@code null} (delegated behavior of {@link Thread#setName(String)})
     * @param callable the callable to execute; must not be {@code null}
     * @param <T> the callable result type
     * @return the callable result
     * @throws Exception when {@code callable.call()} throws
     * @since 0.0.1
     */
    public static <T> T callWithThreadName(final String threadName, final Callable<T> callable) throws Exception {
        final Thread current = Thread.currentThread();
        final String previous = current.getName();
        current.setName(threadName);
        try {
            return callable.call();
        } finally {
            current.setName(previous);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(DEFAULT_SHUTDOWN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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
 * Shared execution runtime for the Paramixel engine.
 *
 * <p>Holds a single virtual-thread-per-task executor and the global concurrency limiter.
 */
public final class ParamixelExecutionRuntime implements AutoCloseable {

    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    private final ExecutorService executor;
    private final ParamixelConcurrencyLimiter limiter;

    public static ParamixelExecutionRuntime createDefault() {
        return new ParamixelExecutionRuntime(Runtime.getRuntime().availableProcessors());
    }

    public ParamixelExecutionRuntime(final int cores) {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.limiter = new ParamixelConcurrencyLimiter(cores);
    }

    public ExecutorService executor() {
        return executor;
    }

    public ParamixelConcurrencyLimiter limiter() {
        return limiter;
    }

    public Future<?> submitNamed(final String threadName, final Runnable runnable) {
        Objects.requireNonNull(threadName, "threadName");
        Objects.requireNonNull(runnable, "runnable");
        return executor.submit(() -> runWithThreadName(threadName, runnable));
    }

    public <T> Future<T> submitNamed(final String threadName, final Callable<T> callable) {
        Objects.requireNonNull(threadName, "threadName");
        Objects.requireNonNull(callable, "callable");
        return executor.submit(() -> callWithThreadName(threadName, callable));
    }

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

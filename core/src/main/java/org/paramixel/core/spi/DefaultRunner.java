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

package org.paramixel.core.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.core.Action;
import org.paramixel.core.Configuration;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.exception.ConfigurationException;
import org.paramixel.core.spi.listener.SafeListener;
import org.paramixel.core.support.Arguments;

/**
 * Default {@link Runner} implementation used for standard Paramixel execution.
 *
 * <p>This SPI implementation validates configuration, creates execution infrastructure, detects invalid action-graph
 * conditions such as cycles and thread-starvation deadlocks, and drives listener callbacks around the root action.
 */
public final class DefaultRunner implements Runner {

    private static final ThreadLocal<ExecutionScope> EXECUTION_SCOPE = new ThreadLocal<>();
    private static final String RUNNER_THREAD_NAME = "paramixel-runner";
    private static final String PARALLEL_THREAD_NAME = "paramixel-parallel";

    private final Listener listener;
    private final Map<String, String> configuration;
    private final ExecutorService externalExecutorService;

    /**
     * Creates a runner with the supplied configuration, listener, and optional external executor service.
     *
     * @param configuration the configuration map to use, or {@code null} to load
     *     {@link Configuration#defaultProperties()}
     * @param listener the listener to receive lifecycle callbacks
     * @param externalExecutorService the executor service to reuse for runner-managed work, or {@code null} to create
     *     internal executors
     * @throws NullPointerException if {@code listener} is {@code null}
     * @throws ConfigurationException if {@code configuration} contains an invalid runner parallelism value
     */
    public DefaultRunner(
            Map<String, String> configuration, Listener listener, ExecutorService externalExecutorService) {
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.configuration =
                configuration != null ? Map.copyOf(configuration) : Map.copyOf(Configuration.defaultProperties());
        this.externalExecutorService = externalExecutorService;
        resolveParallelism(this.configuration);
    }

    @Override
    public Map<String, String> getConfiguration() {
        return configuration;
    }

    @Override
    public Listener getListener() {
        return listener;
    }

    @Override
    public Result run(Action action) {
        Objects.requireNonNull(action, "action must not be null");

        Object executionDomain = new Object();
        ExecutorService runnerExec;
        boolean ownsRunnerExec;

        if (externalExecutorService != null) {
            runnerExec = externalExecutorService;
            ownsRunnerExec = false;
        } else {
            runnerExec = createExecutorService(configuration, RUNNER_THREAD_NAME);
            ownsRunnerExec = true;
        }

        ExecutorService parallelExec = createExecutorService(configuration, PARALLEL_THREAD_NAME);
        ExecutorService routingExec = new RoutingExecutorService(runnerExec, parallelExec, executionDomain);

        Listener safeListener = listener instanceof SafeListener ? listener : new SafeListener(listener);

        DefaultResult rootResult = new DefaultResult(action);

        ExecutionScope previousScope = EXECUTION_SCOPE.get();
        EXECUTION_SCOPE.set(new ExecutionScope(executionDomain, 0));

        try {
            new CycleLoopDetector().validateNoCycles(action);
            new DeadlockDetector().validateNoDeadlock(action, resolveParallelism(configuration));
            safeListener.runStarted(this);

            Context context = new DefaultContext(configuration, safeListener, routingExec);
            Result executeResult = action.execute(context);

            rootResult.setStatus(executeResult.getStatus());
            rootResult.setElapsedTime(executeResult.getElapsedTime());
            for (Result child : executeResult.getChildren()) {
                rootResult.addChild(child);
            }

            safeListener.runCompleted(this, rootResult);
        } finally {
            restoreExecutionScope(previousScope);
            if (ownsRunnerExec) {
                shutdownExecutorService(runnerExec);
            }
            shutdownExecutorService(parallelExec);
        }
        return rootResult;
    }

    /**
     * Resolves the effective runner parallelism from configuration.
     *
     * @param configuration the configuration to inspect
     * @return the positive parallelism value
     * @throws ConfigurationException if the configured value is missing, non-numeric, or not positive
     */
    private static int resolveParallelism(Map<String, String> configuration) {
        String configuredParallelism = configuration.getOrDefault(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(Runtime.getRuntime().availableProcessors()));

        final int parallelism;
        try {
            parallelism = Integer.parseInt(configuredParallelism);
        } catch (NumberFormatException e) {
            throw ConfigurationException.of(
                    "Invalid configuration for '" + Configuration.RUNNER_PARALLELISM + "': expected integer but was '"
                            + configuredParallelism + "'",
                    e);
        }

        if (parallelism <= 0) {
            throw ConfigurationException.of("Invalid configuration for '" + Configuration.RUNNER_PARALLELISM
                    + "': expected positive integer but was '"
                    + configuredParallelism + "'");
        }

        return parallelism;
    }

    private static ExecutorService createExecutorService(Map<String, String> configuration, String threadName) {
        int parallelism = resolveParallelism(configuration);
        AtomicInteger counter = new AtomicInteger(1);

        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                parallelism, parallelism, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, threadName + "-" + counter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                });

        threadPoolExecutor.prestartAllCoreThreads();
        return threadPoolExecutor;
    }

    private static void shutdownExecutorService(ExecutorService executorService) {
        executorService.shutdown();

        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void restoreExecutionScope(ExecutionScope previousScope) {
        if (previousScope == null) {
            EXECUTION_SCOPE.remove();
        } else {
            EXECUTION_SCOPE.set(previousScope);
        }
    }

    private static final class RoutingExecutorService extends AbstractExecutorService {

        private final ExecutorService runnerExecutorService;
        private final ExecutorService parallelExecutorService;
        private final Object executionDomain;

        RoutingExecutorService(
                ExecutorService runnerExecutorService,
                ExecutorService parallelExecutorService,
                Object executionDomain) {
            this.runnerExecutorService = runnerExecutorService;
            this.parallelExecutorService = parallelExecutorService;
            this.executionDomain = executionDomain;
        }

        @Override
        public void shutdown() {
            runnerExecutorService.shutdown();
            parallelExecutorService.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            List<Runnable> runnables = new ArrayList<>(runnerExecutorService.shutdownNow());
            runnables.addAll(parallelExecutorService.shutdownNow());
            return runnables;
        }

        @Override
        public boolean isShutdown() {
            return runnerExecutorService.isShutdown() && parallelExecutorService.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return runnerExecutorService.isTerminated() && parallelExecutorService.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            Arguments.requireNonNegative(timeout, "timeout must be non-negative");
            Objects.requireNonNull(unit, "unit must not be null");
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            boolean runnerTerminated = runnerExecutorService.awaitTermination(timeout, unit);
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return runnerTerminated && parallelExecutorService.isTerminated();
            }
            boolean parallelTerminated = parallelExecutorService.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS);
            return runnerTerminated && parallelTerminated;
        }

        @Override
        public void execute(Runnable command) {
            Objects.requireNonNull(command, "command must not be null");
            ExecutionScope scope = EXECUTION_SCOPE.get();
            boolean sameDomain = scope != null && scope.domain() == executionDomain;
            int currentDepth = sameDomain ? scope.parallelDepth() : 0;
            ExecutorService delegate = currentDepth == 0 ? runnerExecutorService : parallelExecutorService;
            int childDepth = currentDepth + 1;

            delegate.execute(() -> {
                ExecutionScope previousScope = EXECUTION_SCOPE.get();
                EXECUTION_SCOPE.set(new ExecutionScope(executionDomain, childDepth));
                try {
                    command.run();
                } finally {
                    restoreExecutionScope(previousScope);
                }
            });
        }
    }

    private record ExecutionScope(Object domain, int parallelDepth) {}
}

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

package org.paramixel.core;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.paramixel.core.listener.SafeListener;

/**
 * Coordinates top-level execution of actions.
 *
 * <p>A runner owns the configuration, listener, and executor service used to execute an
 * {@link Action}. Create instances with {@link #builder()} and then invoke {@link #run(Action)} to
 * execute a plan.</p>
 */
public final class Runner {

    private final Listener listener;
    private final Map<String, String> configuration;
    private final ExecutorService executorService;
    private final boolean ownsExecutorService;

    private Runner(Map<String, String> configuration, Listener listener, ExecutorService executorService) {
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.configuration = configuration != null ? Map.copyOf(configuration) : Configuration.defaultProperties();

        if (executorService != null) {
            this.executorService = executorService;
            this.ownsExecutorService = false;
        } else {
            this.executorService = createExecutorService(this.configuration);
            this.ownsExecutorService = true;
        }
    }

    /**
     * Creates a new builder for constructing {@link Runner} instances.
     *
     * @return a new runner builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the immutable configuration used by this runner.
     *
     * @return the runner configuration
     */
    public Map<String, String> getConfiguration() {
        return configuration;
    }

    /**
     * Returns the listener that receives lifecycle callbacks for this runner.
     *
     * @return the runner listener
     */
    public Listener getListener() {
        return listener;
    }

    /**
     * Executes the supplied action.
     *
     * <p>The configured listener is notified before execution starts and after it completes. If this
     * runner created its own executor service, that executor service is shut down after execution.</p>
     *
     * @param action the action to execute
     * @throws NullPointerException if {@code action} is {@code null}
     */
    public void run(Action action) {
        Objects.requireNonNull(action, "action must not be null");

        Listener safeListener = listener instanceof SafeListener ? listener : SafeListener.of(listener);
        safeListener.runStarted(this, action);

        try {
            Context context = Context.of(configuration, safeListener, executorService);
            action.execute(context);
            safeListener.runCompleted(this, action);
        } finally {
            if (ownsExecutorService) {
                shutdownExecutorService(executorService);
            }
        }
    }

    private static ExecutorService createExecutorService(Map<String, String> configuration) {
        String configuredParallelism = configuration.getOrDefault(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(Runtime.getRuntime().availableProcessors() * 2));

        final int parallelism;
        try {
            parallelism = Integer.parseInt(configuredParallelism);
        } catch (NumberFormatException e) {
            throw ConfigurationException.of(
                    "Invalid configuration for '" + Configuration.RUNNER_PARALLELISM + "': expected integer but was '"
                            + configuredParallelism + "'",
                    e);
        }

        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                parallelism, parallelism, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "paramixel");
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

    /**
     * Builds {@link Runner} instances with optional custom components.
     */
    public static final class Builder {

        private Map<String, String> configuration;
        private Listener listener = Listener.treeListener();
        private ExecutorService executorService;

        /**
         * Sets the configuration properties for the runner being built.
         *
         * <p>The provided map is defensively copied.</p>
         *
         * @param properties the configuration properties to use
         * @return this builder
         * @throws NullPointerException if {@code properties} is {@code null}
         */
        public Builder configuration(Map<String, String> properties) {
            this.configuration = Map.copyOf(Objects.requireNonNull(properties, "configuration must not be null"));
            return this;
        }

        /**
         * Sets the listener for the runner being built.
         *
         * @param listener the listener to use
         * @return this builder
         * @throws NullPointerException if {@code listener} is {@code null}
         */
        public Builder listener(Listener listener) {
            this.listener = Objects.requireNonNull(listener, "listener must not be null");
            return this;
        }

        /**
         * Sets the executor service for the runner being built.
         *
         * <p>When supplied, the built runner uses this executor service and does not manage its
         * lifecycle.</p>
         *
         * @param executorService the executor service to use
         * @return this builder
         * @throws NullPointerException if {@code executorService} is {@code null}
         */
        public Builder executorService(ExecutorService executorService) {
            this.executorService = Objects.requireNonNull(executorService, "executorService must not be null");
            return this;
        }

        /**
         * Builds a new {@link Runner} from the current builder state.
         *
         * @return a new runner instance
         */
        public Runner build() {
            return new Runner(configuration, listener, executorService);
        }
    }
}

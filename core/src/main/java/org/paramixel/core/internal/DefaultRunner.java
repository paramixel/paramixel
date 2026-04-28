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

package org.paramixel.core.internal;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.paramixel.core.Action;
import org.paramixel.core.Configuration;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;

/**
 * The default implementation of {@link Runner}.
 */
public final class DefaultRunner implements Runner {

    private final Listener listener;
    private final Map<String, String> configuration;
    private volatile ExecutorService executorService;

    private DefaultRunner(Builder builder) {
        this(builder.listener, builder.configuration);
    }

    private DefaultRunner(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.configuration = Configuration.defaultProperties();
    }

    private DefaultRunner(Listener listener, Map<String, String> configuration) {
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.configuration = configuration != null ? configuration : Configuration.defaultProperties();
    }

    @Override
    public Listener listener() {
        return listener;
    }

    @Override
    public Map<String, String> configuration() {
        return configuration;
    }

    /**
     * Returns a builder for a DefaultRunner.
     *
     * @return A new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Result run(Action action) {
        Objects.requireNonNull(action, "action must not be null");
        listener.planStarted(this, action);
        Result result = run(DefaultContext.create(action, this));
        listener.planCompleted(this, result);
        shutdownExecutorService();
        return result;
    }

    @Override
    public Result run(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        return context.action().execute(context);
    }

    @Override
    public Result run(Action action, Context parentContext) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(parentContext, "parentContext must not be null");
        if (parentContext instanceof DefaultContext defaultContext) {
            return run(defaultContext.createChild(action));
        }
        return run(new DefaultContext(action, parentContext, this));
    }

    /**
     * Returns the executor service for parallel execution.
     *
     * @return The executor service, or null if not yet created.
     */
    public ExecutorService executorService() {
        return executorService;
    }

    /**
     * Gets or creates the executor service for parallel execution.
     *
     * @param parallelism The parallelism level.
     * @return The executor service.
     */
    public ExecutorService getOrCreateExecutorService(int parallelism) {
        if (executorService == null) {
            synchronized (this) {
                if (executorService == null) {
                    executorService = Executors.newFixedThreadPool(parallelism, r -> {
                        Thread thread = new Thread(r, "paramixel-worker");
                        thread.setDaemon(true);
                        return thread;
                    });
                }
            }
        }
        return executorService;
    }

    /**
     * Shuts down the executor service for parallel execution.
     */
    private void shutdownExecutorService() {
        ExecutorService es;
        synchronized (this) {
            es = executorService;
            executorService = null;
        }

        if (es == null) {
            return;
        }

        es.shutdown();
        try {
            if (!es.awaitTermination(5, TimeUnit.SECONDS)) {
                es.shutdownNow();
            }
        } catch (InterruptedException e) {
            es.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds {@link DefaultRunner} instances.
     */
    public static final class Builder {

        private Listener listener = Listener.treeListener();
        private Map<String, String> configuration;

        private Builder() {}

        /**
         * Sets the listener.
         *
         * @param listener The listener to notify; must not be null.
         * @return This builder.
         */
        public Builder listener(Listener listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Sets the configuration.
         *
         * @param properties The configuration properties; must not be null.
         * @return This builder.
         */
        public Builder configuration(Map<String, String> properties) {
            this.configuration = Map.copyOf(Objects.requireNonNull(properties, "configuration must not be null"));
            return this;
        }

        /**
         * Builds the runner.
         *
         * @return A new DefaultRunner.
         */
        public DefaultRunner build() {
            return new DefaultRunner(this);
        }
    }
}

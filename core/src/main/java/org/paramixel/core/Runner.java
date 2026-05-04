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
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.paramixel.core.spi.DefaultRunner;

/**
 * Executes Paramixel actions.
 *
 * <p>A {@code Runner} is the main entry point for launching an {@link Action} tree. It holds the effective
 * configuration and the {@link Listener} used for lifecycle notifications.
 *
 * @apiNote Use {@link #builder()} to customize configuration, listeners, or the executor service used during a run.
 */
public interface Runner {

    /**
     * Creates a new builder for constructing a {@link Runner}.
     *
     * @return a new runner builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the effective runner configuration.
     *
     * @return the configuration properties used by this runner
     */
    Map<String, String> getConfiguration();

    /**
     * Returns the listener receiving run lifecycle callbacks.
     *
     * @return the runner listener
     */
    Listener getListener();

    /**
     * Executes the supplied action tree.
     *
     * @param action the root action to execute
     * @return the result produced for the supplied action
     */
    Result run(Action action);

    /**
     * Resolves an action with the supplied selector and executes it when present.
     *
     * @param selector the selector used to locate an action
     * @return the result of the resolved action, or an empty {@link Optional} when no matching action is found
     */
    default Optional<Result> run(Selector selector) {
        Objects.requireNonNull(selector, "selector must not be null");
        Optional<Action> optionalAction = Resolver.resolveActions(getConfiguration(), selector);
        return optionalAction.map(this::run);
    }

    /**
     * Executes an action and converts the final outcome into a process-style exit code.
     *
     * <p>The returned value is {@code 0} for passing results. Skipped results also return {@code 0} unless
     * {@link Configuration#FAILURE_ON_SKIP} is configured as {@code true}. All other outcomes return {@code 1}.
     *
     * @param action the root action to execute
     * @return {@code 0} for success and {@code 1} for failure
     */
    default int runAndReturnExitCode(Action action) {
        Objects.requireNonNull(action, "action must not be null");
        Result result = run(action);
        boolean failureOnSkip =
                Boolean.parseBoolean(getConfiguration().getOrDefault(Configuration.FAILURE_ON_SKIP, "false"));
        if (result.getStatus().isPass()) {
            return 0;
        }
        if (result.getStatus().isSkip() && !failureOnSkip) {
            return 0;
        }
        return 1;
    }

    /**
     * Resolves and executes an action, then converts the outcome into a process-style exit code.
     *
     * <p>If the selector resolves no action, this method returns {@code 0}.
     *
     * @param selector the selector used to locate an action
     * @return {@code 0} for success and {@code 1} for failure
     */
    default int runAndReturnExitCode(Selector selector) {
        Objects.requireNonNull(selector, "selector must not be null");
        Optional<Result> optionalResult = run(selector);
        if (optionalResult.isEmpty()) {
            return 0;
        }
        Result result = optionalResult.get();
        boolean failureOnSkip =
                Boolean.parseBoolean(getConfiguration().getOrDefault(Configuration.FAILURE_ON_SKIP, "false"));
        if (result.getStatus().isPass()) {
            return 0;
        }
        if (result.getStatus().isSkip() && !failureOnSkip) {
            return 0;
        }
        return 1;
    }

    /**
     * Executes an action and terminates the current JVM with the derived exit code.
     *
     * @param action the root action to execute
     */
    default void runAndExit(Action action) {
        Objects.requireNonNull(action, "action must not be null");
        System.exit(runAndReturnExitCode(action));
    }

    /**
     * Resolves and executes an action, then terminates the current JVM with the derived exit code.
     *
     * @param selector the selector used to locate an action
     */
    default void runAndExit(Selector selector) {
        Objects.requireNonNull(selector, "selector must not be null");
        System.exit(runAndReturnExitCode(selector));
    }

    /**
     * Fluent builder for {@link Runner} instances.
     *
     * <p>By default, the builder uses {@link Factory#defaultListener()} and the implementation's default
     * configuration.
     */
    final class Builder {

        private Map<String, String> configuration;
        private Listener listener = Factory.defaultListener();
        private ExecutorService executorService;

        private Builder() {}

        /**
         * Sets the configuration map to use for the runner.
         *
         * <p>The supplied map is defensively copied.
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
         * Sets the listener to receive lifecycle callbacks.
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
         * Sets an external executor service for runner-managed work.
         *
         * <p>When supplied, the runner reuses this executor service instead of creating its own primary executor.
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
         * Builds a new {@link Runner} instance.
         *
         * @return a new runner
         */
        public Runner build() {
            return new DefaultRunner(configuration, listener, executorService);
        }
    }
}

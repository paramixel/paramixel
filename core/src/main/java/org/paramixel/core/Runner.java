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
import org.paramixel.core.internal.DefaultRunner;

/**
 * Executes Paramixel actions.
 *
 * <p>A {@link Runner} is the main entry point for launching an {@link Action} tree. It holds the effective
 * configuration and the {@link Listener} used for lifecycle notifications.
 *
 * <p>Runners implement {@link AutoCloseable}. When used in try-with-resources, {@link #close()} cascades to the
 * listener, releasing any resources it holds.
 *
 * <p>Action tree execution is not designed for concurrent use across runner instances. Each action tree should be
 * executed by a single runner at a time; concurrent invocation of {@link #run(Action)} across different runner
 * instances operating on the same or overlapping action trees is not supported.
 *
 * <p>Use {@link #builder()} to customize configuration or listeners used during a run.
 */
public interface Runner extends AutoCloseable {

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
     * @throws NullPointerException if {@code action} is {@code null}
     * @throws org.paramixel.core.exception.CycleDetectedException if the action graph contains a cycle
     * @throws org.paramixel.core.exception.ConfigurationException if the configuration is invalid
     */
    Result run(Action action);

    /**
     * Resolves an action with the supplied selector and executes it when present.
     *
     * @param selector the selector used to locate an action
     * @return the result of the resolved action, or an empty {@link Optional} when no matching action is found
     * @throws NullPointerException if {@code selector} is {@code null}
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
     * @throws NullPointerException if {@code action} is {@code null}
     */
    default int runAndReturnExitCode(Action action) {
        Objects.requireNonNull(action, "action must not be null");
        Result result = run(action);
        boolean failureOnSkip =
                Boolean.parseBoolean(getConfiguration().getOrDefault(Configuration.FAILURE_ON_SKIP, "false"));
        var status = result.getStatus();
        if (status.isPass()) {
            return 0;
        }
        if (status.isSkip() && !failureOnSkip) {
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
     * @throws NullPointerException if {@code selector} is {@code null}
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
        var status = result.getStatus();
        if (status.isPass()) {
            return 0;
        }
        if (status.isSkip() && !failureOnSkip) {
            return 0;
        }
        return 1;
    }

    /**
     * Executes an action and terminates the current JVM with the derived exit code.
     *
     * <p>This method never returns normally. It always calls {@link System#exit(int)}.
     *
     * @param action the root action to execute
     * @see System#exit(int)
     * @throws NullPointerException if {@code action} is {@code null}
     */
    default void runAndExit(Action action) {
        Objects.requireNonNull(action, "action must not be null");
        System.exit(runAndReturnExitCode(action));
    }

    /**
     * Resolves and executes an action, then terminates the current JVM with the derived exit code.
     *
     * <p>This method never returns normally. It always calls {@link System#exit(int)}.
     *
     * @param selector the selector used to locate an action
     * @see System#exit(int)
     * @throws NullPointerException if {@code selector} is {@code null}
     */
    default void runAndExit(Selector selector) {
        Objects.requireNonNull(selector, "selector must not be null");
        System.exit(runAndReturnExitCode(selector));
    }

    /**
     * Releases resources held by this runner and its listener.
     *
     * <p>The default implementation does nothing. The default runner implementation cascades {@code close()} to
     * its listener so that listeners holding resources are cleaned up when the runner is used in try-with-resources.
     */
    @Override
    default void close() {
        // Intentionally empty
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
        private boolean built;

        private Builder() {
            // Intentionally empty
        }

        /**
         * Sets the configuration map to use for the runner.
         *
         * <p>The supplied map is defensively copied.
         *
         * @param properties the configuration properties to use
         * @return this builder
         * @throws NullPointerException if {@code properties} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder configuration(Map<String, String> properties) {
            ensureNotBuilt();
            this.configuration = Map.copyOf(Objects.requireNonNull(properties, "configuration must not be null"));
            return this;
        }

        /**
         * Sets the listener to receive lifecycle callbacks.
         *
         * @param listener the listener to use
         * @return this builder
         * @throws NullPointerException if {@code listener} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder listener(Listener listener) {
            ensureNotBuilt();
            this.listener = Objects.requireNonNull(listener, "listener must not be null");
            return this;
        }

        /**
         * Builds a new {@link Runner} instance.
         *
         * @return a new runner
         * @throws IllegalStateException if this builder has already been built
         */
        public Runner build() {
            ensureNotBuilt();
            built = true;
            return new DefaultRunner(configuration, listener);
        }

        private void ensureNotBuilt() {
            if (built) {
                throw new IllegalStateException("builder already built");
            }
        }
    }
}

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

package org.paramixel.api;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import nonapi.org.paramixel.ConcreteRunner;
import org.paramixel.api.action.Action;
import org.paramixel.api.exception.ConfigurationException;
import org.paramixel.api.exception.CycleDetectedException;
import org.paramixel.api.selector.Selector;

/**
 * Executes Paramixel actions and returns results containing descriptor trees and effective aggregate status.
 *
 * <p>Implementations are not required to be thread-safe. Concurrent use of a single {@code Runner} instance
 * from multiple threads is undefined unless the implementation documents otherwise.
 *
 * @see Result
 */
public interface Runner {

    /**
     * Creates a runner using the default configuration and listener chain.
     *
     * @return a configured runner
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    static Runner defaultRunner() {
        final var configuration = Configuration.defaultConfiguration();
        return new ConcreteRunner(configuration, Listener.defaultListener(configuration));
    }

    /**
     * Creates a builder for runner instances.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the effective configuration used by this runner.
     *
     * @return the configuration; never {@code null}
     */
    Configuration configuration();

    /**
     * Executes the action.
     *
     * @param action the action to execute; must not be {@code null}
     * @return the run result containing the root descriptor and effective aggregate status; never {@code null}
     * @throws NullPointerException if {@code action} is {@code null}
     * @throws IllegalStateException if the builder has already been resolved
     * @throws CycleDetectedException if discovery detects a cycle
     * @throws ConfigurationException if the configuration is invalid
     */
    Result run(Action action);

    /**
     * Resolves an action with the supplied selector and executes it when present.
     *
     * @param selector the selector used to locate an action; must not be {@code null}
     * @return the run result, or empty when no action is resolved
     * @throws NullPointerException if {@code selector} is {@code null}
     */
    Optional<Result> run(Selector selector);

    /**
     * Executes the action and converts the result
     * outcome to an exit code.
     *
     * @param action the action to execute; must not be {@code null}
     * @return {@code 0} for success and {@code 1} for failure
     * @throws NullPointerException if {@code action} is {@code null}
     * @throws IllegalStateException if the builder has already been resolved
     */
    int runAndReturnExitCode(Action action);

    /**
     * Resolves an action matching the supplied selector, executes it, and converts
     * the result outcome to an exit code.
     *
     * @param selector the selector used to locate an action; must not be {@code null}
     * @return {@code 0} for success and {@code 1} for failure
     * @throws NullPointerException if {@code selector} is {@code null}
     */
    int runAndReturnExitCode(Selector selector);

    /**
     * Executes the action and terminates the JVM
     * with the derived exit code.
     *
     * @param action the action to execute; must not be {@code null}
     * @throws NullPointerException if {@code action} is {@code null}
     * @throws IllegalStateException if the builder has already been resolved
     * @throws SecurityException if a security manager prevents {@link System#exit}
     */
    void runAndExit(Action action);

    /**
     * Resolves an action matching the supplied selector and terminates the JVM
     * with the derived exit code.
     *
     * @param selector the selector used to locate an action; must not be {@code null}
     * @throws NullPointerException if {@code selector} is {@code null}
     * @throws SecurityException if a security manager prevents {@link System#exit}
     */
    void runAndExit(Selector selector);

    /**
     * Discovers actions from the classpath and executes them.
     *
     * @return {@code 0} for success and {@code 1} for failure
     */
    int run();

    /**
     * Fluent builder for {@link Runner} instances.
     */
    final class Builder {

        private Configuration configuration;
        private Listener listener;
        private boolean explicitListener;
        private boolean shuffled;
        private long shuffleSeed;

        private Builder() {
            // Intentionally empty
        }

        /**
         * Sets the runner configuration.
         *
         * @param configuration the configuration; must not be {@code null}
         * @return this builder
         */
        public Builder configuration(final Configuration configuration) {
            this.configuration = Objects.requireNonNull(configuration, "configuration is null");
            return this;
        }

        /**
         * Sets the listener.
         *
         * @param listener the listener; must not be {@code null}
         * @return this builder
         */
        public Builder listener(final Listener listener) {
            this.listener = Objects.requireNonNull(listener, "listener is null");
            this.explicitListener = true;
            return this;
        }

        /**
         * Configures discovered action ordering to be shuffled randomly at build time.
         *
         * <p>The seed is generated from {@link ThreadLocalRandom} when {@link #build()} is called
         * and applies only to discovery-based execution ({@code run(Selector)} and {@code run()}).
         * Direct execution via {@code run(Action)} is unaffected — users control their own
         * builder shuffle for direct trees. For reproducible shuffling, use {@link #shuffle(long)}.
         *
         * @return this builder
         */
        public Builder shuffle() {
            this.shuffled = true;
            this.shuffleSeed = ThreadLocalRandom.current().nextLong();
            return this;
        }

        /**
         * Configures discovered action ordering to be shuffled with the supplied seed at build time.
         *
         * <p>Applies only to discovery-based execution ({@code run(Selector)} and {@code run()}).
         * Using the same seed produces the same ordering, enabling reproducible flaky-test investigations.
         *
         * @param seed the PRNG seed for reproducible shuffling
         * @return this builder
         */
        public Builder shuffle(final long seed) {
            this.shuffled = true;
            this.shuffleSeed = seed;
            return this;
        }

        /**
         * Builds the runner.
         *
         * @return a new runner
         */
        public Runner build() {
            final var effectiveConfiguration =
                    configuration == null ? Configuration.defaultConfiguration() : configuration;
            if (explicitListener) {
                return new ConcreteRunner(effectiveConfiguration, listener, shuffled, shuffleSeed);
            }
            return new ConcreteRunner(
                    effectiveConfiguration, Listener.defaultListener(effectiveConfiguration), shuffled, shuffleSeed);
        }
    }

    /**
     * Resolves and executes discovered actions using default configuration.
     *
     * @param args command-line arguments
     */
    static void main(final String[] args) {
        System.exit(builder().build().run());
    }
}

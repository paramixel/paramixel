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
import java.util.concurrent.ThreadLocalRandom;
import nonapi.org.paramixel.ConcreteRunner;
import nonapi.org.paramixel.listener.DefaultListeners;
import org.paramixel.api.action.Action;
import org.paramixel.api.exception.ConfigurationException;
import org.paramixel.api.selector.Selector;

/**
 * Executes Paramixel actions and returns a {@link Result} for every run.
 *
 * <p>There is one entry point per intent:
 *
 * <ul>
 *   <li>{@link #run(Action)} executes a tree you already built. It performs <strong>no</strong> discovery, no
 *       classpath scan and runs no {@code @BeforeAll}/{@code @AfterAll} hooks: the tree is the complete
 *       definition. Wrap it in a {@link org.paramixel.api.action.Scope} when it needs setup and teardown.</li>
 *   <li>{@link #run(Selector)} discovers factories using exactly the supplied selector and nothing else. If a
 *       {@code paramixel.match.*} key is set but the selector does not use {@link Selector#configured()}, the
 *       runner reports {@link Listener#onWarning(String)} rather than merging the key silently.</li>
 *   <li>{@link #run()} discovers factories using {@link Selector#configured()}.</li>
 * </ul>
 *
 * <p>Every entry point returns a {@link Result}; {@link Result#exitCode()} is the single source of truth for a
 * process exit code. Terminating the JVM is the caller's decision, which makes it visible at the call site:
 * {@code System.exit(Runner.builder().build().run().exitCode())}. {@link Launcher} does this for command-line use.
 *
 * <p>In a discovery run, hooks are global: {@code @BeforeAll} and {@code @AfterAll} methods are found across the
 * whole classpath, not just in the selected packages, because they wrap the entire run rather than individual
 * factories.
 *
 * <p>Implementations are not required to be thread-safe. Concurrent use of a single {@code Runner} instance from
 * multiple threads is undefined.
 *
 * @see Result
 * @see Launcher
 */
public interface Runner {

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
     * Executes the supplied action tree.
     *
     * <p>No discovery and no classpath scan happen, and no {@code @BeforeAll}/{@code @AfterAll} hook runs: the
     * supplied tree is the complete run. {@link Configuration#FAIL_FAST} applies to the direct children of the
     * root, which for a direct tree is the tree's own first level.
     *
     * @param action the action to execute; must not be {@code null}
     * @return the run result; never {@code null}
     * @throws NullPointerException if {@code action} is {@code null}
     * @throws ConfigurationException if the configuration is invalid
     */
    Result run(Action action);

    /**
     * Discovers factories with exactly the supplied selector and executes them.
     *
     * <p>The selector is used as given: no configuration key is merged into it. When any {@code paramixel.match.*}
     * key is set and the selector does not use {@link Selector#configured()}, the runner reports a warning through
     * {@link Listener#onWarning(String)} so the ignored key is not silent.
     *
     * @param selector the selector to use; must not be {@code null}
     * @return the run result, with an empty root descriptor when nothing was discovered; never {@code null}
     * @throws NullPointerException if {@code selector} is {@code null}
     * @throws ConfigurationException if the configuration is invalid
     */
    Result run(Selector selector);

    /**
     * Discovers factories with {@link Selector#configured()} and executes them.
     *
     * @return the run result, with an empty root descriptor when nothing was discovered; never {@code null}
     * @throws ConfigurationException if the configuration is invalid
     */
    Result run();

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
                    effectiveConfiguration, DefaultListeners.create(effectiveConfiguration), shuffled, shuffleSeed);
        }
    }
}

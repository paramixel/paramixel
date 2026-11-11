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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import nonapi.org.paramixel.RunnerImpl;
import org.paramixel.api.exception.CompilationException;
import org.paramixel.api.exception.ConfigurationException;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

/**
 * Executes task definitions and returns a {@link Result} for every run.
 *
 * <p>There is one entry point per intent:
 *
 * <ul>
 *   <li>{@link #run(Task)} executes a definition you built. It performs no discovery, no classpath scan, and runs no
 *       {@code @BeforeAll}/{@code @AfterAll} hooks: the definition is the complete run.</li>
 *   <li>{@link #run(Selector)} discovers factories using exactly the supplied selector and nothing else. A configured
 *       {@code paramixel.match.*} key that the selector does not use is reported as a warning rather than merged
 *       silently.</li>
 *   <li>{@link #run()} discovers factories using the configured selection.</li>
 * </ul>
 *
 * <p><strong>Concurrency contract:</strong> one run at a time per runner instance. A concurrent call is rejected with
 * {@link IllegalStateException} instead of producing undefined behavior. Independent runner instances may run
 * concurrently and share no mutable framework state.
 *
 * <p>{@link #close()} requests cooperative cancellation of an active run and releases long-lived runner resources. It is
 * idempotent, so try-with-resources and an explicit shutdown can both be used.
 *
 * <p>Validation happens before execution: an invalid definition, an undefined pool, or an illegal plan throws
 * {@link CompilationException}; an invalid configuration throws {@link ConfigurationException}; discovery problems
 * throw {@link ResolverException}. In all three cases no factory, lifecycle callback, or body has run.
 *
 * @see Result
 * @see Launcher
 */
public interface Runner extends AutoCloseable {

    /**
     * Creates a builder.
     *
     * @return a builder; never {@code null}
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the effective configuration of this runner.
     *
     * @return the configuration; never {@code null}
     */
    Configuration configuration();

    /**
     * Executes the supplied definition.
     *
     * @param definition the definition; must not be {@code null}
     * @return the result; never {@code null}
     * @throws NullPointerException if {@code definition} is {@code null}
     * @throws ConfigurationException if the configuration is invalid
     * @throws CompilationException if the definition or plan is invalid
     * @throws IllegalStateException if a run is already in progress on this runner
     */
    Result run(Task definition);

    /**
     * Discovers factories matching exactly the supplied selector and executes them.
     *
     * @param selector the selector; must not be {@code null}
     * @return the result, with no root when nothing matched; never {@code null}
     * @throws NullPointerException if {@code selector} is {@code null}
     * @throws ConfigurationException if the configuration is invalid
     * @throws ResolverException if discovery or factory validation fails
     * @throws CompilationException if the discovered definition is invalid
     * @throws IllegalStateException if a run is already in progress on this runner
     */
    Result run(Selector selector);

    /**
     * Discovers factories using the configured selection and executes them.
     *
     * @return the result, with no root when nothing matched; never {@code null}
     * @throws ConfigurationException if the configuration is invalid
     * @throws ResolverException if discovery or factory validation fails
     * @throws CompilationException if the discovered definition is invalid
     * @throws IllegalStateException if a run is already in progress on this runner
     */
    Result run();

    /**
     * Requests cooperative cancellation of an active run and releases runner resources. Idempotent.
     */
    @Override
    void close();

    /**
     * Fluent builder for {@link Runner} instances.
     */
    final class Builder {

        private Configuration configuration;
        private Listener listener;
        private boolean explicitListener;
        private boolean shuffled;
        private long shuffleSeed;
        private final Map<Pool, Integer> pools = new LinkedHashMap<>();

        private Builder() {
            // Intentionally empty
        }

        /**
         * Sets the runner configuration.
         *
         * @param configuration the configuration; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code configuration} is {@code null}
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
         * @throws NullPointerException if {@code listener} is {@code null}
         */
        public Builder listener(final Listener listener) {
            this.listener = Objects.requireNonNull(listener, "listener is null");
            this.explicitListener = true;
            return this;
        }

        /**
         * Configures the capacity of a named pool.
         *
         * <p>This override takes precedence over a configured property. A pool that a parallel task names but that is
         * neither configured here nor by property fails validation before any test code runs.
         *
         * @param pool the pool; must not be {@code null}
         * @param capacity the capacity; must be positive
         * @return this builder
         * @throws NullPointerException if {@code pool} is {@code null}
         * @throws IllegalArgumentException if {@code capacity} is not positive
         */
        public Builder pool(final Pool pool, final int capacity) {
            Objects.requireNonNull(pool, "pool is null");
            if (capacity <= 0) {
                throw new IllegalArgumentException("Pool capacity must be positive: " + capacity);
            }
            pools.put(pool, capacity);
            return this;
        }

        /**
         * Configures discovery ordering to be shuffled with a seed chosen when the runner is built.
         *
         * <p>Applies to discovery runs only; a direct definition controls its own shuffle. The chosen seed is reported
         * in {@link Result#shuffleSeed()} so the order can be reproduced.
         *
         * @return this builder
         */
        public Builder shuffle() {
            this.shuffled = true;
            this.shuffleSeed = ThreadLocalRandom.current().nextLong();
            return this;
        }

        /**
         * Configures discovery ordering to be shuffled with the supplied seed.
         *
         * @param seed the seed; the same seed yields the same discovered order
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
         * @return a runner; never {@code null}
         */
        public Runner build() {
            final var effective = configuration == null ? Configuration.defaultConfiguration() : configuration;
            final var effectiveListener = explicitListener ? listener : Listener.defaultListener(effective);
            return new RunnerImpl(effective, effectiveListener, shuffled, shuffleSeed, new LinkedHashMap<>(pools));
        }
    }
}

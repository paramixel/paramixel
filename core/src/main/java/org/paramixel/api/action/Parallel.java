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

package org.paramixel.api.action;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.regex.Pattern;
import nonapi.org.paramixel.Pools;
import nonapi.org.paramixel.action.RootParallels;
import nonapi.org.paramixel.support.Arguments;

/**
 * Composite action that executes its direct children concurrently, bounded by a named pool.
 *
 * <p>Every {@code Parallel} names a <em>pool</em>: a run-wide concurrency budget sized by
 * {@code paramixel.parallelism.<pool>} (see {@link org.paramixel.api.Configuration#poolKey(String)}). A size is a positive
 * integer, or {@code @cores}, {@code @cores*N} or {@code @cores/N}, which resolve against the processors the JVM
 * reports and are never less than one. Each direct child takes one permit from the pool before it starts and
 * returns it when the child's whole subtree is terminal, so across every {@code Parallel} that names the same pool
 * at most that many children are alive at once. Children are admitted in declaration order, and a later child
 * waits only for a permit, never for an earlier child to finish.
 *
 * <p>The runner's own root pool is not addressable: it is sized by {@code paramixel.parallelism} and is used by the
 * runner for discovery. A pool may therefore be named {@code root} like any other name, and
 * {@code paramixel.parallelism.root} sizes it.
 *
 * <p>Pools are named for what they limit, for example {@code docker} or {@code environments}, not for where
 * they sit in the tree. A pool that is not defined, or that is nested under a {@code Parallel} that names the
 * same pool (which could never make progress once the pool is exhausted), is rejected before anything runs.
 *
 * <p>Thread-safety: instances are immutable and safe for concurrent use.
 */
public final class Parallel implements Action {

    private static final Pattern POOL_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    static {
        RootParallels.register(displayName -> new Builder(displayName, Pools.ROOT_POOL_NAME, true));
    }

    private final String displayName;
    private final List<Action> children;
    private final String pool;
    private final long seed;
    private final boolean shuffled;

    private Parallel(
            final String displayName,
            final String pool,
            final List<Action> children,
            final long seed,
            final boolean shuffled) {
        this.displayName = Objects.requireNonNull(displayName, "displayName is null");
        this.children = validateChildren(children);
        this.pool = pool;
        this.seed = seed;
        this.shuffled = shuffled;
    }

    /**
     * Creates a new builder for a Parallel action with the given display name and pool.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @param pool the name of the pool that bounds this action's children; must start with a letter and contain
     *     only letters, digits, {@code _} and {@code -}
     * @return a new builder
     * @throws NullPointerException if {@code displayName} or {@code pool} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank or {@code pool} is not a valid pool name
     */
    public static Builder parallel(final String displayName, final String pool) {
        return new Builder(displayName, pool);
    }

    /**
     * Returns the action display name.
     *
     * @return the display name, never {@code null}
     */
    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the name of the pool that bounds this action's children.
     *
     * @return the pool name; never {@code null}
     */
    public String pool() {
        return pool;
    }

    /**
     * Returns the child actions.
     *
     * @return the child actions; never {@code null}
     */
    public List<Action> children() {
        return children;
    }

    /**
     * Returns the seed that shuffled the children at build time.
     *
     * <p>When present, the {@link #children()} list reflects the shuffled order and the value is the
     * seed passed to {@code new Random(seed)} before shuffling, enabling reproduction. When empty, the
     * children are in insertion order.
     *
     * @return the shuffle seed, or empty when the children were not shuffled
     */
    public Optional<Long> shuffleSeed() {
        return shuffled ? Optional.of(seed) : Optional.empty();
    }

    private static List<Action> validateChildren(final List<Action> children) {
        Objects.requireNonNull(children, "children is null");
        for (Action child : children) {
            Objects.requireNonNull(child, "children contains null element");
        }
        return List.copyOf(children);
    }

    /**
     * Fluent builder for {@link Parallel} actions.
     *
     * <p>Builders are reusable; calling {@link #build()} creates a new immutable
     * {@code Parallel} snapshot from the builder's current configuration without
     * modifying the builder's state.</p>
     */
    public static final class Builder implements ActionBuilder {

        private final String displayName;
        private final String pool;
        private final Children children = new Children();

        private Builder(final String displayName, final String pool) {
            this(displayName, pool, false);
        }

        private Builder(final String displayName, final String pool, final boolean frameworkPool) {
            Objects.requireNonNull(displayName, "displayName is null");
            Arguments.requireNonBlank(displayName, "displayName is blank");
            Objects.requireNonNull(pool, "pool is null");
            if (!frameworkPool && !POOL_NAME.matcher(pool).matches()) {
                throw new IllegalArgumentException("pool is not a valid pool name (letters, digits, '_' and '-', "
                        + "starting with a letter): '" + pool + "'");
            }
            this.displayName = displayName;
            this.pool = pool;
        }

        /**
         * Adds a child action. A builder argument is built immediately and the resulting
         * action snapshot is stored; an action argument is used as is.
         *
         * @param action the child action action or builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder child(final ActionBuilder action) {
            children.add(action);
            return this;
        }

        /**
         * Adds one child per supplied action or builder, in iteration order.
         *
         * @param actions the child actions or builders; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code actions} or an element is {@code null}
         */
        public Builder children(final Iterable<? extends ActionBuilder> actions) {
            children.addAll(actions);
            return this;
        }

        /**
         * Adds one child per item, mapping each item at define time.
         *
         * <p>{@code each} is {@code child(...)} called once per item in iteration order, so it mixes with fixed
         * {@code child(...)} calls and their declaration order is the pool's admission order. The mapper runs
         * eagerly, during definition; a throw from it is a define-time failure, and the mapper must not touch a
         * fixture (use an {@code Instance} supplier for that).
         *
         * @param <T> the item type
         * @param items the items to map; must not be {@code null}
         * @param mapper the mapper from item to child; must not be {@code null} or return {@code null}
         * @return this builder
         * @throws NullPointerException if {@code items}, {@code mapper}, or a mapped result is {@code null}
         */
        public <T> Builder each(final Iterable<T> items, final Function<? super T, ? extends ActionBuilder> mapper) {
            children.each(items, mapper);
            return this;
        }

        /**
         * Configures children to be shuffled randomly at build time.
         *
         * <p>A seed is generated from {@link ThreadLocalRandom} at build time and
         * stored on the resulting action for reporting. For reproducible shuffling,
         * use {@link #shuffle(long)}.
         *
         * @return this builder
         */
        public Builder shuffle() {
            children.shuffle();
            return this;
        }

        /**
         * Configures children to be shuffled with the supplied seed at build time.
         *
         * <p>Using the same seed on an identical tree produces the same shuffled
         * order, enabling reproducible flaky-test investigations. The seed value
         * {@code 0} is valid and will produce a consistent shuffle.
         *
         * @param seed the PRNG seed for reproducible shuffling
         * @return this builder
         */
        public Builder shuffle(final long seed) {
            children.shuffle(seed);
            return this;
        }

        /**
         * Builds an immutable {@link Parallel} action from this builder's configuration.
         *
         * @return a new Parallel action
         */
        @Override
        public Parallel build() {
            return new Parallel(displayName, pool, children.snapshot(), children.seed(), children.isShuffled());
        }
    }
}

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import nonapi.org.paramixel.support.Arguments;

/**
 * Composite action that makes every direct child ready at once.
 *
 * <p>A Parallel node never withholds a child: all direct children become ready as soon as the node
 * starts, and the scheduler dispatches their leaf actions in depth-first priority order. What
 * {@link #parallelism()} bounds is <em>execution</em>, not readiness: it is the maximum number of leaf
 * actions anywhere in this node's subtree that may be running at the same time. Nested caps compose —
 * every capped ancestor's limit applies independently, so a leaf runs only when each capped ancestor
 * has room.</p>
 *
 * <p>{@link #parallelism()} is an upper bound, not a guarantee. Actual concurrency may be lower due to
 * this cap, a capped ancestor, the global thread count ({@code paramixel.parallelism}), or the global
 * leaf concurrency cap ({@code paramixel.scheduler.concurrency.cap}).</p>
 *
 * <p>Thread-safety: instances are immutable and safe for concurrent use.</p>
 */
public final class Parallel implements Action {

    private final String displayName;
    private final List<Action> children;
    private final int parallelism;
    private final long seed;
    private final boolean shuffled;

    private Parallel(
            final String displayName,
            final int parallelism,
            final List<Action> children,
            final long seed,
            final boolean shuffled) {
        this.displayName = ActionNames.requireDisplayName(displayName);
        this.children = ActionChildren.validateChildren(children);
        this.parallelism = parallelism;
        this.seed = seed;
        this.shuffled = shuffled;
    }

    /**
     * Creates a new builder for a Parallel action with the given display name.
     *
     * @param displayName the action display name (must not be {@code null} or blank)
     * @return a new builder
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static Builder builder(final String displayName) {
        return new Builder(displayName);
    }

    /**
     * Creates a new builder for a {@code Parallel} action with the given display name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return a new builder
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     * @see Builder
     */
    public static Builder parallel(final String displayName) {
        return builder(displayName);
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
     * Returns the declared leaf-concurrency cap for this node's subtree.
     *
     * <p>When {@link Builder#parallelism(int)} is not called, this method returns {@link
     * Integer#MAX_VALUE} as an internal sentinel, meaning this node adds no cap and execution is
     * bounded only by capped ancestors, the global thread count ({@code paramixel.parallelism}), and
     * {@code paramixel.scheduler.concurrency.cap}.</p>
     *
     * <p>When explicitly configured, this value is the maximum number of leaf actions anywhere in
     * this node's subtree that may be running at the same time. It is a cap on running leaf tasks,
     * not on in-flight direct children: a child subtree does not consume this node's capacity while
     * it is merely ready or waiting on a sequential dependency.</p>
     *
     * <p>This value is a cap only. Runtime concurrency is best-effort and may be lower depending on
     * global and nested contention.</p>
     *
     * @return the parallelism limit
     */
    public int parallelism() {
        return parallelism;
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
     * Returns whether children were shuffled at build time.
     *
     * <p>When {@code true}, the {@link #children()} list reflects the shuffled order,
     * and {@link #seed()} returns the PRNG seed that produced it. When {@code false},
     * children are in insertion order.
     *
     * @return {@code true} if children were shuffled
     */
    public boolean isShuffled() {
        return shuffled;
    }

    /**
     * Returns the PRNG seed used to shuffle children.
     *
     * <p>Returns {@code 0} when this action was not shuffled. When
     * {@link #isShuffled()} returns {@code true}, this value is the seed
     * passed to {@code new Random(seed)} before shuffling, enabling
     * reproducible reordering.
     *
     * @return the shuffle seed, or {@code 0} when not shuffled
     */
    public long seed() {
        return seed;
    }

    /**
     * Fluent builder for {@link Parallel} actions.
     *
     * <p>Builders are reusable; calling {@link #build()} creates a new immutable
     * {@code Parallel} snapshot from the builder's current configuration without
     * modifying the builder's state.</p>
     */
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final String displayName;
        private final List<Action> children = new ArrayList<>();
        private int parallelism = Integer.MAX_VALUE;
        private boolean shuffled;
        private long shuffleSeed;

        private Builder(final String displayName) {
            ActionNames.requireDisplayName(displayName);
            this.displayName = displayName;
        }

        /**
         * Sets the maximum number of leaf actions that may run at the same time in this node's
         * subtree.
         *
         * <p>When this method is not called, this node adds no cap. When configured, the value
         * bounds running leaf tasks anywhere below this node — including inside nested Sequential
         * and Parallel children — and is combined with the caps of its own ancestors, the global
         * thread count ({@code paramixel.parallelism}), and
         * {@code paramixel.scheduler.concurrency.cap}. Whichever limit is tightest binds.</p>
         *
         * @param parallelism the maximum concurrent leaf count for this subtree (must be positive)
         * @return this builder
         * @throws IllegalArgumentException if {@code parallelism} is not positive
         */
        public Builder parallelism(final int parallelism) {
            Arguments.requirePositive(parallelism, "parallelism must be positive, was: " + parallelism);
            this.parallelism = parallelism;
            return this;
        }

        /**
         * Adds a child action.
         *
         * @param action the child action (must not be {@code null})
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder child(final Action action) {
            children.add(Objects.requireNonNull(action, "action is null"));
            return this;
        }

        /**
         * Adds a child action from a builder. The builder is built immediately
         * and the resulting action snapshot is stored.
         *
         * @param builder the child action builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code builder} is {@code null}
         */
        public Builder child(final org.paramixel.api.action.Builder builder) {
            Objects.requireNonNull(builder, "builder is null");
            children.add(Objects.requireNonNull(builder.build(), "builder.build() returned null"));
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
            this.shuffled = true;
            this.shuffleSeed = ThreadLocalRandom.current().nextLong();
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
            this.shuffled = true;
            this.shuffleSeed = seed;
            return this;
        }

        /**
         * Builds an immutable {@link Parallel} action from this builder's configuration.
         *
         * @return a new Parallel action
         */
        @Override
        public Parallel build() {
            final var mutableChildren = new ArrayList<>(children);
            if (shuffled) {
                Collections.shuffle(mutableChildren, new Random(shuffleSeed));
            }
            return new Parallel(displayName, parallelism, List.copyOf(mutableChildren), shuffleSeed, shuffled);
        }
    }
}

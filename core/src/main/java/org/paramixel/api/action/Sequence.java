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

/**
 * An action that executes its children sequentially in the order they were added.
 *
 * <p>When configured as <em>dependent</em> (the default), an
 * {@link org.paramixel.api.Status#FAILED FAILED} or {@link org.paramixel.api.Status#SKIPPED SKIPPED}
 * child causes all remaining children to be skipped. An {@link org.paramixel.api.Status#ABORTED ABORTED}
 * child is a local precondition/assumption failure and does <em>not</em> short-circuit the
 * sequence: remaining children still run, and the sequence aggregates to
 * {@link org.paramixel.api.Status#ABORTED}. (To stop a dependent sequence after a child, have
 * that child throw {@link org.paramixel.api.exception.FailException}.) When configured as
 * <em>independent</em>, all children run regardless of individual outcomes.
 */
@Deprecated(forRemoval = true, since = "6.2.0")
public final class Sequence implements Action {

    private final Sequential delegate;

    private Sequence(final Sequential delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is null");
    }

    /**
     * Creates a new builder for a {@code Sequence} action with the given display name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return a new builder
     */
    public static Builder builder(final String displayName) {
        return new Builder(displayName);
    }

    /**
     * Creates a new builder for a {@code Sequence} action with the given display name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return a new builder
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     * @see Builder
     */
    public static Builder sequence(final String displayName) {
        return builder(displayName);
    }

    @Override
    public String displayName() {
        return delegate.displayName();
    }

    /**
     * Returns whether children are dependent — i.e., an {@link org.paramixel.api.Status#FAILED FAILED}
     * or {@link org.paramixel.api.Status#SKIPPED SKIPPED} child causes remaining children to be
     * skipped (an {@link org.paramixel.api.Status#ABORTED ABORTED} child does not short-circuit).
     *
     * @return {@code true} if children are dependent
     */
    public boolean isDependent() {
        return delegate.isDependent();
    }

    /**
     * Returns whether children are independent — i.e., all run regardless of
     * individual outcomes.
     *
     * @return {@code true} if children are independent
     */
    public boolean isIndependent() {
        return delegate.isIndependent();
    }

    /**
     * Returns the child actions.
     *
     * @return the child actions; never {@code null}
     */
    public List<Action> children() {
        return delegate.children();
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
        return delegate.isShuffled();
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
        return delegate.seed();
    }

    /**
     * Fluent builder for {@link Sequence} actions.
     */
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final Sequential.Builder delegate;

        private Builder(final String displayName) {
            this.delegate = Sequential.builder(displayName);
        }

        /**
         * Configures the sequence action as dependent, so that an {@link org.paramixel.api.Status#FAILED FAILED}
         * or {@link org.paramixel.api.Status#SKIPPED SKIPPED} child causes remaining children to be
         * skipped. An {@link org.paramixel.api.Status#ABORTED ABORTED} child does not short-circuit.
         * This is the default.
         *
         * @return this builder
         */
        public Builder dependent() {
            delegate.dependent();
            return this;
        }

        /**
         * Configures the sequence action as independent, so that all children run
         * regardless of individual outcomes.
         *
         * @return this builder
         */
        public Builder independent() {
            delegate.independent();
            return this;
        }

        /**
         * Adds a child action.
         *
         * @param action the child action; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder child(final Action action) {
            delegate.child(action);
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
            delegate.child(builder);
            return this;
        }

        /**
         * Configures children to be shuffled randomly at build time.
         *
         * <p>A seed is generated from {@link java.util.concurrent.ThreadLocalRandom} at build time and
         * stored on the resulting action for reporting. For reproducible shuffling,
         * use {@link #shuffle(long)}.
         *
         * @return this builder
         */
        public Builder shuffle() {
            delegate.shuffle();
            return this;
        }

        /**
         * Configures children to be shuffled with the supplied seed at build time.
         *
         * <p>Using the same seed on an identical tree produces the same shuffled
         * order, enabling reproducible flaky-test investigations.
         *
         * @param seed the PRNG seed for reproducible shuffling
         * @return this builder
         */
        public Builder shuffle(final long seed) {
            delegate.shuffle(seed);
            return this;
        }

        /**
         * Builds the sequence action.
         *
         * @return a new sequence action
         */
        @Override
        public Sequence build() {
            return new Sequence(delegate.build());
        }
    }
}

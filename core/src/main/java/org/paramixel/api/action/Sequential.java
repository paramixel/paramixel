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
import nonapi.org.paramixel.support.Arguments;

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
public final class Sequential implements Action {

    private final String displayName;
    private final List<Action> children;
    private final boolean dependent;
    private final long seed;
    private final boolean shuffled;

    private Sequential(
            final String displayName,
            final List<Action> children,
            final boolean dependent,
            final long seed,
            final boolean shuffled) {
        this.displayName = Objects.requireNonNull(displayName, "displayName is null");
        this.children = validateChildren(children);
        this.dependent = dependent;
        this.seed = seed;
        this.shuffled = shuffled;
    }

    /**
     * Creates a new builder for a {@code Sequential} action with the given display name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return a new builder
     */
    public static Builder sequential(final String displayName) {
        return new Builder(displayName);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Returns whether children are dependent — i.e., an {@link org.paramixel.api.Status#FAILED FAILED}
     * or {@link org.paramixel.api.Status#SKIPPED SKIPPED} child causes remaining children to be
     * skipped (an {@link org.paramixel.api.Status#ABORTED ABORTED} child does not short-circuit).
     *
     * @return {@code true} if children are dependent
     */
    public boolean isDependent() {
        return dependent;
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
     * Fluent builder for {@link Sequential} actions.
     */
    public static final class Builder implements ActionBuilder {

        private final String displayName;
        private final Children children = new Children();
        private boolean dependent = true;

        private Builder(final String displayName) {
            Objects.requireNonNull(displayName, "displayName is null");
            Arguments.requireNonBlank(displayName, "displayName is blank");
            this.displayName = displayName;
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
            dependent = true;
            return this;
        }

        /**
         * Configures the sequence action as independent, so that all children run
         * regardless of individual outcomes.
         *
         * @return this builder
         */
        public Builder independent() {
            dependent = false;
            return this;
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
         * {@code child(...)} calls. The mapper runs eagerly, during definition; a throw from it is a define-time
         * failure, and the mapper must not touch a fixture (use an {@code Instance} supplier for that).
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
         * order, enabling reproducible flaky-test investigations.
         *
         * @param seed the PRNG seed for reproducible shuffling
         * @return this builder
         */
        public Builder shuffle(final long seed) {
            children.shuffle(seed);
            return this;
        }

        /**
         * Builds the sequence action.
         *
         * @return a new sequence action
         */
        @Override
        public Sequential build() {
            return new Sequential(displayName, children.snapshot(), dependent, children.seed(), children.isShuffled());
        }
    }
}

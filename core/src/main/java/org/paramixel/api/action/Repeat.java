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

import java.util.Objects;
import nonapi.org.paramixel.support.Arguments;

/**
 * An action that executes a single child action a configurable number of times.
 *
 * <p>The child action instance is reused for all repetitions. If
 * the child has mutable state, that state persists across repetitions. The
 * child action must therefore be stateless or designed for repeated execution.
 * This is the test author's responsibility to ensure; {@code Repeat} does not
 * clone or reset the child between repetitions.
 *
 * <p>All repetitions run regardless of individual outcomes. A failure in a single
 * repetition does not prevent subsequent repetitions from running.
 */
public final class Repeat implements Action {

    private final String displayName;
    private final Action body;
    private final int iterations;

    private Repeat(final String displayName, final Action body, final int iterations) {
        Objects.requireNonNull(displayName, "displayName is null");
        this.displayName = Arguments.requireNonBlank(displayName, "displayName is blank");
        this.body = Objects.requireNonNull(body, "child is null");
        this.iterations = validateRepeatCount(iterations);
    }

    /**
     * Creates a new builder for a {@code Repeat} action with the given display name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return a new builder
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static Builder builder(final String displayName) {
        Objects.requireNonNull(displayName, "displayName is null");
        Arguments.requireNonBlank(displayName, "displayName is blank");
        return new Builder(displayName);
    }

    /**
     * Creates a new builder for a {@code Repeat} action with the given display name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return a new builder
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     * @see Builder
     */
    public static Builder repeat(final String displayName) {
        return builder(displayName);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the child action being repeated.
     *
     * <p>The returned instance is the same object used for all repetitions. If
     * the child has mutable state, that state persists across repetitions.
     *
     * @return the child action; never {@code null}
     */
    public Action body() {
        return body;
    }

    /**
     * Returns the number of repetitions.
     *
     * @return the repeat count
     */
    public int iterations() {
        return iterations;
    }

    private static int validateRepeatCount(final int repeatCount) {
        Arguments.requireTrue(repeatCount > 0, "repeatCount must be positive, was: " + repeatCount);
        return repeatCount;
    }

    /**
     * Fluent builder for {@link Repeat} actions.
     */
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final String displayName;
        private Action child;
        private int iterations = 1;

        private Builder(final String displayName) {
            this.displayName = displayName;
        }

        /**
         * Sets the child action.
         *
         * @param action the child action; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder body(final Action action) {
            this.child = Objects.requireNonNull(action, "action is null");
            return this;
        }

        /**
         * Sets the child action from a builder. The builder is built immediately
         * and the resulting action snapshot is stored.
         *
         * @param builder the child action builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code builder} is {@code null}
         */
        public Builder body(final org.paramixel.api.action.Builder builder) {
            Objects.requireNonNull(builder, "builder is null");
            this.child = Objects.requireNonNull(builder.build(), "builder.build() returned null");
            return this;
        }

        /**
         * Sets the number of repetitions.
         *
         * @param iterations the number of iterations to repeat the child; must be positive
         * @return this builder
         * @throws IllegalArgumentException if {@code iterations} is not positive
         */
        public Builder iterations(final int iterations) {
            Arguments.requireTrue(iterations > 0, "iterations must be positive, was: " + iterations);
            this.iterations = iterations;
            return this;
        }

        /**
         * Builds the repeat action.
         *
         * @return a new repeat action
         * @throws IllegalStateException if no child action was configured
         */
        @Override
        public Repeat build() {
            if (child == null) {
                throw new IllegalStateException("child action must be configured");
            }
            return new Repeat(displayName, child, iterations);
        }
    }
}

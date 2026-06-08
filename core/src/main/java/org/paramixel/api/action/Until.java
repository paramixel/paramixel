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
import java.util.Optional;
import java.util.function.Predicate;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Context;
import org.paramixel.api.exception.AbortedException;

/**
 * An action that executes a body child repeatedly until a termination condition
 * is satisfied or {@link #maxIterations()} is exhausted.
 *
 * <p>Individual iteration failures do not terminate the loop — they are treated
 * as "try again" signals. Only an {@link AbortedException}
 * from the body causes an immediate stop with {@code ABORTED} status.
 *
 * <p>When a {@link Predicate}{@code <Context>} is configured via
 * {@link Builder#until(Predicate)}, the predicate is evaluated after each
 * iteration. A return value of {@code true} signals satisfaction and stops
 * the loop with {@code PASSED} status. When the predicate is absent, the loop
 * stops when the body action passes.
 *
 * <p>If all iterations are exhausted without satisfaction, the action reports
 * {@code FAILED}.
 *
 * <p>The body action instance is reused for all iterations. If the body has
 * mutable state, that state persists across iterations. The body action must
 * therefore be stateless or designed for repeated execution. This is the test
 * author's responsibility to ensure; {@code Until} does not clone or reset
 * the body between iterations.
 */
public final class Until implements Action {

    private final String displayName;
    private final Action body;
    private final int maxIterations;
    private final Predicate<Context> until;

    private Until(
            final String displayName, final Action body, final int maxIterations, final Predicate<Context> until) {
        Objects.requireNonNull(displayName, "displayName is null");
        this.displayName = Arguments.requireNonBlank(displayName, "displayName is blank");
        this.body = Objects.requireNonNull(body, "body is null");
        Arguments.requireTrue(maxIterations > 0, "maxIterations must be positive, was: " + maxIterations);
        this.maxIterations = maxIterations;
        this.until = until;
    }

    /**
     * Creates a new builder for an {@code Until} action with the given display name.
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

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the child action being executed each iteration.
     *
     * <p>The returned instance is the same object used for all iterations. If
     * the child has mutable state, that state persists across iterations.
     *
     * @return the child action; never {@code null}
     */
    public Action body() {
        return body;
    }

    /**
     * Returns the maximum number of iterations.
     *
     * @return the maximum iteration count
     */
    public int maxIterations() {
        return maxIterations;
    }

    /**
     * Returns the termination predicate, if configured.
     *
     * <p>When present, the predicate is evaluated after each iteration; a return value of
     * {@code true} signals satisfaction and stops the loop. When absent, the loop
     * stops when the body action passes.
     *
     * @return the termination predicate; never {@code null} (wrapped in {@code Optional})
     */
    public Optional<Predicate<Context>> until() {
        return Optional.ofNullable(until);
    }

    /**
     * Fluent builder for {@link Until} actions.
     */
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final String displayName;
        private Action body;
        private int maxIterations = -1;
        private Predicate<Context> until;

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
            this.body = Objects.requireNonNull(action, "action is null");
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
            this.body = Objects.requireNonNull(builder.build(), "builder.build() returned null");
            return this;
        }

        /**
         * Sets the termination predicate.
         *
         * <p>When configured, the predicate is evaluated after each iteration; a return
         * value of {@code true} signals satisfaction and stops the loop. When {@code null}
         * or not configured, the loop stops when the body action passes.
         *
         * @param predicate the termination predicate; {@code null} to clear a previously configured predicate
         * @return this builder
         */
        public Builder until(final Predicate<Context> predicate) {
            this.until = predicate;
            return this;
        }

        /**
         * Sets the maximum number of iterations.
         *
         * @param maxIterations the maximum iteration count; must be positive
         * @return this builder
         * @throws IllegalArgumentException if {@code maxIterations} is not positive
         */
        public Builder maxIterations(final int maxIterations) {
            Arguments.requireTrue(maxIterations > 0, "maxIterations must be positive, was: " + maxIterations);
            this.maxIterations = maxIterations;
            return this;
        }

        /**
         * Builds the until action.
         *
         * @return a new until action
         * @throws IllegalStateException if no child action was configured
         * @throws IllegalStateException if maxIterations was not configured
         */
        @Override
        public Until build() {
            if (body == null) {
                throw new IllegalStateException("body action must be configured");
            }
            if (maxIterations <= 0) {
                throw new IllegalStateException("maxIterations must be configured");
            }
            return new Until(displayName, body, maxIterations, until);
        }
    }
}

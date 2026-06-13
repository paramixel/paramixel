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

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Context;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.PolicyException;

/**
 * An action that executes a body action repeatedly for up to {@link #maxIterations()}
 * iterations, with an optional termination predicate and an optional inter-iteration delay.
 *
 * <p>When no predicate is configured, all iterations run regardless of individual
 * outcomes. This provides simple bounded repetition.
 *
 * <p>When a {@link Predicate}{@code <Context>} is configured via
 * {@link Builder#until(Predicate)}, the predicate is evaluated after each
 * iteration. A return value of {@code true} signals satisfaction and stops
 * the loop with {@code PASSED} status.
 *
 * <p>When a {@link DelayPolicy} is configured via {@link Builder#delay(DelayPolicy)},
 * the loop pauses between iterations according to the policy. The delay is applied
 * after iteration N completes and before iteration N+1 starts. Delay never fires
 * before the first iteration, never after the last iteration, never after abort,
 * and never after predicate satisfaction.
 *
 * <p>Individual iteration failures do not terminate the loop &mdash; they are treated
 * as &quot;try again&quot; signals. Only an {@link AbortedException}
 * from the body causes an immediate stop with {@code ABORTED} status.
 *
 * <p>If all iterations are exhausted without satisfaction, the action reports
 * {@code FAILED}.
 *
 * <p>The body action instance is reused for all iterations. If the body has
 * mutable state, that state persists across iterations. The body action must
 * therefore be stateless or designed for repeated execution. This is the test
 * author's responsibility to ensure; {@code Loop} does not clone or reset
 * the body between iterations.
 */
public final class Loop implements Action {

    private final String displayName;
    private final Action body;
    private final int maxIterations;
    private final Predicate<Context> until;
    private final DelayPolicy delayPolicy;

    private Loop(
            final String displayName,
            final Action body,
            final int maxIterations,
            final Predicate<Context> until,
            final DelayPolicy delayPolicy) {
        Objects.requireNonNull(displayName, "displayName is null");
        this.displayName = Arguments.requireNonBlank(displayName, "displayName is blank");
        this.body = Objects.requireNonNull(body, "body is null");
        Arguments.requireTrue(maxIterations > 0, "maxIterations must be positive, was: " + maxIterations);
        this.maxIterations = maxIterations;
        this.until = until;
        this.delayPolicy = delayPolicy;
    }

    /**
     * Creates a new builder for a {@code Loop} action with the given display name.
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
     * Creates a new builder for a {@code Loop} action with the given display name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return a new builder
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     * @see Builder
     */
    public static Builder loop(final String displayName) {
        return builder(displayName);
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
     * {@code true} signals satisfaction and stops the loop. When absent, all iterations
     * are executed regardless of individual outcomes.
     *
     * @return the termination predicate; never {@code null} (wrapped in {@code Optional})
     */
    public Optional<Predicate<Context>> until() {
        return Optional.ofNullable(until);
    }

    /**
     * Returns the inter-iteration delay policy, if configured.
     *
     * <p>When present, the loop pauses between iterations according to the policy.
     * Delay is applied after iteration N completes and before iteration N+1 starts.
     * Delay never fires before the first iteration, never after the last iteration,
     * never after abort, and never after predicate satisfaction.
     *
     * @return the delay policy; never {@code null} (wrapped in {@code Optional})
     */
    public Optional<DelayPolicy> delay() {
        return Optional.ofNullable(delayPolicy);
    }

    /**
     * Defines a delay schedule applied between loop iterations.
     *
     * <p>A delay policy determines how long to pause after a completed iteration
     * before starting the next one. Delay is never applied before the first
     * iteration, after the last iteration, after abort, or after predicate
     * satisfaction.
     *
     * <p>Two built-in implementations are provided:
     * <ul>
     *   <li>{@link Linear} &mdash; constant delay between every iteration</li>
     *   <li>{@link Exponential} &mdash; doubling delay (baseDelay, 2*baseDelay, 4*baseDelay, ...)</li>
     * </ul>
     */
    public sealed interface DelayPolicy permits DelayPolicy.Linear, DelayPolicy.Exponential {

        /**
         * Returns the delay after a completed iteration, before the next begins.
         *
         * @param completedIteration 1-based number of the iteration just completed
         * @return the delay duration; never null, negative values return {@link Duration#ZERO}
         */
        Duration delayForIteration(int completedIteration);

        /**
         * Constant delay between every iteration.
         *
         * @param delay the delay duration between iterations; must not be {@code null} or negative
         */
        record Linear(Duration delay) implements DelayPolicy {
            /**
             * Creates a linear (constant) delay policy.
             *
             * @param delay the delay duration between iterations; must not be {@code null} or negative
             * @throws NullPointerException if {@code delay} is {@code null}
             * @throws IllegalArgumentException if {@code delay} is negative
             */
            public Linear {
                Objects.requireNonNull(delay, "delay is null");
                Arguments.requireNonNegative(delay.toMillis(), "delay is negative");
            }

            @Override
            public Duration delayForIteration(int completedIteration) {
                if (completedIteration < 1) {
                    return Duration.ZERO;
                }
                return delay;
            }
        }

        /**
         * Doubling delay: baseDelay, 2*baseDelay, 4*baseDelay, ...
         *
         * @param baseDelay the base delay duration before exponential backoff; must not be {@code null} or negative
         */
        record Exponential(Duration baseDelay) implements DelayPolicy {
            /**
             * Creates an exponential delay policy.
             *
             * @param baseDelay the base delay duration; must not be {@code null} or negative
             * @throws NullPointerException if {@code baseDelay} is {@code null}
             * @throws IllegalArgumentException if {@code baseDelay} is negative
             */
            public Exponential {
                Objects.requireNonNull(baseDelay, "baseDelay is null");
                Arguments.requireNonNegative(baseDelay.toMillis(), "baseDelay is negative");
            }

            @Override
            public Duration delayForIteration(int completedIteration) {
                if (completedIteration < 1) {
                    return Duration.ZERO;
                }
                int exponent = completedIteration - 1;
                long multiplier = 1L << Math.min(exponent, 62);
                return multiplyOrZero(baseDelay, multiplier);
            }
        }

        /**
         * Multiplies a duration by a multiplier, returning {@link Duration#ZERO} on overflow.
         *
         * @param duration the duration to multiply
         * @param multiplier the multiplier
         * @return the multiplied duration
         * @throws PolicyException if the multiplication overflows
         */
        private static Duration multiplyOrZero(final Duration duration, final long multiplier) {
            if (multiplier < 0) {
                return Duration.ZERO;
            }
            try {
                var result = duration.multipliedBy(multiplier);
                if (result.isNegative()) {
                    return Duration.ZERO;
                }
                return result;
            } catch (ArithmeticException e) {
                throw new PolicyException("duration overflow multiplying by " + multiplier, e);
            }
        }
    }

    /**
     * Fluent builder for {@link Loop} actions.
     */
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final String displayName;
        private Action body;
        private int maxIterations = -1;
        private Predicate<Context> until;
        private DelayPolicy delayPolicy;

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
         * value of {@code true} signals satisfaction and stops the loop. When not
         * configured, all iterations are executed regardless of individual outcomes.
         *
         * @param predicate the termination predicate; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code predicate} is {@code null}
         */
        public Builder until(final Predicate<Context> predicate) {
            this.until = Objects.requireNonNull(predicate, "predicate is null");
            return this;
        }

        /**
         * Sets the inter-iteration delay policy.
         *
         * <p>When configured, the loop pauses between iterations according to the
         * policy. The delay is applied after iteration N completes and before
         * iteration N+1 starts, never before the first iteration, never after the
         * last iteration, never after abort, and never after predicate satisfaction.
         *
         * @param policy the delay policy; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code policy} is {@code null}
         */
        public Builder delay(final DelayPolicy policy) {
            this.delayPolicy = Objects.requireNonNull(policy, "policy is null");
            return this;
        }

        /**
         * Sets a constant inter-iteration delay.
         *
         * <p>This is a convenience overload that wraps the duration in a
         * {@link DelayPolicy.Linear} policy.
         *
         * @param fixedDelay the fixed delay between iterations; must not be {@code null} or negative
         * @return this builder
         * @throws NullPointerException if {@code fixedDelay} is {@code null}
         * @throws IllegalArgumentException if {@code fixedDelay} is negative
         */
        public Builder delay(final Duration fixedDelay) {
            Objects.requireNonNull(fixedDelay, "fixedDelay is null");
            Arguments.requireNonNegative(fixedDelay.toMillis(), "fixedDelay is negative");
            this.delayPolicy = new DelayPolicy.Linear(fixedDelay);
            return this;
        }

        /**
         * Sets a constant inter-iteration delay in milliseconds.
         *
         * <p>This is a convenience overload that wraps the millisecond value in a
         * {@link DelayPolicy.Linear} policy.
         *
         * @param fixedDelayMillis the fixed delay in milliseconds; must not be negative
         * @return this builder
         * @throws IllegalArgumentException if {@code fixedDelayMillis} is negative
         */
        public Builder delay(final long fixedDelayMillis) {
            Arguments.requireNonNegative(fixedDelayMillis, "fixedDelayMillis is negative");
            this.delayPolicy = new DelayPolicy.Linear(Duration.ofMillis(fixedDelayMillis));
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
         * Builds the loop action.
         *
         * @return a new loop action
         * @throws IllegalStateException if no child action was configured
         * @throws IllegalStateException if maxIterations was not configured
         */
        @Override
        public Loop build() {
            if (body == null) {
                throw new IllegalStateException("body action must be configured");
            }
            if (maxIterations <= 0) {
                throw new IllegalStateException("maxIterations must be configured");
            }
            return new Loop(displayName, body, maxIterations, until, delayPolicy);
        }
    }
}

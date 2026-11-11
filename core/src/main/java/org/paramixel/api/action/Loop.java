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
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Context;
import org.paramixel.api.Status;
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.support.Backoff;

/**
 * An action that executes a body action repeatedly for up to {@link #maxIterations()}
 * iterations, with an optional termination predicate and an optional inter-iteration delay.
 *
 * <p>When no predicate is configured, all iterations run regardless of individual
 * outcomes. This provides simple bounded repetition.
 *
 * <p>When a predicate is configured through {@link Builder#until(java.util.function.BiPredicate)},
 * {@link Builder#until(Predicate)} or {@link Builder#untilPassed()}, it is evaluated after each
 * iteration and a {@code true} answer stops the loop with {@code PASSED} status. The
 * {@code BiPredicate} form also receives the iteration's {@link org.paramixel.api.Status}, so
 * "stop when it passed" needs nothing else.
 *
 * <p>Iterations that never run because the predicate was satisfied carry
 * {@code Status.skipped("loop satisfied")}, so a report can collapse them instead of counting them as real skips.
 *
 * <p>When a {@link org.paramixel.api.support.Backoff} is configured via {@link Builder#delay(Backoff)},
 * the loop pauses between iterations according to the schedule. The delay is applied
 * after iteration N completes and before iteration N+1 starts. Delay never fires
 * before the first iteration, never after the last iteration, never after abort,
 * and never after predicate satisfaction.
 *
 * <p>Individual iteration failures do not terminate the loop &mdash; they are treated
 * as &quot;try again&quot; signals. Only an {@link AbortException}
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
    private final BiPredicate<Context, Status> until;
    private final Backoff delay;

    private Loop(
            final String displayName,
            final Action body,
            final int maxIterations,
            final BiPredicate<Context, Status> until,
            final Backoff delay) {
        this.displayName = Objects.requireNonNull(displayName, "displayName is null");
        this.body = Objects.requireNonNull(body, "body is null");
        Arguments.requireTrue(maxIterations > 0, "maxIterations must be positive, was: " + maxIterations);
        this.maxIterations = maxIterations;
        this.until = until;
        this.delay = delay;
    }

    /**
     * Creates a new builder for a {@code Loop} action with the given display name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return a new builder
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static Builder loop(final String displayName) {
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
     * <p>When present, the predicate is evaluated after each iteration with the execution context and that
     * iteration's status; a return value of {@code true} signals satisfaction and stops the loop.
     *
     * @return the termination predicate, or empty when all iterations run
     */
    public Optional<BiPredicate<Context, Status>> until() {
        return Optional.ofNullable(until);
    }

    /**
     * Returns the inter-iteration delay schedule, if configured.
     *
     * <p>Delay is applied after iteration N completes and before iteration N+1 starts. Delay never fires
     * before the first iteration, never after the last iteration, never after abort, and never after predicate
     * satisfaction.
     *
     * @return the delay schedule, or empty when iterations follow each other immediately
     */
    public Optional<Backoff> delay() {
        return Optional.ofNullable(delay);
    }

    /**
     * Fluent builder for {@link Loop} actions.
     */
    public static final class Builder implements ActionBuilder {

        private final String displayName;
        private Action body;
        private int maxIterations = -1;
        private BiPredicate<Context, Status> until;
        private Backoff delay;

        private Builder(final String displayName) {
            this.displayName = displayName;
        }

        /**
         * Sets the child action. A builder argument is built immediately and the resulting
         * action snapshot is stored; an action argument is used as is.
         *
         * @param action the child action action or builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder body(final ActionBuilder action) {
            Objects.requireNonNull(action, "action is null");
            this.body = Objects.requireNonNull(action.build(), "action.build() returned null");
            return this;
        }

        /**
         * Sets the termination predicate over the iteration status.
         *
         * <p>The predicate receives the execution context and the status of the iteration that just completed.
         * Returning {@code true} stops the loop with {@code PASSED} status.
         *
         * @param predicate the termination predicate; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code predicate} is {@code null}
         */
        public Builder until(final BiPredicate<Context, Status> predicate) {
            this.until = Objects.requireNonNull(predicate, "predicate is null");
            return this;
        }

        /**
         * Sets the termination predicate.
         *
         * @param predicate the termination predicate; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code predicate} is {@code null}
         */
        public Builder until(final Predicate<Context> predicate) {
            Objects.requireNonNull(predicate, "predicate is null");
            this.until = (context, status) -> predicate.test(context);
            return this;
        }

        /**
         * Stops the loop after the first iteration that passes.
         *
         * <p>Equivalent to {@code until((context, status) -> status.isPassed())}, and the usual way to retry until
         * something works.
         *
         * @return this builder
         */
        public Builder untilPassed() {
            this.until = (context, status) -> status.isPassed();
            return this;
        }

        /**
         * Sets the inter-iteration delay schedule.
         *
         * <p>The delay is applied after iteration N completes and before iteration N+1 starts, never before the
         * first iteration, never after the last iteration, never after an abort, and never after the predicate
         * was satisfied.
         *
         * @param backoff the delay schedule; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code backoff} is {@code null}
         */
        public Builder delay(final Backoff backoff) {
            this.delay = Objects.requireNonNull(backoff, "backoff is null");
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
            return new Loop(displayName, body, maxIterations, until, delay);
        }
    }
}

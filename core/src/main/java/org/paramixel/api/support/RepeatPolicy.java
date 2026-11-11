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

package org.paramixel.api.support;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.paramixel.api.Context;
import org.paramixel.api.Status;

/**
 * An immutable repetition policy for a task.
 *
 * <p><strong>Repeat is not retry.</strong> Repetition runs the same subtree an intentional, bounded number of times —
 * for example to poll a service until it is ready — rather than re-attempting a failure. Each iteration has its own
 * result identity and timestamps, and an owned {@code forInstance} instance inside the repeated subtree is recreated
 * for every iteration.
 *
 * <p>Iteration always stops at {@link #maxIterations()}. It may also stop early when {@link #untilPassed()} is set and
 * an iteration passes, or when the optional {@code until} predicate returns {@code true} for an iteration.
 */
public final class RepeatPolicy {

    /** The largest accepted iteration bound; a larger value is rejected as unbounded work. */
    public static final int MAX_ITERATIONS = 10_000;

    private final int maxIterations;
    private final boolean untilPassed;
    private final BiPredicate<Context, Status> until;
    private final Backoff delay;

    private RepeatPolicy(
            final int maxIterations,
            final boolean untilPassed,
            final BiPredicate<Context, Status> until,
            final Backoff delay) {
        this.maxIterations = maxIterations;
        this.untilPassed = untilPassed;
        this.until = until;
        this.delay = delay;
    }

    /**
     * Returns a builder.
     *
     * @return a new builder; never {@code null}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the iteration bound.
     *
     * @return the bound; at least one
     */
    public int maxIterations() {
        return maxIterations;
    }

    /**
     * Returns whether iteration stops early on the first passing iteration.
     *
     * @return {@code true} when the policy stops on success
     */
    public boolean untilPassed() {
        return untilPassed;
    }

    /**
     * Returns the optional stop predicate.
     *
     * @return the predicate, or {@code null} when no predicate is configured
     */
    public BiPredicate<Context, Status> until() {
        return until;
    }

    /**
     * Returns the delay schedule between iterations.
     *
     * @return the schedule; never {@code null}
     */
    public Backoff delay() {
        return delay;
    }

    /**
     * Returns the delay before the supplied 1-based iteration.
     *
     * @param nextIteration the iteration about to start; at least two
     * @return the delay; never {@code null}
     */
    public Duration delayBefore(final int nextIteration) {
        return delay.delayFor(Math.max(1, nextIteration - 1));
    }

    /**
     * Returns whether iteration should stop after the supplied iteration.
     *
     * @param iteration the 1-based iteration that just finished; must not be {@code null}
     * @param status the iteration status; must not be {@code null}
     * @param context the execution context of the repeated task; must not be {@code null}
     * @return {@code true} when no further iteration may start
     * @throws NullPointerException if any argument is {@code null}
     */
    public boolean stopsAfter(final int iteration, final Status status, final Context context) {
        Objects.requireNonNull(status, "status is null");
        Objects.requireNonNull(context, "context is null");
        if (iteration >= maxIterations) {
            return true;
        }
        if (untilPassed && status.isPassed()) {
            return true;
        }
        return until != null && until.test(context, status);
    }

    @Override
    public String toString() {
        return "RepeatPolicy[maxIterations=" + maxIterations + ", untilPassed=" + untilPassed + ", delay=" + delay
                + "]";
    }

    /**
     * Builds {@link RepeatPolicy} instances.
     */
    public static final class Builder {

        private Integer maxIterations;
        private boolean untilPassed;
        private BiPredicate<Context, Status> until;
        private Backoff delay = Backoff.fixed(Duration.ZERO);

        private Builder() {
            // Intentionally empty
        }

        /**
         * Sets the iteration bound. Repetition requires an explicit finite bound.
         *
         * @param maxIterations the bound; must be between 1 and {@value #MAX_ITERATIONS}
         * @return this builder
         * @throws IllegalArgumentException if the bound is outside the accepted range
         */
        public Builder maxIterations(final int maxIterations) {
            if (maxIterations < 1 || maxIterations > MAX_ITERATIONS) {
                throw new IllegalArgumentException(
                        "maxIterations must be between 1 and " + MAX_ITERATIONS + ": " + maxIterations);
            }
            this.maxIterations = maxIterations;
            return this;
        }

        /**
         * Stops iterating as soon as an iteration passes.
         *
         * @return this builder
         */
        public Builder untilPassed() {
            this.untilPassed = true;
            return this;
        }

        /**
         * Stops iterating when the supplied predicate returns {@code true}.
         *
         * @param until the predicate, receiving the context and the iteration status; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code until} is {@code null}
         */
        public Builder until(final BiPredicate<Context, Status> until) {
            this.until = Objects.requireNonNull(until, "until is null");
            return this;
        }

        /**
         * Sets the delay schedule between iterations.
         *
         * @param delay the schedule; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code delay} is {@code null}
         */
        public Builder delay(final Backoff delay) {
            this.delay = Objects.requireNonNull(delay, "delay is null");
            return this;
        }

        /**
         * Builds the policy.
         *
         * @return the immutable policy; never {@code null}
         * @throws IllegalStateException if no iteration bound was set
         */
        public RepeatPolicy build() {
            if (maxIterations == null) {
                throw new IllegalStateException("A repeat task requires an explicit maxIterations bound");
            }
            return new RepeatPolicy(maxIterations, untilPassed, until, delay);
        }
    }
}

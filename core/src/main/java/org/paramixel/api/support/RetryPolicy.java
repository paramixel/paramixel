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
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.paramixel.api.Status;

/**
 * An immutable retry policy for a task.
 *
 * <p><strong>Retry is not repeat.</strong> A retry re-attempts the same task only after a non-success outcome that the
 * policy names, up to a finite attempt limit, and never concurrently. A retried task owns its context scope, so each
 * attempt starts with an empty scope: a fixture built in its {@code before} slot is rebuilt, and its {@code after}
 * hooks run before the next attempt begins. Entries inherited from an enclosing scope stay visible, and an ancestor's
 * scope is never reconstructed.
 *
 * <p>The trigger set defaults to {@link Status.Kind#FAILED} only and may contain only the kinds in
 * {@link #RETRYABLE_KINDS}: {@link Status.Kind#FAILED} and {@link Status.Kind#SKIPPED}. A passing outcome is never
 * re-attempted, and run cancellation and assumption aborts are never retried automatically, so a policy can neither
 * turn a pass into a later failure nor silently mask an intentional stop.
 *
 * <p>Instances are immutable and safe to share.
 */
public final class RetryPolicy {

    /** The largest accepted attempt limit; a larger value is rejected as unbounded work. */
    public static final int MAX_ATTEMPTS = 10_000;

    /**
     * The only status kinds a retry policy may name as triggers: the non-success outcomes {@code FAILED} and
     * {@code SKIPPED}. This set is the single definition of a legal trigger for both the builder and plan validation.
     */
    public static final Set<Status.Kind> RETRYABLE_KINDS =
            Collections.unmodifiableSet(EnumSet.of(Status.Kind.FAILED, Status.Kind.SKIPPED));

    private final int maxAttempts;
    private final Set<Status.Kind> retryOn;
    private final Backoff backoff;

    private RetryPolicy(final int maxAttempts, final Set<Status.Kind> retryOn, final Backoff backoff) {
        this.maxAttempts = maxAttempts;
        this.retryOn = Set.copyOf(retryOn);
        this.backoff = backoff;
    }

    /**
     * Returns a policy with the supplied attempt limit, retrying only {@code FAILED} outcomes with no delay.
     *
     * @param maxAttempts the total number of attempts including the first; must be between 1 and
     *     {@value #MAX_ATTEMPTS}
     * @return the policy; never {@code null}
     * @throws IllegalArgumentException if {@code maxAttempts} is outside the accepted range
     */
    public static RetryPolicy attempts(final int maxAttempts) {
        return builder().maxAttempts(maxAttempts).build();
    }

    /**
     * Returns a retry policy builder.
     *
     * @return a new builder; never {@code null}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the total attempt limit.
     *
     * @return the attempt limit; at least one
     */
    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Returns whether more attempts are permitted after the supplied 1-based attempt.
     *
     * @param attempt the 1-based attempt that just finished
     * @return {@code true} when another attempt is permitted
     */
    public boolean permitsAnotherAttempt(final int attempt) {
        return attempt < maxAttempts;
    }

    /**
     * Returns the triggering statuses.
     *
     * @return the immutable trigger set; never {@code null}
     */
    public Set<Status.Kind> retryOn() {
        return retryOn;
    }

    /**
     * Returns whether the supplied status kind triggers another attempt.
     *
     * @param kind the finished attempt's kind; must not be {@code null}
     * @return {@code true} when the kind triggers a retry
     * @throws NullPointerException if {@code kind} is {@code null}
     */
    public boolean retries(final Status.Kind kind) {
        return retryOn.contains(Objects.requireNonNull(kind, "kind is null"));
    }

    /**
     * Returns the delay schedule.
     *
     * @return the schedule; never {@code null}
     */
    public Backoff backoff() {
        return backoff;
    }

    /**
     * Returns the delay before the supplied 1-based attempt.
     *
     * @param nextAttempt the attempt number about to start; at least two, because the first attempt never waits
     * @return the delay; never {@code null}
     * @throws IllegalArgumentException if {@code nextAttempt} is less than two
     */
    public Duration delayBefore(final int nextAttempt) {
        if (nextAttempt < 2) {
            throw new IllegalArgumentException("nextAttempt must be at least 2: " + nextAttempt);
        }
        return backoff.delayFor(nextAttempt - 1);
    }

    @Override
    public String toString() {
        return "RetryPolicy[maxAttempts=" + maxAttempts + ", retryOn=" + retryOn + ", backoff=" + backoff + "]";
    }

    /**
     * Builds {@link RetryPolicy} instances.
     */
    public static final class Builder {

        private int maxAttempts = 1;
        private final EnumSet<Status.Kind> retryOn = EnumSet.of(Status.Kind.FAILED);
        private Backoff backoff = Backoff.fixed(Duration.ZERO);

        private Builder() {
            // Intentionally empty
        }

        /**
         * Sets the total attempt limit.
         *
         * @param maxAttempts the limit including the first attempt; must be between 1 and {@value #MAX_ATTEMPTS}
         * @return this builder
         * @throws IllegalArgumentException if the limit is outside the accepted range
         */
        public Builder maxAttempts(final int maxAttempts) {
            if (maxAttempts < 1 || maxAttempts > MAX_ATTEMPTS) {
                throw new IllegalArgumentException(
                        "maxAttempts must be between 1 and " + MAX_ATTEMPTS + ": " + maxAttempts);
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Replaces the trigger set.
         *
         * @param kinds the status kinds that trigger a retry; must not be {@code null} or empty, and each kind must be
         *     one of {@link #RETRYABLE_KINDS}
         * @return this builder
         * @throws NullPointerException if {@code kinds} or an element is {@code null}
         * @throws IllegalArgumentException if no kind is supplied or a kind is not in {@link #RETRYABLE_KINDS}
         */
        public Builder retryOn(final Status.Kind... kinds) {
            Objects.requireNonNull(kinds, "kinds is null");
            if (kinds.length == 0) {
                throw new IllegalArgumentException("retryOn requires at least one status kind");
            }
            var replacement = EnumSet.noneOf(Status.Kind.class);
            for (Status.Kind kind : kinds) {
                requireRetryable(kind);
                replacement.add(kind);
            }
            retryOn.clear();
            retryOn.addAll(replacement);
            return this;
        }

        /**
         * Sets the delay schedule.
         *
         * @param backoff the schedule; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code backoff} is {@code null}
         */
        public Builder backoff(final Backoff backoff) {
            this.backoff = Objects.requireNonNull(backoff, "backoff is null");
            return this;
        }

        /**
         * Builds the policy.
         *
         * @return the immutable policy; never {@code null}
         */
        public RetryPolicy build() {
            return new RetryPolicy(maxAttempts, retryOn, backoff);
        }

        private static void requireRetryable(final Status.Kind kind) {
            Objects.requireNonNull(kind, "kind is null");
            if (!RETRYABLE_KINDS.contains(kind)) {
                throw new IllegalArgumentException(
                        "A retry policy may only retry " + RETRYABLE_KINDS + ", not " + kind);
            }
        }
    }
}

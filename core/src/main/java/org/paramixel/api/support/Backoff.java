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
import nonapi.org.paramixel.support.Arguments;

/**
 * How long to wait between attempts.
 *
 * <p>One immutable type serves every retry and loop delay:
 *
 * <ul>
 *   <li>{@link #fixed(Duration)} — the same wait every time;</li>
 *   <li>{@link #linear(Duration)} — the wait grows by a constant step
 *       ({@code step}, {@code 2*step}, {@code 3*step}, …);</li>
 *   <li>{@link #exponential(Duration, Duration)} — the wait doubles ({@code base}, {@code 2*base}, {@code 4*base}, …)
 *       and saturates at the supplied maximum.</li>
 * </ul>
 *
 * <p>Arithmetic saturates: a delay never exceeds its maximum (or {@link Duration#ofSeconds(long) Duration.ofSeconds(Long.MAX_VALUE)} when no
 * maximum is configured) and never throws, so a long loop or a high retry count cannot overflow.
 *
 * @see RetryPolicy.Builder#backoff(Backoff)
 * @see RepeatPolicy.Builder#delay(Backoff)
 */
public final class Backoff {

    private static final Duration NO_MAXIMUM = Duration.ofSeconds(Long.MAX_VALUE);

    /**
     * The kind of schedule.
     */
    public enum Kind {

        /** A constant wait. */
        FIXED,

        /** A wait that grows by a constant step. */
        LINEAR,

        /** A wait that doubles up to a maximum. */
        EXPONENTIAL
    }

    private final Kind kind;
    private final Duration base;
    private final Duration maximum;

    private Backoff(final Kind kind, final Duration base, final Duration maximum) {
        this.kind = Objects.requireNonNull(kind, "kind is null");
        this.base = Objects.requireNonNull(base, "base is null");
        this.maximum = Objects.requireNonNull(maximum, "maximum is null");
    }

    /**
     * Returns a schedule that waits the same duration every time.
     *
     * @param delay the wait duration; must not be {@code null} or negative
     * @return the fixed schedule; never {@code null}
     * @throws NullPointerException if {@code delay} is {@code null}
     * @throws IllegalArgumentException if {@code delay} is negative
     */
    public static Backoff fixed(final Duration delay) {
        Objects.requireNonNull(delay, "delay is null");
        Arguments.requireFalse(delay.isNegative(), "delay is negative");
        return new Backoff(Kind.FIXED, delay, NO_MAXIMUM);
    }

    /**
     * Returns a schedule whose wait grows by a constant step.
     *
     * <p>Attempt {@code n} (1-based) waits {@code step * n}.
     *
     * @param step the step added for each attempt; must not be {@code null} or negative
     * @return the linear schedule; never {@code null}
     * @throws NullPointerException if {@code step} is {@code null}
     * @throws IllegalArgumentException if {@code step} is negative
     */
    public static Backoff linear(final Duration step) {
        Objects.requireNonNull(step, "step is null");
        Arguments.requireFalse(step.isNegative(), "step is negative");
        return new Backoff(Kind.LINEAR, step, NO_MAXIMUM);
    }

    /**
     * Returns a schedule whose wait doubles, saturating at the supplied maximum.
     *
     * <p>Attempt {@code n} (1-based) waits {@code base * 2^(n-1)}, capped at {@code max}. The cap means a long-running
     * loop or a high retry count is safe: at attempt 64 the wait is {@code max}, not a duration that overflows.
     *
     * @param base the wait before the second attempt; must not be {@code null} or negative
     * @param max the maximum wait; must not be {@code null} or negative, and must not be shorter than {@code base}
     * @return the exponential schedule; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if a duration is negative or {@code max} is shorter than {@code base}
     */
    public static Backoff exponential(final Duration base, final Duration max) {
        Objects.requireNonNull(base, "base is null");
        Objects.requireNonNull(max, "max is null");
        Arguments.requireFalse(base.isNegative(), "base is negative");
        Arguments.requireFalse(max.isNegative(), "max is negative");
        Arguments.requireTrue(base.compareTo(max) <= 0, "max is shorter than base");
        return new Backoff(Kind.EXPONENTIAL, base, max);
    }

    /**
     * Returns the kind of this schedule.
     *
     * @return the kind; never {@code null}
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the base duration of this schedule.
     *
     * @return the base (or step, or fixed) duration; never {@code null}
     */
    public Duration base() {
        return base;
    }

    /**
     * Returns the maximum wait of this schedule.
     *
     * @return the maximum, or {@link Duration#ofSeconds(long) Duration.ofSeconds(Long.MAX_VALUE)} when the
     *     schedule has no cap
     */
    public Duration maximum() {
        return maximum;
    }

    /**
     * Returns the wait before the supplied attempt.
     *
     * @param attempt the 1-based attempt number; a value below one returns {@link Duration#ZERO}
     * @return the wait; never {@code null} and never negative
     */
    public Duration delayFor(final int attempt) {
        if (attempt < 1) {
            return Duration.ZERO;
        }
        return switch (kind) {
            case FIXED -> base;
            case LINEAR -> capped(multipliedSaturating(base, attempt), maximum);
            case EXPONENTIAL -> capped(exponentialDelay(attempt), maximum);
        };
    }

    private Duration exponentialDelay(final int attempt) {
        var doublings = attempt - 1;
        var result = base;
        for (var i = 0; i < doublings; i++) {
            if (result.compareTo(maximum) >= 0) {
                return maximum;
            }
            result = multipliedSaturating(result, 2L);
        }
        return result;
    }

    /**
     * Multiplies with saturating arithmetic: an overflow yields the largest representable duration instead of
     * throwing.
     */
    private static Duration multipliedSaturating(final Duration duration, final long multiplier) {
        try {
            return duration.multipliedBy(multiplier);
        } catch (ArithmeticException e) {
            return NO_MAXIMUM;
        }
    }

    /**
     * Multiplies with saturating arithmetic: an overflow yields the maximum instead of throwing.
     */
    private static Duration capped(final Duration duration, final Duration maximum) {
        return duration.compareTo(maximum) >= 0 ? maximum : duration;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case FIXED -> "fixed(" + base + ")";
            case LINEAR -> "linear(" + base + ")";
            case EXPONENTIAL -> "exponential(" + base + ", " + maximum + ")";
        };
    }
}

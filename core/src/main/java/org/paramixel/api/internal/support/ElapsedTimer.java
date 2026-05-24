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

package org.paramixel.api.internal.support;

import java.time.Duration;

/**
 * Measures elapsed time using monotonic {@code System.nanoTime()}.
 *
 * <p>Monotonic time is independent of wall-clock time and is not affected by system clock changes such as NTP
 * adjustments, DST transitions, or manual clock changes. This ensures that duration measurements are always accurate
 * and never negative due to external clock adjustments.
 *
 * <p>Use this class for all action duration measurements. Reserve wall-clock time
 * ({@code LocalDateTime.now()} or {@code Instant.now()}) only for human-readable timestamps such as report headers.
 */
public final class ElapsedTimer {

    private final long startNanos;

    private ElapsedTimer(final long startNanos) {
        this.startNanos = startNanos;
    }

    /**
     * Starts a new timer using the current monotonic time.
     *
     * @return a new timer started at the current {@code System.nanoTime()} value
     */
    public static ElapsedTimer start() {
        return new ElapsedTimer(System.nanoTime());
    }

    /**
     * The duration elapsed since this timer was started.
     *
     * <p>The returned duration is based on monotonic time and is never negative. If the monotonic clock value has
     * decreased (which should not occur on a single JVM instance), {@code Duration.ZERO} is returned.
     *
     * @return the elapsed duration since {@link #start()} was called
     */
    public Duration elapsed() {
        return elapsedBetween(startNanos, System.nanoTime());
    }

    /**
     * Computes the duration between two monotonic nano-time values.
     *
     * <p>If {@code endNanos} is less than {@code startNanos} (which should not occur for monotonic time on a single
     * JVM instance), {@code Duration.ZERO} is returned as a safe fallback.
     *
     * @param startNanos the starting monotonic nano-time value
     * @param endNanos the ending monotonic nano-time value
     * @return the elapsed duration, or {@code Duration.ZERO} if the result would be negative
     */
    static Duration elapsedBetween(final long startNanos, final long endNanos) {
        final var elapsedNanos = endNanos - startNanos;
        if (elapsedNanos < 0) {
            return Duration.ZERO;
        }
        return Duration.ofNanos(elapsedNanos);
    }
}

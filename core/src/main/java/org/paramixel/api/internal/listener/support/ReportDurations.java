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

package org.paramixel.api.internal.listener.support;

import java.time.Duration;
import java.util.Objects;

/**
 * Converts {@link Duration} values to non-negative millisecond counts for report output.
 *
 * <p>Negative durations (which should not occur with monotonic timing) are clamped to zero.
 */
public final class ReportDurations {
    /**
     * Unit label used in report fields that express durations in milliseconds.
     */
    public static final String UNIT_MILLISECONDS = "MILLISECONDS";

    private ReportDurations() {
        // Intentionally empty
    }

    /**
     * Converts a duration to a non-negative millisecond count, clamping negative values to zero.
     *
     * @param duration the duration to convert
     * @return the duration in milliseconds, or {@code 0} when the result would be negative
     * @throws NullPointerException if {@code duration} is {@code null}
     */
    public static long toNonNegativeMillis(final Duration duration) {
        long millis =
                Objects.requireNonNull(duration, "duration must not be null").toMillis();
        return millis < 0 ? 0 : millis;
    }
}

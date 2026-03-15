/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine.util;

/**
 * Duration-related utilities.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class DurationUtils {

    /**
     * Prevents instantiation of this utility class.
     *
     * <p>This class exposes only static methods.
     */
    private DurationUtils() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Formats a duration in milliseconds for human-readable output.
     *
     * @param millis duration in milliseconds; must be {@code >= 0}
     * @return formatted duration string; never {@code null}
     * @throws IllegalArgumentException if {@code millis < 0}
     */
    public static String formatMillis(final long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("millis must be >= 0, got: " + millis);
        }
        return DurationUnit.forMillis(millis).format(millis);
    }

    /**
     * Duration units with formatting logic.
     */
    private enum DurationUnit {
        MILLISECONDS(1.0, "ms", 1000),
        SECONDS(1000.0, "s", 60000),
        MINUTES(60000.0, "m", 3600000),
        HOURS(3600000.0, "h", 86400000),
        DAYS(86400000.0, "d", Long.MAX_VALUE);

        private final double divisor;
        private final String suffix;
        private final long threshold;

        DurationUnit(final double divisor, final String suffix, final long threshold) {
            this.divisor = divisor;
            this.suffix = suffix;
            this.threshold = threshold;
        }

        public String format(final long millis) {
            return String.format("%.3f %s", millis / divisor, suffix);
        }

        public static DurationUnit forMillis(final long millis) {
            for (DurationUnit unit : values()) {
                if (millis < unit.threshold) {
                    return unit;
                }
            }
            return DAYS;
        }
    }
}

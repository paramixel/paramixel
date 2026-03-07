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
 * @since 0.0.1
 */
public final class DurationUtils {

    /**
     * Prevents instantiation of this utility class.
     *
     * <p>This class exposes only static methods.
     *
     * @since 0.0.1
     */
    private DurationUtils() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Formats a duration in milliseconds for human-readable output.
     *
     * <p>Rules:
     * <ul>
     *   <li>{@code < 1000}: milliseconds with 3 decimals (e.g. {@code "250.000 ms"})
     *   <li>{@code < 60000}: seconds with 3 decimals (e.g. {@code "1.500 s"})
     *   <li>{@code < 3600000}: minutes with 3 decimals (e.g. {@code "1.083 m"})
     *   <li>{@code < 86400000}: hours with 3 decimals (e.g. {@code "1.000 h"})
     *   <li>{@code >= 86400000}: days with 3 decimals (e.g. {@code "1.000 d"})
     * </ul>
     *
     * @param millis duration in milliseconds; must be {@code >= 0}
     * @return formatted duration string; never {@code null}
     * @throws IllegalArgumentException if {@code millis < 0}
     * @since 0.0.1
     */
    public static String formatMillis(final long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("millis must be >= 0, got: " + millis);
        }

        if (millis < 1000) {
            return String.format("%.3f ms", millis / 1.0);
        }

        if (millis < 60000) {
            return String.format("%.3f s", millis / 1000.0);
        }

        if (millis < 3600000) {
            return String.format("%.3f m", millis / 60000.0);
        }

        if (millis < 86400000) {
            return String.format("%.3f h", millis / 3600000.0);
        }

        return String.format("%.3f d", millis / 86400000.0);
    }
}

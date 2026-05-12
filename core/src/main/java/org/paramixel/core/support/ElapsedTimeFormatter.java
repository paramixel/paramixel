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

package org.paramixel.core.support;

/**
 * Formats elapsed time durations for display in console and report output.
 */
public final class ElapsedTimeFormatter {

    private ElapsedTimeFormatter() {
        // Intentionally empty
    }

    /**
     * Formats a duration given in milliseconds into a human-readable string.
     *
     * <p>When the duration is less than one second, the result is just the raw millisecond value
     * (e.g. {@code "123ms"}). For longer durations, a compact form is used (e.g. {@code "1m 30s (90000ms)"}).
     *
     * @param milliseconds the elapsed time in milliseconds
     * @return the formatted elapsed-time string
     */
    public static String formatElapsedTime(long milliseconds) {
        String formatted = formatDuration(milliseconds);
        String rawMilliseconds = milliseconds + "ms";

        if (formatted.equals(rawMilliseconds)) {
            return formatted;
        }

        return formatted + " (" + rawMilliseconds + ")";
    }

    /**
     * Formats a duration given in milliseconds into a compact human-readable form.
     *
     * <p>Non-zero larger units are included, e.g. {@code "1h 2m 3s 400ms"} or {@code "5s 0ms"}.
     *
     * @param milliseconds the duration in milliseconds
     * @return the formatted duration string
     */
    public static String formatDuration(long milliseconds) {
        long hours = milliseconds / 3_600_000;
        milliseconds %= 3_600_000;

        long minutes = milliseconds / 60_000;
        milliseconds %= 60_000;

        long seconds = milliseconds / 1_000;
        milliseconds %= 1_000;

        StringBuilder result = new StringBuilder();

        if (hours > 0) {
            result.append(hours).append("h ");
        }

        if (minutes > 0 || hours > 0) {
            result.append(minutes).append("m ");
        }

        if (seconds > 0 || minutes > 0 || hours > 0) {
            result.append(seconds).append("s ");
        }

        result.append(milliseconds).append("ms");

        return result.toString().trim();
    }
}

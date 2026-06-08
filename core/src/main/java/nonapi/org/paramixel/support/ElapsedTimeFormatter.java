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

package nonapi.org.paramixel.support;

/**
 * Formats elapsed time durations for display in console and report output.
 *
 * <p>Two formatting modes are provided: {@link #formatElapsedTime(long)} includes a compact
 * form alongside the raw millisecond value, while {@link #formatDuration(long)} produces
 * only the compact form. This class cannot be instantiated.
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
     * @throws IllegalArgumentException if {@code milliseconds} is negative
     */
    public static String formatElapsedTime(final long milliseconds) {
        Arguments.requireFalse(milliseconds < 0, "milliseconds is negative");
        String formatted = formatDuration(milliseconds);
        String rawMilliseconds = milliseconds + " ms";

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
     * @throws IllegalArgumentException if {@code milliseconds} is negative
     */
    public static String formatDuration(long milliseconds) {
        Arguments.requireFalse(milliseconds < 0, "milliseconds is negative");

        StringBuilder result = new StringBuilder();
        boolean hasPrevious = false;

        long hours = milliseconds / 3_600_000;
        if (hours > 0) {
            result.append(hours).append(" h");
            hasPrevious = true;
        }
        milliseconds %= 3_600_000;

        long minutes = milliseconds / 60_000;
        if (minutes > 0 || hours > 0) {
            if (hasPrevious) {
                result.append(' ');
            }
            result.append(minutes).append(" m");
            hasPrevious = true;
        }
        milliseconds %= 60_000;

        long seconds = milliseconds / 1_000;
        if (seconds > 0 || minutes > 0 || hours > 0) {
            if (hasPrevious) {
                result.append(' ');
            }
            result.append(seconds).append(" s");
            hasPrevious = true;
        }

        if (hasPrevious) {
            result.append(' ');
        }
        result.append(milliseconds % 1_000).append(" ms");

        return result.toString();
    }
}

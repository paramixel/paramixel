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

package org.paramixel.maven.plugin.internal.util;

import java.util.List;
import java.util.Objects;
import org.paramixel.core.support.Arguments;

public class DurationUtil {

    public record FormattedDuration(String numberPart, String unit) {

        public FormattedDuration {
            Objects.requireNonNull(numberPart, "numberPart must not be null");
            Arguments.requireNonBlank(numberPart, "numberPart must not be blank");
            Objects.requireNonNull(unit, "unit must not be null");
        }
    }

    private DurationUtil() {
        // Intentionally empty
    }

    public static String formatMillis(final long millis) {
        Arguments.requireNonNegative(millis, "millis must be non-negative");
        return DurationUnit.forMillis(millis).format(millis);
    }

    public static FormattedDuration parseFormatted(final String formatted) {
        Objects.requireNonNull(formatted, "formatted must not be null");
        Arguments.requireNonBlank(formatted, "formatted must not be blank");
        final var spaceIndex = formatted.lastIndexOf(' ');
        if (spaceIndex < 0) {
            return new FormattedDuration(formatted, "");
        }
        final var numberPart = formatted.substring(0, spaceIndex);
        final var unit = formatted.substring(spaceIndex + 1);
        return new FormattedDuration(numberPart, unit);
    }

    public static String padForAlignment(final String formatted, final int maxIntegerWidth) {
        Objects.requireNonNull(formatted, "formatted must not be null");
        Arguments.requireNonBlank(formatted, "formatted must not be blank");
        Arguments.requirePositive(maxIntegerWidth, "maxIntegerWidth must be positive");
        final var parsed = parseFormatted(formatted);

        final var numberPart = parsed.numberPart();
        final var decimalIndex = numberPart.indexOf('.');

        String integerPart;
        String decimalPart;
        if (decimalIndex >= 0) {
            integerPart = numberPart.substring(0, decimalIndex);
            decimalPart = numberPart.substring(decimalIndex);
        } else {
            integerPart = numberPart;
            decimalPart = "";
        }

        final var leftPadding = maxIntegerWidth - integerPart.length();
        final var paddedNumber = " ".repeat(leftPadding) + integerPart + decimalPart;

        final var unit = parsed.unit();
        if (unit.isEmpty()) {
            return paddedNumber;
        }
        return paddedNumber + " " + unit;
    }

    public static int calculateMaxIntegerWidth(final List<String> formattedDurations) {
        Objects.requireNonNull(formattedDurations, "formattedDurations must not be null");
        var maxIntegerWidth = 0;
        for (final var formatted : formattedDurations) {
            final var parsed = parseFormatted(formatted);
            final var numberPart = parsed.numberPart();
            final var decimalIndex = numberPart.indexOf('.');
            final var integerPart = decimalIndex >= 0 ? numberPart.substring(0, decimalIndex) : numberPart;
            if (integerPart.length() > maxIntegerWidth) {
                maxIntegerWidth = integerPart.length();
            }
        }
        return maxIntegerWidth;
    }

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
            for (var unit : values()) {
                if (millis < unit.threshold) {
                    return unit;
                }
            }
            return DAYS;
        }
    }
}

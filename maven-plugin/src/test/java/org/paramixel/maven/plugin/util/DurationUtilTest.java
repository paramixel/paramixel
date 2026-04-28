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

package org.paramixel.maven.plugin.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.maven.plugin.internal.util.DurationUtil;

@DisplayName("DurationUtil tests")
class DurationUtilTest {

    @Nested
    @DisplayName("formatMillis() argument validation")
    class FormatMillisValidationTests {

        @Test
        @DisplayName("should throw IAE when millis is negative")
        void shouldThrowIaeWhenMillisIsNegative() {
            assertThatThrownBy(() -> DurationUtil.formatMillis(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("millis");
        }
    }

    @Nested
    @DisplayName("formatMillis() valid inputs")
    class FormatMillisValidInputTests {

        @Test
        @DisplayName("should format zero millis")
        void shouldFormatZeroMillis() {
            assertThat(DurationUtil.formatMillis(0)).isEqualTo("0.000 ms");
        }

        @Test
        @DisplayName("should format 1 millisecond")
        void shouldFormatOneMillisecond() {
            assertThat(DurationUtil.formatMillis(1)).isEqualTo("1.000 ms");
        }

        @Test
        @DisplayName("should format 999 milliseconds")
        void shouldFormat999Millis() {
            assertThat(DurationUtil.formatMillis(999)).isEqualTo("999.000 ms");
        }

        @Test
        @DisplayName("should format 1000 milliseconds as 1 second")
        void shouldFormat1000MillisAsOneSecond() {
            assertThat(DurationUtil.formatMillis(1000)).isEqualTo("1.000 s");
        }

        @Test
        @DisplayName("should format 60000 milliseconds as 1 minute")
        void shouldFormat60000MillisAsOneMinute() {
            assertThat(DurationUtil.formatMillis(60000)).isEqualTo("1.000 m");
        }

        @Test
        @DisplayName("should format 3600000 milliseconds as 1 hour")
        void shouldFormat3600000MillisAsOneHour() {
            assertThat(DurationUtil.formatMillis(3600000)).isEqualTo("1.000 h");
        }

        @Test
        @DisplayName("should format 86400000 milliseconds as 1 day")
        void shouldFormat86400000MillisAsOneDay() {
            assertThat(DurationUtil.formatMillis(86400000)).isEqualTo("1.000 d");
        }

        @Test
        @DisplayName("should format 500 milliseconds under one second")
        void shouldFormatMillisUnderOneSecond() {
            assertThat(DurationUtil.formatMillis(500)).isEqualTo("500.000 ms");
        }

        @Test
        @DisplayName("should format 1500 milliseconds as 1.5 seconds")
        void shouldFormat1500MillisAs1Point5Seconds() {
            assertThat(DurationUtil.formatMillis(1500)).isEqualTo("1.500 s");
        }
    }

    @Nested
    @DisplayName("parseFormatted() argument validation")
    class ParseFormattedValidationTests {

        @Test
        @DisplayName("should throw NPE when formatted is null")
        void shouldThrowNpeWhenFormattedIsNull() {
            assertThatThrownBy(() -> DurationUtil.parseFormatted(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("formatted");
        }

        @Test
        @DisplayName("should throw IAE when formatted is blank")
        void shouldThrowIaeWhenFormattedIsBlank() {
            assertThatThrownBy(() -> DurationUtil.parseFormatted("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("formatted");
        }
    }

    @Nested
    @DisplayName("parseFormatted() valid inputs")
    class ParseFormattedValidInputTests {

        @Test
        @DisplayName("should parse milliseconds format")
        void shouldParseMillisecondsFormat() {
            var result = DurationUtil.parseFormatted("500.000 ms");
            assertThat(result.numberPart()).isEqualTo("500.000");
            assertThat(result.unit()).isEqualTo("ms");
        }

        @Test
        @DisplayName("should parse seconds format")
        void shouldParseSecondsFormat() {
            var result = DurationUtil.parseFormatted("1.500 s");
            assertThat(result.numberPart()).isEqualTo("1.500");
            assertThat(result.unit()).isEqualTo("s");
        }

        @Test
        @DisplayName("should parse value without unit")
        void shouldParseValueWithoutUnit() {
            var result = DurationUtil.parseFormatted("42.000");
            assertThat(result.numberPart()).isEqualTo("42.000");
            assertThat(result.unit()).isEmpty();
        }
    }

    @Nested
    @DisplayName("padForAlignment() argument validation")
    class PadForAlignmentValidationTests {

        @Test
        @DisplayName("should throw NPE when formatted is null")
        void shouldThrowNpeWhenFormattedIsNull() {
            assertThatThrownBy(() -> DurationUtil.padForAlignment(null, 5))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("formatted");
        }

        @Test
        @DisplayName("should throw IAE when formatted is blank")
        void shouldThrowIaeWhenFormattedIsBlank() {
            assertThatThrownBy(() -> DurationUtil.padForAlignment("   ", 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("formatted");
        }

        @Test
        @DisplayName("should throw IAE when maxIntegerWidth is zero")
        void shouldThrowIaeWhenMaxIntegerWidthIsZero() {
            assertThatThrownBy(() -> DurationUtil.padForAlignment("1.234 ms", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxIntegerWidth");
        }

        @Test
        @DisplayName("should throw IAE when maxIntegerWidth is negative")
        void shouldThrowIaeWhenMaxIntegerWidthIsNegative() {
            assertThatThrownBy(() -> DurationUtil.padForAlignment("1.234 ms", -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxIntegerWidth");
        }
    }

    @Nested
    @DisplayName("padForAlignment() valid inputs")
    class PadForAlignmentValidInputTests {

        @Test
        @DisplayName("should pad integer part for alignment")
        void shouldPadIntegerPartForAlignment() {
            var result = DurationUtil.padForAlignment("1.234 ms", 3);
            assertThat(result).isEqualTo("  1.234 ms");
        }

        @Test
        @DisplayName("should not pad when integer already at max width")
        void shouldNotPadWhenIntegerAlreadyAtMaxWidth() {
            var result = DurationUtil.padForAlignment("100.234 ms", 3);
            assertThat(result).isEqualTo("100.234 ms");
        }

        @Test
        @DisplayName("should pad value without unit")
        void shouldPadValueWithoutUnit() {
            var result = DurationUtil.padForAlignment("5.000", 3);
            assertThat(result).isEqualTo("  5.000");
        }

        @Test
        @DisplayName("should pad value without decimal")
        void shouldPadValueWithoutDecimal() {
            var result = DurationUtil.padForAlignment("500 ms", 4);
            assertThat(result).isEqualTo(" 500 ms");
        }
    }

    @Nested
    @DisplayName("calculateMaxIntegerWidth() argument validation")
    class CalculateMaxIntegerWidthValidationTests {

        @Test
        @DisplayName("should throw NPE when formattedDurations is null")
        void shouldThrowNpeWhenFormattedDurationsIsNull() {
            assertThatThrownBy(() -> DurationUtil.calculateMaxIntegerWidth(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("formattedDurations");
        }
    }

    @Nested
    @DisplayName("calculateMaxIntegerWidth() valid inputs")
    class CalculateMaxIntegerWidthValidInputTests {

        @Test
        @DisplayName("should return 0 for empty list")
        void shouldReturnZeroForEmptyList() {
            assertThat(DurationUtil.calculateMaxIntegerWidth(List.of())).isEqualTo(0);
        }

        @Test
        @DisplayName("should return max integer width for multiple durations")
        void shouldReturnMaxIntegerWidthForMultipleDurations() {
            var durations = List.of("1.234 ms", "100.567 ms", "10.890 ms");
            assertThat(DurationUtil.calculateMaxIntegerWidth(durations)).isEqualTo(3);
        }

        @Test
        @DisplayName("should handle single duration")
        void shouldHandleSingleDuration() {
            var durations = List.of("500.000 ms");
            assertThat(DurationUtil.calculateMaxIntegerWidth(durations)).isEqualTo(3);
        }
    }
}

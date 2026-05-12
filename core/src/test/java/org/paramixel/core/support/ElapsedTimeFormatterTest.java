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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ElapsedTimeFormatter")
class ElapsedTimeFormatterTest {

    @Nested
    @DisplayName("formatDuration")
    class FormatDuration {

        @Test
        @DisplayName("formats zero milliseconds")
        void formatsZeroMilliseconds() {
            assertThat(ElapsedTimeFormatter.formatDuration(0)).isEqualTo("0ms");
        }

        @Test
        @DisplayName("formats sub-second milliseconds")
        void formatsSubSecondMilliseconds() {
            assertThat(ElapsedTimeFormatter.formatDuration(123)).isEqualTo("123ms");
        }

        @Test
        @DisplayName("formats exactly one second")
        void formatsExactlyOneSecond() {
            assertThat(ElapsedTimeFormatter.formatDuration(1000)).isEqualTo("1s 0ms");
        }

        @Test
        @DisplayName("formats seconds and milliseconds")
        void formatsSecondsAndMilliseconds() {
            assertThat(ElapsedTimeFormatter.formatDuration(1500)).isEqualTo("1s 500ms");
        }

        @Test
        @DisplayName("formats exactly one minute")
        void formatsExactlyOneMinute() {
            assertThat(ElapsedTimeFormatter.formatDuration(60000)).isEqualTo("1m 0s 0ms");
        }

        @Test
        @DisplayName("formats minutes, seconds, and milliseconds")
        void formatsMinutesSecondsAndMilliseconds() {
            assertThat(ElapsedTimeFormatter.formatDuration(90000)).isEqualTo("1m 30s 0ms");
        }

        @Test
        @DisplayName("formats exactly one hour")
        void formatsExactlyOneHour() {
            assertThat(ElapsedTimeFormatter.formatDuration(3600000)).isEqualTo("1h 0m 0s 0ms");
        }

        @Test
        @DisplayName("formats hours, minutes, seconds, and milliseconds")
        void formatsHoursMinutesSecondsAndMilliseconds() {
            assertThat(ElapsedTimeFormatter.formatDuration(3723400)).isEqualTo("1h 2m 3s 400ms");
        }

        @Test
        @DisplayName("formats all non-zero units")
        void formatsAllNonZeroUnits() {
            assertThat(ElapsedTimeFormatter.formatDuration(3661500)).isEqualTo("1h 1m 1s 500ms");
        }
    }

    @Nested
    @DisplayName("formatElapsedTime")
    class FormatElapsedTime {

        @Test
        @DisplayName("returns raw milliseconds for sub-second duration")
        void returnsRawMillisecondsForSubSecondDuration() {
            assertThat(ElapsedTimeFormatter.formatElapsedTime(123)).isEqualTo("123ms");
        }

        @Test
        @DisplayName("returns raw milliseconds for zero")
        void returnsRawMillisecondsForZero() {
            assertThat(ElapsedTimeFormatter.formatElapsedTime(0)).isEqualTo("0ms");
        }

        @Test
        @DisplayName("appends raw milliseconds in parentheses for second duration")
        void appendsRawMillisecondsForSecondDuration() {
            assertThat(ElapsedTimeFormatter.formatElapsedTime(1000)).isEqualTo("1s 0ms (1000ms)");
        }

        @Test
        @DisplayName("appends raw milliseconds in parentheses for minute duration")
        void appendsRawMillisecondsForMinuteDuration() {
            assertThat(ElapsedTimeFormatter.formatElapsedTime(90000)).isEqualTo("1m 30s 0ms (90000ms)");
        }

        @Test
        @DisplayName("appends raw milliseconds in parentheses for hour duration")
        void appendsRawMillisecondsForHourDuration() {
            assertThat(ElapsedTimeFormatter.formatElapsedTime(3600000)).isEqualTo("1h 0m 0s 0ms (3600000ms)");
        }
    }
}

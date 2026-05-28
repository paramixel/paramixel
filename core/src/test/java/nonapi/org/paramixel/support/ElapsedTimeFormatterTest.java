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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ElapsedTimeFormatter")
class ElapsedTimeFormatterTest {

    @Test
    @DisplayName("formats zero milliseconds")
    void formatsZeroMilliseconds() {
        assertThat(ElapsedTimeFormatter.formatDuration(0)).isEqualTo("0 ms");
    }

    @Test
    @DisplayName("formats sub-second milliseconds")
    void formatsSubSecondMilliseconds() {
        assertThat(ElapsedTimeFormatter.formatDuration(123)).isEqualTo("123 ms");
    }

    @Test
    @DisplayName("formats exactly one second")
    void formatsExactlyOneSecond() {
        assertThat(ElapsedTimeFormatter.formatDuration(1000)).isEqualTo("1 s 0 ms");
    }

    @Test
    @DisplayName("formats seconds and milliseconds")
    void formatsSecondsAndMilliseconds() {
        assertThat(ElapsedTimeFormatter.formatDuration(1500)).isEqualTo("1 s 500 ms");
    }

    @Test
    @DisplayName("formats exactly one minute")
    void formatsExactlyOneMinute() {
        assertThat(ElapsedTimeFormatter.formatDuration(60000)).isEqualTo("1 m 0 s 0 ms");
    }

    @Test
    @DisplayName("formats minutes, seconds, and milliseconds")
    void formatsMinutesSecondsAndMilliseconds() {
        assertThat(ElapsedTimeFormatter.formatDuration(90000)).isEqualTo("1 m 30 s 0 ms");
    }

    @Test
    @DisplayName("formats exactly one hour")
    void formatsExactlyOneHour() {
        assertThat(ElapsedTimeFormatter.formatDuration(3600000)).isEqualTo("1 h 0 m 0 s 0 ms");
    }

    @Test
    @DisplayName("formats hours, minutes, seconds, and milliseconds")
    void formatsHoursMinutesSecondsAndMilliseconds() {
        assertThat(ElapsedTimeFormatter.formatDuration(3723400)).isEqualTo("1 h 2 m 3 s 400 ms");
    }

    @Test
    @DisplayName("formats all non-zero units")
    void formatsAllNonZeroUnits() {
        assertThat(ElapsedTimeFormatter.formatDuration(3661500)).isEqualTo("1 h 1 m 1 s 500 ms");
    }

    @Test
    @DisplayName("rejects negative duration")
    void rejectsNegativeDuration() {
        assertThatThrownBy(() -> ElapsedTimeFormatter.formatDuration(-90000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("milliseconds is negative");
    }

    @Test
    @DisplayName("returns raw milliseconds for sub-second duration")
    void returnsRawMillisecondsForSubSecondDuration() {
        assertThat(ElapsedTimeFormatter.formatElapsedTime(123)).isEqualTo("123 ms");
    }

    @Test
    @DisplayName("returns raw milliseconds for zero")
    void returnsRawMillisecondsForZero() {
        assertThat(ElapsedTimeFormatter.formatElapsedTime(0)).isEqualTo("0 ms");
    }

    @Test
    @DisplayName("appends raw milliseconds in parentheses for second duration")
    void appendsRawMillisecondsForSecondDuration() {
        assertThat(ElapsedTimeFormatter.formatElapsedTime(1000)).isEqualTo("1 s 0 ms (1000 ms)");
    }

    @Test
    @DisplayName("appends raw milliseconds in parentheses for minute duration")
    void appendsRawMillisecondsForMinuteDuration() {
        assertThat(ElapsedTimeFormatter.formatElapsedTime(90000)).isEqualTo("1 m 30 s 0 ms (90000 ms)");
    }

    @Test
    @DisplayName("appends raw milliseconds in parentheses for hour duration")
    void appendsRawMillisecondsForHourDuration() {
        assertThat(ElapsedTimeFormatter.formatElapsedTime(3600000)).isEqualTo("1 h 0 m 0 s 0 ms (3600000 ms)");
    }

    @Test
    @DisplayName("rejects negative elapsed time")
    void rejectsNegativeElapsedTime() {
        assertThatThrownBy(() -> ElapsedTimeFormatter.formatElapsedTime(-90000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("milliseconds is negative");
    }
}

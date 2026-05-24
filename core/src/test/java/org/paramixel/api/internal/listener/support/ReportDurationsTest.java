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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReportDurations")
class ReportDurationsTest {

    @Test
    @DisplayName("toNonNegativeMillis converts positive duration")
    void toNonNegativeMillisConvertsPositiveDuration() {
        assertThat(ReportDurations.toNonNegativeMillis(Duration.ofMillis(123))).isEqualTo(123);
    }

    @Test
    @DisplayName("toNonNegativeMillis converts zero duration")
    void toNonNegativeMillisConvertsZeroDuration() {
        assertThat(ReportDurations.toNonNegativeMillis(Duration.ZERO)).isEqualTo(0);
    }

    @Test
    @DisplayName("toNonNegativeMillis clamps negative millis to zero")
    void toNonNegativeMillisClampsNegativeMillisToZero() {
        assertThat(ReportDurations.toNonNegativeMillis(Duration.ofMillis(-1))).isEqualTo(0);
    }

    @Test
    @DisplayName("toNonNegativeMillis clamps negative nanos to zero")
    void toNonNegativeMillisClampsNegativeNanosToZero() {
        assertThat(ReportDurations.toNonNegativeMillis(Duration.ofNanos(-1))).isEqualTo(0);
    }
}

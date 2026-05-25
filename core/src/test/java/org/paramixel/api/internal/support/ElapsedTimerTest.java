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

package org.paramixel.api.internal.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ElapsedTimer")
class ElapsedTimerTest {

    @Test
    @DisplayName("elapsedBetween returns positive duration for forward nanos")
    void elapsedBetweenReturnsPositiveDurationForForwardNanos() {
        assertThat(ElapsedTimer.elapsedBetween(10, 25)).isEqualTo(Duration.ofNanos(15));
    }

    @Test
    @DisplayName("elapsedBetween returns zero for equal nanos")
    void elapsedBetweenReturnsZeroForEqualNanos() {
        assertThat(ElapsedTimer.elapsedBetween(10, 10)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("elapsedBetween returns zero for backward nanos")
    void elapsedBetweenReturnsZeroForBackwardNanos() {
        assertThat(ElapsedTimer.elapsedBetween(25, 10)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("start().elapsed() returns non-negative duration")
    void startElapsedReturnsNonNegativeDuration() {
        Duration elapsed = ElapsedTimer.start().elapsed();

        assertThat(elapsed).isNotNull();
        assertThat(elapsed.isNegative()).isFalse();
    }

    @Test
    @DisplayName("elapsed time is independent of wall-clock time")
    void elapsedTimeIsIndependentOfWallClockTime() {
        long nanoStart = System.nanoTime();
        long millisStart = System.currentTimeMillis();
        ElapsedTimer timer = ElapsedTimer.start();

        long elapsedNanos = System.nanoTime() - nanoStart;
        long elapsedMillis = System.currentTimeMillis() - millisStart;
        Duration elapsed = timer.elapsed();

        assertThat(elapsed.isNegative()).isFalse();
        assertThat(elapsed.toNanos()).isLessThanOrEqualTo(elapsedNanos + 1_000_000L);
        assertThat(elapsed.toMillis()).isLessThanOrEqualTo(elapsedMillis + 1L);
    }

    @Test
    @DisplayName("elapsed duration is accurate for a known sleep")
    void elapsedDurationIsAccurateForKnownSleep() throws InterruptedException {
        ElapsedTimer timer = ElapsedTimer.start();
        Thread.sleep(100);
        Duration elapsed = timer.elapsed();

        assertThat(elapsed.toMillis()).isGreaterThanOrEqualTo(90L);
        assertThat(elapsed.toMillis()).isLessThan(500L);
    }

    @Test
    @DisplayName("elapsedBetween returns zero for negative nanoTime difference")
    void elapsedBetweenReturnsZeroForNegativeNanoTimeDifference() {
        long simulatedClockJump = 1_000_000_000L;
        assertThat(ElapsedTimer.elapsedBetween(5 * simulatedClockJump, simulatedClockJump))
                .isEqualTo(Duration.ZERO);
    }
}

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

package org.paramixel.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Backoff")
class BackoffTest {

    @Test
    @DisplayName("fixed returns the same wait for every attempt")
    void fixed() {
        var backoff = Backoff.fixed(Duration.ofMillis(50));

        assertThat(backoff.kind()).isEqualTo(Backoff.Kind.FIXED);
        assertThat(backoff.delayFor(0)).isEqualTo(Duration.ZERO);
        assertThat(backoff.delayFor(1)).isEqualTo(Duration.ofMillis(50));
        assertThat(backoff.delayFor(64)).isEqualTo(Duration.ofMillis(50));
    }

    @Test
    @DisplayName("linear grows by the step")
    void linear() {
        var backoff = Backoff.linear(Duration.ofMillis(100));

        assertThat(backoff.kind()).isEqualTo(Backoff.Kind.LINEAR);
        assertThat(backoff.delayFor(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(backoff.delayFor(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(backoff.delayFor(3)).isEqualTo(Duration.ofMillis(300));
    }

    @Test
    @DisplayName("exponential doubles and saturates at the maximum")
    void exponential() {
        var backoff = Backoff.exponential(Duration.ofMillis(10), Duration.ofMillis(100));

        assertThat(backoff.delayFor(1)).isEqualTo(Duration.ofMillis(10));
        assertThat(backoff.delayFor(2)).isEqualTo(Duration.ofMillis(20));
        assertThat(backoff.delayFor(3)).isEqualTo(Duration.ofMillis(40));
        assertThat(backoff.delayFor(4)).isEqualTo(Duration.ofMillis(80));
        assertThat(backoff.delayFor(5)).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    @DisplayName("exponential at iteration 64 returns the maximum and never throws")
    void exponentialAtIteration64Saturates() {
        var maximum = Duration.ofSeconds(5);
        var backoff = Backoff.exponential(Duration.ofMillis(1), maximum);

        assertThat(backoff.delayFor(64)).isEqualTo(maximum);
        assertThat(backoff.delayFor(Integer.MAX_VALUE)).isEqualTo(maximum);
    }

    @Test
    @DisplayName("a linear schedule saturates instead of overflowing")
    void linearSaturates() {
        var backoff = Backoff.linear(Duration.ofSeconds(Long.MAX_VALUE));

        assertThat(backoff.delayFor(2)).isEqualTo(Duration.ofSeconds(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("an attempt below one produces no wait")
    void belowOneAttemptHasNoWait() {
        assertThat(Backoff.fixed(Duration.ofSeconds(1)).delayFor(0)).isEqualTo(Duration.ZERO);
        assertThat(Backoff.linear(Duration.ofSeconds(1)).delayFor(-5)).isEqualTo(Duration.ZERO);
        assertThat(Backoff.exponential(Duration.ofSeconds(1), Duration.ofSeconds(2))
                        .delayFor(0))
                .isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("factories reject invalid durations")
    void factoriesRejectInvalidDurations() {
        assertThatThrownBy(() -> Backoff.fixed(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Backoff.fixed(Duration.ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Backoff.linear(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Backoff.linear(Duration.ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Backoff.exponential(null, Duration.ZERO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Backoff.exponential(Duration.ZERO, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Backoff.exponential(Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max is shorter than base");
    }

    @Test
    @DisplayName("toString names the schedule")
    void toStringNamesTheSchedule() {
        assertThat(Backoff.fixed(Duration.ofMillis(1))).hasToString("fixed(PT0.001S)");
        assertThat(Backoff.linear(Duration.ofMillis(1))).hasToString("linear(PT0.001S)");
        assertThat(Backoff.exponential(Duration.ofMillis(1), Duration.ofMillis(2)))
                .hasToString("exponential(PT0.001S, PT0.002S)");
    }
}

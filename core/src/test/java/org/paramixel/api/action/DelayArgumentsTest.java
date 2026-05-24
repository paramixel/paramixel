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

package org.paramixel.api.action;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Delay arguments")
class DelayArgumentsTest {

    @Test
    @DisplayName("of(String, long) rejects null name")
    void ofLongRejectsNullName() {
        assertThatThrownBy(() -> Delay.of(null, 1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, long) rejects blank name")
    void ofLongRejectsBlankName() {
        assertThatThrownBy(() -> Delay.of(" ", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String, long) rejects negative milliseconds")
    void ofLongRejectsNegativeMillis() {
        assertThatThrownBy(() -> Delay.of("delay", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String, long) accepts zero milliseconds")
    void ofLongAcceptsZeroMillis() {
        var delay = Delay.of("delay", 0);
        assertThat(delay).isNotNull();
        assertThat(delay.name()).isEqualTo("delay");
    }

    @Test
    @DisplayName("of(String, long) creates delay with name")
    void ofLongCreatesDelay() {
        var delay = Delay.of("delay", 100);
        assertThat(delay.name()).isEqualTo("delay");
        assertThat(delay.kind()).isEqualTo("Delay");
    }

    @Test
    @DisplayName("of(String, Duration) rejects null name")
    void ofDurationRejectsNullName() {
        assertThatThrownBy(() -> Delay.of(null, ofMillis(1))).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, Duration) rejects blank name")
    void ofDurationRejectsBlankName() {
        assertThatThrownBy(() -> Delay.of(" ", ofMillis(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String, Duration) rejects null duration")
    void ofDurationRejectsNullDuration() {
        assertThatThrownBy(() -> Delay.of("delay", (Duration) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, Duration) rejects negative duration")
    void ofDurationRejectsNegativeDuration() {
        assertThatThrownBy(() -> Delay.of("delay", ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String, Duration) creates delay with name")
    void ofDurationCreatesDelay() {
        var delay = Delay.of("delay", ofMillis(100));
        assertThat(delay.name()).isEqualTo("delay");
    }

    @Test
    @DisplayName("random(String, long, long) rejects null name")
    void randomRejectsNullName() {
        assertThatThrownBy(() -> Delay.random(null, 1, 10)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("random(String, long, long) rejects blank name")
    void randomRejectsBlankName() {
        assertThatThrownBy(() -> Delay.random(" ", 1, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("random(String, long, long) rejects negative minimum")
    void randomRejectsNegativeMinimum() {
        assertThatThrownBy(() -> Delay.random("delay", -1, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("random(String, long, long) rejects maximum less than minimum")
    void randomRejectsMaxLessThanMin() {
        assertThatThrownBy(() -> Delay.random("delay", 10, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("random(String, long, long) accepts equal min and max")
    void randomAcceptsEqualMinMax() {
        var delay = Delay.random("delay", 5, 5);
        assertThat(delay).isNotNull();
        assertThat(delay.name()).isEqualTo("delay");
    }

    @Test
    @DisplayName("random(String, long, long) creates delay with name")
    void randomCreatesDelay() {
        var delay = Delay.random("delay", 1, 10);
        assertThat(delay.name()).isEqualTo("delay");
        assertThat(delay.kind()).isEqualTo("Delay");
    }
}

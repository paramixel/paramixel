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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// BackoffDelay exposes no no-arg constructor: floor, ceiling, and growth factor are all required,
// so there is no "default values" case to test. All cases below construct with explicit values.
@DisplayName("BackoffDelay")
class BackoffDelayTest {

    @Test
    @DisplayName("first nextDelayNanos returns the floor")
    void firstDelayReturnsFloor() {
        var backoff = new BackoffDelay(1_000L, 1_000_000_000L, 4.0);

        assertThat(backoff.nextDelayNanos()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("delays grow by the growth factor and clamp at the ceiling")
    void delaysGrowAndClampAtCeiling() {
        var backoff = new BackoffDelay(1_000L, 1_000_000_000L, 4.0);

        assertThat(collect(backoff, 12))
                .containsExactly(
                        1_000L,
                        4_000L,
                        16_000L,
                        64_000L,
                        256_000L,
                        1_024_000L,
                        4_096_000L,
                        16_384_000L,
                        65_536_000L,
                        262_144_000L,
                        1_000_000_000L, // would be 1_048_576_000; clamped at the ceiling
                        1_000_000_000L);
    }

    @Test
    @DisplayName("reset returns the next delay to the floor and the series re-grows")
    void resetReturnsToFloorAndRegrows() {
        var backoff = new BackoffDelay(1_000L, 1_000_000_000L, 4.0);

        backoff.nextDelayNanos(); // 1_000
        backoff.nextDelayNanos(); // 4_000
        backoff.reset();

        assertThat(backoff.nextDelayNanos()).isEqualTo(1_000L);
        assertThat(backoff.nextDelayNanos()).isEqualTo(4_000L);
    }

    @Test
    @DisplayName("interleaving reset between delays always restores the floor")
    void resetInterleavedRestoresFloor() {
        var backoff = new BackoffDelay(1_000L, 1_000_000_000L, 4.0);

        assertThat(backoff.nextDelayNanos()).isEqualTo(1_000L);
        backoff.reset();
        assertThat(backoff.nextDelayNanos()).isEqualTo(1_000L);
        backoff.reset();
        assertThat(backoff.nextDelayNanos()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("constructor rejects non-positive floor")
    void rejectsNonPositiveFloor() {
        assertThatThrownBy(() -> new BackoffDelay(0L, 1_000L, 4.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("floorNanos");
        assertThatThrownBy(() -> new BackoffDelay(-1L, 1_000L, 4.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("floorNanos");
    }

    @Test
    @DisplayName("constructor rejects ceiling below floor")
    void rejectsCeilingBelowFloor() {
        assertThatThrownBy(() -> new BackoffDelay(1_000L, 999L, 4.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ceilingNanos");
    }

    @Test
    @DisplayName("constructor rejects growth factor at or below one, including NaN")
    void rejectsInvalidGrowthFactor() {
        assertThatThrownBy(() -> new BackoffDelay(1_000L, 1_000_000L, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("growthFactor");
        assertThatThrownBy(() -> new BackoffDelay(1_000L, 1_000_000L, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("growthFactor");
        assertThatThrownBy(() -> new BackoffDelay(1_000L, 1_000_000L, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("growthFactor");
    }

    @Test
    @DisplayName("large ceiling and factor clamp at the ceiling without long overflow")
    void largeValuesClampWithoutOverflow() {
        long ceiling = Long.MAX_VALUE / 2;
        var backoff = new BackoffDelay(1L, ceiling, 1e9);

        assertThat(collect(backoff, 5))
                .containsExactly(1L, 1_000_000_000L, 1_000_000_000_000_000_000L, ceiling, ceiling);
    }

    private static List<Long> collect(final BackoffDelay backoff, final int count) {
        var values = new ArrayList<Long>(count);
        for (int i = 0; i < count; i++) {
            values.add(backoff.nextDelayNanos());
        }
        return values;
    }
}

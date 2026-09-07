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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Action;

@DisplayName("Action execution strategies")
@SuppressWarnings({"deprecation", "removal"})
class ActionExecutionStrategiesTest {

    @Test
    @DisplayName("supports every permitted action subtype")
    void supportsEveryPermittedActionSubtype() {
        for (var type : Action.class.getPermittedSubclasses()) {
            assertThat(ActionExecutionStrategies.supports(type.asSubclass(Action.class)))
                    .as(type.getName())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("loop delay conversion")
    class LoopDelayConversion {

        @Test
        @DisplayName("sub-millisecond delays are not truncated to zero")
        void subMillisecondDelaysAreNotTruncated() {
            assertThat(ActionExecutionStrategies.delayNanosOrZero(Duration.ofNanos(900_000)))
                    .isEqualTo(900_000L);
            assertThat(ActionExecutionStrategies.delayNanosOrZero(Duration.ofNanos(1)))
                    .isEqualTo(1L);
        }

        @Test
        @DisplayName("millisecond and larger delays convert exactly")
        void millisecondDelaysConvertExactly() {
            assertThat(ActionExecutionStrategies.delayNanosOrZero(Duration.ofMillis(5)))
                    .isEqualTo(5_000_000L);
            assertThat(ActionExecutionStrategies.delayNanosOrZero(Duration.ofSeconds(2)))
                    .isEqualTo(2_000_000_000L);
        }

        @Test
        @DisplayName("zero and negative delays yield no delay")
        void zeroAndNegativeDelaysYieldNoDelay() {
            assertThat(ActionExecutionStrategies.delayNanosOrZero(Duration.ZERO))
                    .isZero();
            assertThat(ActionExecutionStrategies.delayNanosOrZero(Duration.ofNanos(-5)))
                    .isZero();
        }
    }
}

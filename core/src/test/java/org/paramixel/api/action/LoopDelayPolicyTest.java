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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Loop.DelayPolicy;
import org.paramixel.api.exception.PolicyException;

@DisplayName("Loop.DelayPolicy")
class LoopDelayPolicyTest {

    @Nested
    @DisplayName("Linear")
    class Linear {

        @Test
        @DisplayName("returns constant delay for all iterations")
        void returnsConstantDelayForAllIterations() {
            var policy = new DelayPolicy.Linear(Duration.ofMillis(100));
            assertThat(policy.delayForIteration(1)).isEqualTo(Duration.ofMillis(100));
            assertThat(policy.delayForIteration(2)).isEqualTo(Duration.ofMillis(100));
            assertThat(policy.delayForIteration(10)).isEqualTo(Duration.ofMillis(100));
        }

        @Test
        @DisplayName("returns zero for iteration 0 or negative")
        void returnsZeroForIterationZeroOrNegative() {
            var policy = new DelayPolicy.Linear(Duration.ofMillis(100));
            assertThat(policy.delayForIteration(0)).isEqualTo(Duration.ZERO);
            assertThat(policy.delayForIteration(-1)).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("rejects null delay")
        void rejectsNullDelay() {
            assertThatThrownBy(() -> new DelayPolicy.Linear(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("delay is null");
        }

        @Test
        @DisplayName("rejects negative delay")
        void rejectsNegativeDelay() {
            assertThatThrownBy(() -> new DelayPolicy.Linear(Duration.ofMillis(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts zero delay")
        void acceptsZeroDelay() {
            var policy = new DelayPolicy.Linear(Duration.ZERO);
            assertThat(policy.delayForIteration(1)).isEqualTo(Duration.ZERO);
        }
    }

    @Nested
    @DisplayName("Exponential")
    class ExponentialTests {

        @Test
        @DisplayName("doubles delay each iteration")
        void doublesDelayEachIteration() {
            var policy = new DelayPolicy.Exponential(Duration.ofMillis(100));
            assertThat(policy.delayForIteration(1)).isEqualTo(Duration.ofMillis(100));
            assertThat(policy.delayForIteration(2)).isEqualTo(Duration.ofMillis(200));
            assertThat(policy.delayForIteration(3)).isEqualTo(Duration.ofMillis(400));
            assertThat(policy.delayForIteration(4)).isEqualTo(Duration.ofMillis(800));
        }

        @Test
        @DisplayName("returns zero for iteration 0 or negative")
        void returnsZeroForIterationZeroOrNegative() {
            var policy = new DelayPolicy.Exponential(Duration.ofMillis(100));
            assertThat(policy.delayForIteration(0)).isEqualTo(Duration.ZERO);
            assertThat(policy.delayForIteration(-1)).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("caps exponent at 62 for very high iterations")
        void capsExponentAt62ForHighIterations() {
            var policy = new DelayPolicy.Exponential(Duration.ofMillis(1));
            // exponent is capped at 62, so iteration 64 and iteration 100 produce the same result
            var delay64 = policy.delayForIteration(64);
            var delay100 = policy.delayForIteration(100);
            assertThat(delay64).isEqualTo(delay100);
            assertThat(delay64).isPositive();
        }

        @Test
        @DisplayName("rejects null baseDelay")
        void rejectsNullBaseDelay() {
            assertThatThrownBy(() -> new DelayPolicy.Exponential(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("baseDelay is null");
        }

        @Test
        @DisplayName("rejects negative baseDelay")
        void rejectsNegativeBaseDelay() {
            assertThatThrownBy(() -> new DelayPolicy.Exponential(Duration.ofMillis(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts zero baseDelay")
        void acceptsZeroBaseDelay() {
            var policy = new DelayPolicy.Exponential(Duration.ZERO);
            assertThat(policy.delayForIteration(1)).isEqualTo(Duration.ZERO);
            assertThat(policy.delayForIteration(2)).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("throws PolicyException on duration overflow")
        void throwsPolicyExceptionOnDurationOverflow() {
            // Use a base delay that passes constructor validation but overflows when multiplied
            // Long.MAX_VALUE / 1000 seconds is the largest that doesn't overflow toMillis()
            // Iteration 21 has multiplier 1L << 20 = 1048576, which causes overflow
            var policy = new DelayPolicy.Exponential(Duration.ofSeconds(Long.MAX_VALUE / 1000));
            assertThatThrownBy(() -> policy.delayForIteration(21))
                    .isInstanceOf(PolicyException.class)
                    .hasMessageContaining("duration overflow")
                    .hasCauseInstanceOf(ArithmeticException.class);
        }

        @Test
        @DisplayName("returns zero for very large iteration with zero base delay")
        void returnsZeroForVeryLargeIterationWithZeroBaseDelay() {
            var policy = new DelayPolicy.Exponential(Duration.ZERO);
            assertThat(policy.delayForIteration(100)).isEqualTo(Duration.ZERO);
        }
    }
}

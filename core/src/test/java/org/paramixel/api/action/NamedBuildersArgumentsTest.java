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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.paramixel.api.action.Assert.assertFalse;
import static org.paramixel.api.action.Assert.assertThat;
import static org.paramixel.api.action.Assert.assertTrue;
import static org.paramixel.api.action.Conditional.conditional;
import static org.paramixel.api.action.Delay.delay;
import static org.paramixel.api.action.Delay.delayRandom;
import static org.paramixel.api.action.Instance.instance;
import static org.paramixel.api.action.Isolated.isolated;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.action.Timeout.timeout;

import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Named builders arguments")
@SuppressWarnings("removal")
class NamedBuildersArgumentsTest {

    @Nested
    @DisplayName("scope")
    class ScopeArgs {

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> scope(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> scope(" ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("sequence")
    class SequenceArgs {

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> Sequence.sequence(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Sequence.sequence(" ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("parallel")
    class ParallelArgs {

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> parallel(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> parallel(" ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("repeat")
    class RepeatArgs {

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> Repeat.repeat(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Repeat.repeat(" ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("conditional")
    class ConditionalArgs {

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> conditional(null, ctx -> true)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> conditional(" ", ctx -> true)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null condition")
        void rejectsNullCondition() {
            assertThatThrownBy(() -> conditional("name", null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("instance")
    class InstanceArgs {

        @Test
        @DisplayName("rejects null name with factory")
        void rejectsNullNameWithFactory() {
            assertThatThrownBy(() -> instance(null, () -> new Object())).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name with factory")
        void rejectsBlankNameWithFactory() {
            assertThatThrownBy(() -> instance(" ", () -> new Object())).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null factory")
        void rejectsNullFactory() {
            assertThatThrownBy(() -> instance("name", (java.util.function.Supplier<?>) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null class")
        void rejectsNullClass() {
            assertThatThrownBy(() -> instance((Class<?>) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null name with class")
        void rejectsNullNameWithClass() {
            assertThatThrownBy(() -> instance(null, Object.class)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name with class")
        void rejectsBlankNameWithClass() {
            assertThatThrownBy(() -> instance(" ", Object.class)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null type with name")
        void rejectsNullTypeWithName() {
            assertThatThrownBy(() -> instance("name", (Class<?>) null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("isolated")
    class IsolatedArgs {

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> isolated(null, "lock")).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> isolated(" ", "lock")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null lockName")
        void rejectsNullLockName() {
            assertThatThrownBy(() -> isolated("name", null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank lockName")
        void rejectsBlankLockName() {
            assertThatThrownBy(() -> isolated("name", " ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("timeout")
    class TimeoutArgs {

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> timeout(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> timeout(" ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("until")
    class UntilArgs {

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> Until.until(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Until.until(" ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("step")
    class StepArgs {

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> step(null, ctx -> {})).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> step(" ", ctx -> {})).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null consumer")
        void rejectsNullConsumer() {
            assertThatThrownBy(() -> step("name", null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("delay")
    class DelayArgs {

        @Test
        @DisplayName("delay with ms rejects null name")
        void delayWithMsRejectsNullName() {
            assertThatThrownBy(() -> delay(null, 500L)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("delay with ms rejects blank name")
        void delayWithMsRejectsBlankName() {
            assertThatThrownBy(() -> delay(" ", 500L)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delay with ms rejects negative milliseconds")
        void delayWithMsRejectsNegativeMillis() {
            assertThatThrownBy(() -> delay("pause", -1L)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delay with Duration rejects null name")
        void delayWithDurationRejectsNullName() {
            assertThatThrownBy(() -> delay(null, java.time.Duration.ofSeconds(1)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("delay with Duration rejects blank name")
        void delayWithDurationRejectsBlankName() {
            assertThatThrownBy(() -> delay(" ", java.time.Duration.ofSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delay with Duration rejects null duration")
        void delayWithDurationRejectsNullDuration() {
            assertThatThrownBy(() -> delay("pause", (java.time.Duration) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("delayRandom rejects null name")
        void delayRandomRejectsNullName() {
            assertThatThrownBy(() -> delayRandom(null, 100, 500)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("delayRandom rejects blank name")
        void delayRandomRejectsBlankName() {
            assertThatThrownBy(() -> delayRandom(" ", 100, 500)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delayRandom rejects negative minimum")
        void delayRandomRejectsNegativeMinimum() {
            assertThatThrownBy(() -> delayRandom("pause", -1, 500)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delayRandom rejects max less than min")
        void delayRandomRejectsMaxLessThanMin() {
            assertThatThrownBy(() -> delayRandom("pause", 500, 100)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("assertThat / assertTrue / assertFalse")
    class AssertArgs {

        @Test
        @DisplayName("assertThat rejects null name")
        void assertThatRejectsNullName() {
            assertThatThrownBy(() -> assertThat(null, true, true)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("assertThat rejects blank name")
        void assertThatRejectsBlankName() {
            assertThatThrownBy(() -> assertThat(" ", true, true)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("assertThat rejects null name with message")
        void assertThatRejectsNullNameWithMessage() {
            assertThatThrownBy(() -> assertThat(null, true, true, "msg")).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("assertThat rejects null message")
        void assertThatRejectsNullMessage() {
            assertThatThrownBy(() -> assertThat("name", true, true, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("assertThat rejects blank message")
        void assertThatRejectsBlankMessage() {
            assertThatThrownBy(() -> assertThat("name", true, true, " ")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("assertThat rejects null supplier")
        void assertThatRejectsNullSupplier() {
            assertThatThrownBy(() -> assertThat("name", true, (BooleanSupplier) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("assertThat rejects null supplier with message")
        void assertThatRejectsNullSupplierWithMessage() {
            assertThatThrownBy(() -> assertThat("name", true, (BooleanSupplier) null, "msg"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("assertTrue rejects null name")
        void assertTrueRejectsNullName() {
            assertThatThrownBy(() -> assertTrue(null, true)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("assertTrue rejects blank name")
        void assertTrueRejectsBlankName() {
            assertThatThrownBy(() -> assertTrue(" ", true)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("assertTrue rejects null supplier")
        void assertTrueRejectsNullSupplier() {
            assertThatThrownBy(() -> assertTrue("name", (BooleanSupplier) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("assertFalse rejects null name")
        void assertFalseRejectsNullName() {
            assertThatThrownBy(() -> assertFalse(null, false)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("assertFalse rejects blank name")
        void assertFalseRejectsBlankName() {
            assertThatThrownBy(() -> assertFalse(" ", false)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("assertFalse rejects null supplier")
        void assertFalseRejectsNullSupplier() {
            assertThatThrownBy(() -> assertFalse("name", (BooleanSupplier) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}

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
import static org.paramixel.api.action.Conditional.conditional;
import static org.paramixel.api.action.Instance.of;
import static org.paramixel.api.action.Isolated.isolated;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.action.Timeout.timeout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Named builders arguments")
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
    @DisplayName("sequential")
    class SequentialArgs {

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> Sequential.sequential(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Sequential.sequential(" ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("parallel")
    class ParallelArgs {

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> parallel(null, "t0")).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> parallel(" ", "t1")).isInstanceOf(IllegalArgumentException.class);
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
            assertThatThrownBy(() -> of(null, Object.class, () -> new Object()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name with factory")
        void rejectsBlankNameWithFactory() {
            assertThatThrownBy(() -> of(" ", Object.class, () -> new Object()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null factory")
        void rejectsNullFactory() {
            assertThatThrownBy(() -> of("name", Object.class, (java.util.function.Supplier<?>) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null class")
        void rejectsNullClass() {
            assertThatThrownBy(() -> of((Class<?>) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null name with class")
        void rejectsNullNameWithClass() {
            assertThatThrownBy(() -> of(null, Object.class)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name with class")
        void rejectsBlankNameWithClass() {
            assertThatThrownBy(() -> of(" ", Object.class)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null type with name")
        void rejectsNullTypeWithName() {
            assertThatThrownBy(() -> of("name", (Class<?>) null)).isInstanceOf(NullPointerException.class);
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
    @DisplayName("loop")
    class LoopArgs {

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> Loop.loop(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Loop.loop(" ")).isInstanceOf(IllegalArgumentException.class);
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
}

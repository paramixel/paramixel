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

package org.paramixel.core.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;

@DisplayName("Sequential")
class SequentialTest {

    @Nested
    @DisplayName("of(String, List)")
    class OfList {

        @Test
        @DisplayName("creates sequential action with valid arguments")
        void createsWithValidArguments() {
            Action child = Noop.of("child");
            Sequential seq = Sequential.of("test", List.of(child));

            assertThat(seq.getName()).isEqualTo("test");
            assertThat(seq.getChildren()).hasSize(1);
        }

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> Sequential.of(null, List.of(Noop.of("c"))))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Sequential.of("", List.of(Noop.of("c"))))
                    .withMessage("name must not be blank");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Sequential.of("   ", List.of(Noop.of("c"))))
                    .withMessage("name must not be blank");
        }

        @Test
        @DisplayName("rejects null list")
        void rejectsNullList() {
            assertThatThrownBy(() -> Sequential.of("test", (List<Action>) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects empty list")
        void rejectsEmptyList() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Sequential.of("test", List.of()))
                    .withMessage("children must not be empty");
        }

        @Test
        @DisplayName("rejects null element in list")
        void rejectsNullElementInList() {
            ArrayList<Action> list = new ArrayList<>();
            list.add(null);
            assertThatThrownBy(() -> Sequential.of("test", list)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("of(String, Action...)")
    class OfVarargs {

        @Test
        @DisplayName("creates sequential action with valid varargs")
        void createsWithValidVarargs() {
            Sequential seq = Sequential.of("test", Noop.of("a"), Noop.of("b"));

            assertThat(seq.getName()).isEqualTo("test");
            assertThat(seq.getChildren()).hasSize(2);
        }

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> Sequential.of(null, Noop.of("c"))).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Sequential.of("", Noop.of("c")))
                    .withMessage("name must not be blank");
        }

        @Test
        @DisplayName("rejects null array")
        void rejectsNullArray() {
            assertThatThrownBy(() -> Sequential.of("test", (Action[]) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects empty array")
        void rejectsEmptyArray() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Sequential.of("test", new Action[0]))
                    .withMessage("children must not be empty");
        }

        @Test
        @DisplayName("rejects null element in array")
        void rejectsNullElementInArray() {
            Action[] actions = new Action[] {null};
            assertThatThrownBy(() -> Sequential.of("test", actions)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("rejects null context")
        void rejectsNullContext() {
            Sequential seq = Sequential.of("test", Noop.of("child"));

            assertThatThrownBy(() -> seq.execute(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("executes all children in order regardless of failures")
        void executesAllChildrenRegardlessOfFailures() {
            List<String> order = new ArrayList<>();
            Action first = Direct.of("first", ctx -> {
                order.add("first");
                throw new RuntimeException("fail");
            });
            Action second = Direct.of("second", ctx -> order.add("second"));

            Sequential seq = Sequential.of("test", first, second);
            Result result = Runner.builder().build().run(seq);

            assertThat(result.getStatus().isFailure()).isTrue();
            assertThat(order).containsExactly("first", "second");
        }

        @Test
        @DisplayName("computes pass status when all children pass")
        void computesPassStatusWhenAllChildrenPass() {
            Sequential seq = Sequential.of("test", Noop.of("a"), Noop.of("b"));
            Result result = Runner.builder().build().run(seq);

            assertThat(result.getStatus().isPass()).isTrue();
            assertThat(result.getChildren()).hasSize(2);
        }

        @Test
        @DisplayName("computes failure status when any child fails")
        void computesFailureStatusWhenAnyChildFails() {
            Sequential seq = Sequential.of("test", Noop.of("a"), Direct.of("b", ctx -> {
                throw new RuntimeException("fail");
            }));
            Result result = Runner.builder().build().run(seq);

            assertThat(result.getStatus().isFailure()).isTrue();
        }

        @Test
        @DisplayName("computes skip status when all children skip")
        void computesSkipStatusWhenAllChildrenSkip() {
            Sequential seq = Sequential.of("test", Direct.of("a", ctx -> {
                throw org.paramixel.core.exception.SkipException.of("skip");
            }));
            Result result = Runner.builder().build().run(seq);

            assertThat(result.getStatus().isSkip()).isTrue();
        }
    }

    @Nested
    @DisplayName("skip")
    class Skip {

        @Test
        @DisplayName("rejects null context")
        void rejectsNullContext() {
            Sequential seq = Sequential.of("test", Noop.of("child"));

            assertThatThrownBy(() -> seq.skip(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getChildren")
    class GetChildren {

        @Test
        @DisplayName("returns unmodifiable list with parent references")
        void returnsUnmodifiableListWithParentReferences() {
            Action child = Noop.of("child");
            Sequential seq = Sequential.of("test", child);

            assertThat(seq.getChildren()).containsExactly(child);
            assertThat(child.getParent()).isPresent().contains(seq);
        }
    }
}

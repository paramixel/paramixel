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

package org.paramixel.core.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Arguments")
class ArgumentsTest {

    @Nested
    @DisplayName("requireNonBlank(String, String)")
    class RequireNonBlankStringMessage {

        @Test
        @DisplayName("returns non-blank string")
        void returnsNonBlankString() {
            assertThat(Arguments.requireNonBlank("hello", "must not be blank")).isEqualTo("hello");
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThatThrownBy(() -> Arguments.requireNonBlank(null, "must not be blank"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects empty string")
        void rejectsEmpty() {
            assertThatThrownBy(() -> Arguments.requireNonBlank("", "must not be blank"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("must not be blank");
        }

        @Test
        @DisplayName("rejects blank string")
        void rejectsBlank() {
            assertThatThrownBy(() -> Arguments.requireNonBlank("   ", "must not be blank"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("must not be blank");
        }
    }

    @Nested
    @DisplayName("requireNonBlank(String, Supplier)")
    class RequireNonBlankStringSupplier {

        @Test
        @DisplayName("returns non-blank string")
        void returnsNonBlankString() {
            assertThat(Arguments.requireNonBlank("hello", () -> "must not be blank"))
                    .isEqualTo("hello");
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThatThrownBy(() -> Arguments.requireNonBlank(null, () -> "must not be blank"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank with supplier message")
        void rejectsBlankWithSupplierMessage() {
            assertThatThrownBy(() -> Arguments.requireNonBlank("", () -> "supplied message"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("supplied message");
        }

        @Test
        @DisplayName("does not evaluate supplier for valid value")
        void doesNotEvaluateSupplierForValidValue() {
            boolean[] evaluated = {false};
            Arguments.requireNonBlank("valid", () -> {
                evaluated[0] = true;
                return "should not be used";
            });
            assertThat(evaluated[0]).isFalse();
        }
    }

    @Nested
    @DisplayName("requireNotEmpty(String, String)")
    class RequireNotEmpty {

        @Test
        @DisplayName("returns non-empty string")
        void returnsNonEmptyString() {
            assertThat(Arguments.requireNotEmpty("hello", "must not be empty")).isEqualTo("hello");
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThatThrownBy(() -> Arguments.requireNotEmpty(null, "must not be empty"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects empty string")
        void rejectsEmpty() {
            assertThatThrownBy(() -> Arguments.requireNotEmpty("", "must not be empty"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("must not be empty");
        }

        @Test
        @DisplayName("accepts blank but non-empty string")
        void acceptsBlankButNonEmpty() {
            assertThat(Arguments.requireNotEmpty("   ", "must not be empty")).isEqualTo("   ");
        }
    }

    @Nested
    @DisplayName("requirePositive(int, String)")
    class RequirePositive {

        @Test
        @DisplayName("returns positive value")
        void returnsPositiveValue() {
            assertThat(Arguments.requirePositive(5, "must be positive")).isEqualTo(5);
        }

        @Test
        @DisplayName("rejects zero")
        void rejectsZero() {
            assertThatThrownBy(() -> Arguments.requirePositive(0, "must be positive"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("must be positive");
        }

        @Test
        @DisplayName("rejects negative value")
        void rejectsNegative() {
            assertThatThrownBy(() -> Arguments.requirePositive(-1, "must be positive"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("must be positive");
        }
    }

    @Nested
    @DisplayName("requireNonNegative(long, String)")
    class RequireNonNegative {

        @Test
        @DisplayName("returns zero value")
        void returnsZero() {
            assertThat(Arguments.requireNonNegative(0L, "must be non-negative")).isEqualTo(0L);
        }

        @Test
        @DisplayName("returns positive value")
        void returnsPositive() {
            assertThat(Arguments.requireNonNegative(10L, "must be non-negative"))
                    .isEqualTo(10L);
        }

        @Test
        @DisplayName("rejects negative value")
        void rejectsNegative() {
            assertThatThrownBy(() -> Arguments.requireNonNegative(-1L, "must be non-negative"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("must be non-negative");
        }
    }

    @Nested
    @DisplayName("require(boolean, String)")
    class RequireBooleanString {

        @Test
        @DisplayName("passes when condition is true")
        void passesWhenTrue() {
            Arguments.require(true, "should not throw");
        }

        @Test
        @DisplayName("throws when condition is false")
        void throwsWhenFalse() {
            assertThatThrownBy(() -> Arguments.require(false, "condition failed"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("condition failed");
        }
    }

    @Nested
    @DisplayName("require(boolean, Supplier)")
    class RequireBooleanSupplier {

        @Test
        @DisplayName("passes when condition is true")
        void passesWhenTrue() {
            Arguments.require(true, () -> "should not throw");
        }

        @Test
        @DisplayName("throws with supplier message when condition is false")
        void throwsWithSupplierMessageWhenFalse() {
            assertThatThrownBy(() -> Arguments.require(false, () -> "supplied message"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("supplied message");
        }

        @Test
        @DisplayName("rejects null supplier")
        void rejectsNullSupplier() {
            assertThatThrownBy(() -> Arguments.require(false, (java.util.function.Supplier<String>) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("requireNonEmpty(Collection, String)")
    class RequireNonEmptyCollection {

        @Test
        @DisplayName("returns non-empty collection")
        void returnsNonEmptyCollection() {
            List<String> list = List.of("a");
            assertThat(Arguments.requireNonEmpty(list, "must not be empty")).isSameAs(list);
        }

        @Test
        @DisplayName("rejects empty collection")
        void rejectsEmptyCollection() {
            assertThatThrownBy(() -> Arguments.requireNonEmpty(Collections.emptyList(), "must not be empty"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("must not be empty");
        }
    }

    @Nested
    @DisplayName("requireNoNullElements(Collection, String)")
    class RequireNoNullElementsCollection {

        @Test
        @DisplayName("returns collection with no null elements")
        void returnsCollectionWithNoNullElements() {
            List<String> list = List.of("a", "b");
            assertThat(Arguments.requireNoNullElements(list, "no nulls")).isSameAs(list);
        }

        @Test
        @DisplayName("rejects collection with null element")
        void rejectsCollectionWithNullElement() {
            ArrayList<String> list = new ArrayList<>();
            list.add(null);
            assertThatThrownBy(() -> Arguments.requireNoNullElements(list, "no nulls"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("no nulls");
        }
    }

    @Nested
    @DisplayName("requireNoNullElements(T[], String)")
    class RequireNoNullElementsArray {

        @Test
        @DisplayName("returns array with no null elements")
        void returnsArrayWithNoNullElements() {
            String[] array = {"a", "b"};
            assertThat(Arguments.requireNoNullElements(array, "no nulls")).isSameAs(array);
        }

        @Test
        @DisplayName("rejects array with null element")
        void rejectsArrayWithNullElement() {
            String[] array = {"a", null, "b"};
            assertThatThrownBy(() -> Arguments.requireNoNullElements(array, "no nulls"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("no nulls");
        }

        @Test
        @DisplayName("accepts empty array")
        void acceptsEmptyArray() {
            String[] array = {};
            assertThat(Arguments.requireNoNullElements(array, "no nulls")).isSameAs(array);
        }
    }
}

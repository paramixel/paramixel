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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Assert arguments")
class AssertArgumentsTest {

    @Nested
    @DisplayName("AssertTrue arguments")
    class AssertTrueArgumentsTests {

        @Test
        @DisplayName("of(String, boolean) rejects null name")
        void ofBooleanRejectsNullName() {
            assertThatThrownBy(() -> AssertTrue.of(null, true)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, boolean) rejects blank name")
        void ofBooleanRejectsBlankName() {
            assertThatThrownBy(() -> AssertTrue.of(" ", true)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of(String, boolean, String) rejects null name")
        void ofBooleanWithMessageRejectsNullName() {
            assertThatThrownBy(() -> AssertTrue.of(null, true, "msg")).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, boolean, String) rejects blank name")
        void ofBooleanWithMessageRejectsBlankName() {
            assertThatThrownBy(() -> AssertTrue.of(" ", true, "msg")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of(String, boolean, String) rejects null message")
        void ofBooleanWithMessageRejectsNullMessage() {
            assertThatThrownBy(() -> AssertTrue.of("name", true, (String) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, boolean, String) rejects blank message")
        void ofBooleanWithMessageRejectsBlankMessage() {
            assertThatThrownBy(() -> AssertTrue.of("name", true, " ")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) rejects null name")
        void ofSupplierRejectsNullName() {
            assertThatThrownBy(() -> AssertTrue.of(null, () -> true)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) rejects blank name")
        void ofSupplierRejectsBlankName() {
            assertThatThrownBy(() -> AssertTrue.of(" ", () -> true)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) rejects null supplier")
        void ofSupplierRejectsNullSupplier() {
            assertThatThrownBy(() -> AssertTrue.of("name", (java.util.function.BooleanSupplier) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) rejects null name")
        void ofSupplierWithMessageRejectsNullName() {
            assertThatThrownBy(() -> AssertTrue.of(null, () -> true, "msg")).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) rejects blank name")
        void ofSupplierWithMessageRejectsBlankName() {
            assertThatThrownBy(() -> AssertTrue.of(" ", () -> true, "msg"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) rejects null supplier")
        void ofSupplierWithMessageRejectsNullSupplier() {
            assertThatThrownBy(() -> AssertTrue.of("name", null, "msg")).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) rejects null message")
        void ofSupplierWithMessageRejectsNullMessage() {
            assertThatThrownBy(() -> AssertTrue.of("name", () -> true, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) rejects blank message")
        void ofSupplierWithMessageRejectsBlankMessage() {
            assertThatThrownBy(() -> AssertTrue.of("name", () -> true, " "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of(String, boolean) creates assert-true with name")
        void ofBooleanCreatesAssertTrue() {
            var action = AssertTrue.of("assert", true);
            assertThat(action.name()).isEqualTo("assert");
            assertThat(action.kind()).isEqualTo("AssertTrue");
        }

        @Test
        @DisplayName("of(String, boolean, String) creates assert-true with name")
        void ofBooleanWithMessageCreatesAssertTrue() {
            var action = AssertTrue.of("assert", true, "msg");
            assertThat(action.name()).isEqualTo("assert");
            assertThat(action.kind()).isEqualTo("AssertTrue");
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) creates assert-true with name")
        void ofSupplierCreatesAssertTrue() {
            var action = AssertTrue.of("assert", () -> true);
            assertThat(action.name()).isEqualTo("assert");
            assertThat(action.kind()).isEqualTo("AssertTrue");
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) creates assert-true with name")
        void ofSupplierWithMessageCreatesAssertTrue() {
            var action = AssertTrue.of("assert", () -> true, "msg");
            assertThat(action.name()).isEqualTo("assert");
            assertThat(action.kind()).isEqualTo("AssertTrue");
        }
    }

    @Nested
    @DisplayName("AssertFalse arguments")
    class AssertFalseArgumentsTests {

        @Test
        @DisplayName("of(String, boolean) rejects null name")
        void ofBooleanRejectsNullName() {
            assertThatThrownBy(() -> AssertFalse.of(null, false)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, boolean) rejects blank name")
        void ofBooleanRejectsBlankName() {
            assertThatThrownBy(() -> AssertFalse.of(" ", false)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of(String, boolean, String) rejects null name")
        void ofBooleanWithMessageRejectsNullName() {
            assertThatThrownBy(() -> AssertFalse.of(null, false, "msg")).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, boolean, String) rejects blank name")
        void ofBooleanWithMessageRejectsBlankName() {
            assertThatThrownBy(() -> AssertFalse.of(" ", false, "msg")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of(String, boolean, String) rejects null message")
        void ofBooleanWithMessageRejectsNullMessage() {
            assertThatThrownBy(() -> AssertFalse.of("name", false, (String) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, boolean, String) rejects blank message")
        void ofBooleanWithMessageRejectsBlankMessage() {
            assertThatThrownBy(() -> AssertFalse.of("name", false, " ")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) rejects null name")
        void ofSupplierRejectsNullName() {
            assertThatThrownBy(() -> AssertFalse.of(null, () -> false)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) rejects blank name")
        void ofSupplierRejectsBlankName() {
            assertThatThrownBy(() -> AssertFalse.of(" ", () -> false)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) rejects null supplier")
        void ofSupplierRejectsNullSupplier() {
            assertThatThrownBy(() -> AssertFalse.of("name", (java.util.function.BooleanSupplier) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) rejects null name")
        void ofSupplierWithMessageRejectsNullName() {
            assertThatThrownBy(() -> AssertFalse.of(null, () -> false, "msg")).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) rejects blank name")
        void ofSupplierWithMessageRejectsBlankName() {
            assertThatThrownBy(() -> AssertFalse.of(" ", () -> false, "msg"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) rejects null supplier")
        void ofSupplierWithMessageRejectsNullSupplier() {
            assertThatThrownBy(() -> AssertFalse.of("name", null, "msg")).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) rejects null message")
        void ofSupplierWithMessageRejectsNullMessage() {
            assertThatThrownBy(() -> AssertFalse.of("name", () -> false, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) rejects blank message")
        void ofSupplierWithMessageRejectsBlankMessage() {
            assertThatThrownBy(() -> AssertFalse.of("name", () -> false, " "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of(String, boolean) creates assert-false with name")
        void ofBooleanCreatesAssertFalse() {
            var action = AssertFalse.of("assert", false);
            assertThat(action.name()).isEqualTo("assert");
            assertThat(action.kind()).isEqualTo("AssertFalse");
        }

        @Test
        @DisplayName("of(String, boolean, String) creates assert-false with name")
        void ofBooleanWithMessageCreatesAssertFalse() {
            var action = AssertFalse.of("assert", false, "msg");
            assertThat(action.name()).isEqualTo("assert");
            assertThat(action.kind()).isEqualTo("AssertFalse");
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) creates assert-false with name")
        void ofSupplierCreatesAssertFalse() {
            var action = AssertFalse.of("assert", () -> false);
            assertThat(action.name()).isEqualTo("assert");
            assertThat(action.kind()).isEqualTo("AssertFalse");
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) creates assert-false with name")
        void ofSupplierWithMessageCreatesAssertFalse() {
            var action = AssertFalse.of("assert", () -> false, "msg");
            assertThat(action.name()).isEqualTo("assert");
            assertThat(action.kind()).isEqualTo("AssertFalse");
        }
    }
}

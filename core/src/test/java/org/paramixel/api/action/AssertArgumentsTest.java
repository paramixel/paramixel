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

import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Assert arguments")
class AssertArgumentsTest {

    @Test
    @DisplayName("of(String, boolean, boolean) rejects null name")
    void ofBooleanRejectsNullName() {
        assertThatThrownBy(() -> Assert.of(null, true, true)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, boolean, boolean) rejects blank name")
    void ofBooleanRejectsBlankName() {
        assertThatThrownBy(() -> Assert.of(" ", true, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String, boolean, boolean, String) rejects null name")
    void ofBooleanWithMessageRejectsNullName() {
        assertThatThrownBy(() -> Assert.of(null, true, true, "msg")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, boolean, boolean, String) rejects blank name")
    void ofBooleanWithMessageRejectsBlankName() {
        assertThatThrownBy(() -> Assert.of(" ", true, true, "msg")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String, boolean, boolean, String) rejects null message")
    void ofBooleanWithMessageRejectsNullMessage() {
        assertThatThrownBy(() -> Assert.of("name", true, true, (String) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, boolean, boolean, String) rejects blank message")
    void ofBooleanWithMessageRejectsBlankMessage() {
        assertThatThrownBy(() -> Assert.of("name", true, true, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String, boolean, BooleanSupplier) rejects null name")
    void ofSupplierRejectsNullName() {
        assertThatThrownBy(() -> Assert.of(null, true, () -> true)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, boolean, BooleanSupplier) rejects blank name")
    void ofSupplierRejectsBlankName() {
        assertThatThrownBy(() -> Assert.of(" ", true, () -> true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String, boolean, BooleanSupplier) rejects null supplier")
    void ofSupplierRejectsNullSupplier() {
        assertThatThrownBy(() -> Assert.of("name", true, (BooleanSupplier) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, boolean, BooleanSupplier, String) rejects null name")
    void ofSupplierWithMessageRejectsNullName() {
        assertThatThrownBy(() -> Assert.of(null, true, () -> true, "msg")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, boolean, BooleanSupplier, String) rejects blank name")
    void ofSupplierWithMessageRejectsBlankName() {
        assertThatThrownBy(() -> Assert.of(" ", true, () -> true, "msg")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String, boolean, BooleanSupplier, String) rejects null supplier")
    void ofSupplierWithMessageRejectsNullSupplier() {
        assertThatThrownBy(() -> Assert.of("name", true, null, "msg")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, boolean, BooleanSupplier, String) rejects null message")
    void ofSupplierWithMessageRejectsNullMessage() {
        assertThatThrownBy(() -> Assert.of("name", true, () -> true, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, boolean, BooleanSupplier, String) rejects blank message")
    void ofSupplierWithMessageRejectsBlankMessage() {
        assertThatThrownBy(() -> Assert.of("name", true, () -> true, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String, boolean, boolean) creates assert with name")
    void ofBooleanCreatesAssert() {
        var action = Assert.of("assert", true, true);
        assertThat(action.displayName()).isEqualTo("assert");
        assertThat(action).isInstanceOf(Assert.class);
    }

    @Test
    @DisplayName("of(String, boolean, boolean, String) creates assert with name")
    void ofBooleanWithMessageCreatesAssert() {
        var action = Assert.of("assert", true, true, "msg");
        assertThat(action.displayName()).isEqualTo("assert");
        assertThat(action).isInstanceOf(Assert.class);
    }

    @Test
    @DisplayName("of(String, boolean, BooleanSupplier) creates assert with name")
    void ofSupplierCreatesAssert() {
        var action = Assert.of("assert", true, () -> true);
        assertThat(action.displayName()).isEqualTo("assert");
        assertThat(action).isInstanceOf(Assert.class);
    }

    @Test
    @DisplayName("of(String, boolean, BooleanSupplier, String) creates assert with name")
    void ofSupplierWithMessageCreatesAssert() {
        var action = Assert.of("assert", true, () -> true, "msg");
        assertThat(action.displayName()).isEqualTo("assert");
        assertThat(action).isInstanceOf(Assert.class);
    }
}

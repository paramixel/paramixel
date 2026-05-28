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

import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Arguments string validation")
class ArgumentsStringValidationTest {

    @Test
    @DisplayName("requireNonBlank(String, String) returns non-blank string")
    void requireNonBlankStringMessageReturnsNonBlankString() {
        assertThat(Arguments.requireNonBlank("hello", "is blank")).isEqualTo("hello");
    }

    @Test
    @DisplayName("requireNonBlank(String, String) rejects null")
    void requireNonBlankStringMessageRejectsNull() {
        assertThatThrownBy(() -> Arguments.requireNonBlank(null, "is blank")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("requireNonBlank(String, String) rejects empty string")
    void requireNonBlankStringMessageRejectsEmpty() {
        assertThatThrownBy(() -> Arguments.requireNonBlank("", "is blank"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("is blank");
    }

    @Test
    @DisplayName("requireNonBlank(String, String) rejects blank string")
    void requireNonBlankStringMessageRejectsBlank() {
        assertThatThrownBy(() -> Arguments.requireNonBlank("   ", "is blank"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("is blank");
    }

    @Test
    @DisplayName("requireNonBlank(String, Supplier) returns non-blank string")
    void requireNonBlankStringSupplierReturnsNonBlankString() {
        assertThat(Arguments.requireNonBlank("hello", () -> "is blank")).isEqualTo("hello");
    }

    @Test
    @DisplayName("requireNonBlank(String, Supplier) rejects null")
    void requireNonBlankStringSupplierRejectsNull() {
        assertThatThrownBy(() -> Arguments.requireNonBlank(null, () -> "is blank"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("requireNonBlank(String, Supplier) rejects blank with supplier message")
    void requireNonBlankStringSupplierRejectsBlankWithSupplierMessage() {
        assertThatThrownBy(() -> Arguments.requireNonBlank("", () -> "supplied message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("supplied message");
    }

    @Test
    @DisplayName("requireNonBlank(String, Supplier) does not evaluate supplier for valid value")
    void requireNonBlankStringSupplierDoesNotEvaluateSupplierForValidValue() {
        boolean[] evaluated = {false};
        Arguments.requireNonBlank("valid", () -> {
            evaluated[0] = true;
            return "should not be used";
        });
        assertThat(evaluated[0]).isFalse();
    }

    @Test
    @DisplayName("requireNonBlank(String, Supplier) rejects null message supplier")
    void requireNonBlankStringSupplierRejectsNullMessageSupplier() {
        assertThatThrownBy(() -> Arguments.requireNonBlank("", (Supplier<String>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("requireNotEmpty(String, String) returns non-empty string")
    void requireNotEmptyReturnsNonEmptyString() {
        assertThat(Arguments.requireNotEmpty("hello", "is empty")).isEqualTo("hello");
    }

    @Test
    @DisplayName("requireNotEmpty(String, String) rejects null")
    void requireNotEmptyRejectsNull() {
        assertThatThrownBy(() -> Arguments.requireNotEmpty(null, "is empty")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("requireNotEmpty(String, String) rejects empty string")
    void requireNotEmptyRejectsEmpty() {
        assertThatThrownBy(() -> Arguments.requireNotEmpty("", "is empty"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("is empty");
    }

    @Test
    @DisplayName("requireNotEmpty(String, String) accepts blank but non-empty string")
    void requireNotEmptyAcceptsBlankButNonEmpty() {
        assertThat(Arguments.requireNotEmpty("   ", "is empty")).isEqualTo("   ");
    }
}

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

@DisplayName("Arguments type validation")
class ArgumentsTypeValidationTest {

    @Test
    @DisplayName("requireInstanceOf(Object, Class, String) returns instance when type matches")
    void requireInstanceOfStringReturnsInstanceWhenTypeMatches() {
        String value = "hello";
        assertThat(Arguments.requireInstanceOf(value, String.class, "wrong type"))
                .isSameAs(value);
    }

    @Test
    @DisplayName("requireInstanceOf(Object, Class, String) returns subclass instance")
    void requireInstanceOfStringReturnsSubclassInstance() {
        Number value = Integer.valueOf(42);
        assertThat(Arguments.requireInstanceOf(value, Number.class, "wrong type"))
                .isSameAs(value);
    }

    @Test
    @DisplayName("requireInstanceOf(Object, Class, String) rejects null object")
    void requireInstanceOfStringRejectsNullObject() {
        assertThatThrownBy(() -> Arguments.requireInstanceOf(null, String.class, "is null"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("requireInstanceOf(Object, Class, String) rejects null type")
    void requireInstanceOfStringRejectsNullType() {
        assertThatThrownBy(() -> Arguments.requireInstanceOf("hello", null, "is null"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("requireInstanceOf(Object, Class, String) rejects wrong type")
    void requireInstanceOfStringRejectsWrongType() {
        assertThatThrownBy(() -> Arguments.requireInstanceOf(42, String.class, "wrong type"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("wrong type");
    }

    @Test
    @DisplayName("requireInstanceOf(Object, Class, Supplier) returns instance when type matches")
    void requireInstanceOfSupplierReturnsInstanceWhenTypeMatches() {
        String value = "hello";
        assertThat(Arguments.requireInstanceOf(value, String.class, () -> "wrong type"))
                .isSameAs(value);
    }

    @Test
    @DisplayName("requireInstanceOf(Object, Class, Supplier) rejects null object")
    void requireInstanceOfSupplierRejectsNullObject() {
        assertThatThrownBy(() -> Arguments.requireInstanceOf(null, String.class, () -> "is null"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("requireInstanceOf(Object, Class, Supplier) rejects null type")
    void requireInstanceOfSupplierRejectsNullType() {
        assertThatThrownBy(() -> Arguments.requireInstanceOf("hello", null, () -> "is null"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("requireInstanceOf(Object, Class, Supplier) rejects wrong type with supplier message")
    void requireInstanceOfSupplierRejectsWrongTypeWithSupplierMessage() {
        assertThatThrownBy(() -> Arguments.requireInstanceOf(42, String.class, () -> "supplied message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("supplied message");
    }

    @Test
    @DisplayName("requireInstanceOf(Object, Class, Supplier) rejects null supplier")
    void requireInstanceOfSupplierRejectsNullSupplier() {
        assertThatThrownBy(() -> Arguments.requireInstanceOf("hello", String.class, (Supplier<String>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("requireInstanceOf(Object, Class, Supplier) does not evaluate supplier for valid value")
    void requireInstanceOfSupplierDoesNotEvaluateSupplierForValidValue() {
        boolean[] evaluated = {false};
        Arguments.requireInstanceOf("valid", String.class, () -> {
            evaluated[0] = true;
            return "should not be used";
        });
        assertThat(evaluated[0]).isFalse();
    }
}

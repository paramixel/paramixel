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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Arguments boolean validation")
class ArgumentsBooleanValidationTest {

    @Test
    @DisplayName("requireTrue(boolean, String) passes when condition is true")
    void requireTrueStringPassesWhenTrue() {
        Arguments.requireTrue(true, "should not throw");
    }

    @Test
    @DisplayName("requireTrue(boolean, String) throws when condition is false")
    void requireTrueStringThrowsWhenFalse() {
        assertThatThrownBy(() -> Arguments.requireTrue(false, "condition failed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("condition failed");
    }

    @Test
    @DisplayName("requireTrue(boolean, Supplier) passes when condition is true")
    void requireTrueSupplierPassesWhenTrue() {
        Arguments.requireTrue(true, () -> "should not throw");
    }

    @Test
    @DisplayName("requireTrue(boolean, Supplier) throws with supplier message when condition is false")
    void requireTrueSupplierThrowsWithSupplierMessageWhenFalse() {
        assertThatThrownBy(() -> Arguments.requireTrue(false, () -> "supplied message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("supplied message");
    }

    @Test
    @DisplayName("requireTrue(boolean, Supplier) rejects null supplier")
    void requireTrueSupplierRejectsNullSupplier() {
        assertThatThrownBy(() -> Arguments.requireTrue(false, (Supplier<String>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("requireFalse(boolean, String) passes when condition is false")
    void requireFalseStringPassesWhenFalse() {
        Arguments.requireFalse(false, "should not throw");
    }

    @Test
    @DisplayName("requireFalse(boolean, String) throws when condition is true")
    void requireFalseStringThrowsWhenTrue() {
        assertThatThrownBy(() -> Arguments.requireFalse(true, "condition was true"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("condition was true");
    }

    @Test
    @DisplayName("requireFalse(boolean, Supplier) passes when condition is false")
    void requireFalseSupplierPassesWhenFalse() {
        Arguments.requireFalse(false, () -> "should not throw");
    }

    @Test
    @DisplayName("requireFalse(boolean, Supplier) throws with supplier message when condition is true")
    void requireFalseSupplierThrowsWithSupplierMessageWhenTrue() {
        assertThatThrownBy(() -> Arguments.requireFalse(true, () -> "supplied message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("supplied message");
    }

    @Test
    @DisplayName("requireFalse(boolean, Supplier) rejects null supplier")
    void requireFalseSupplierRejectsNullSupplier() {
        assertThatThrownBy(() -> Arguments.requireFalse(true, (Supplier<String>) null))
                .isInstanceOf(NullPointerException.class);
    }
}

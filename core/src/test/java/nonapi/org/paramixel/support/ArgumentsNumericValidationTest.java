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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Arguments numeric validation")
class ArgumentsNumericValidationTest {

    @Test
    @DisplayName("requirePositive(int, String) returns positive value")
    void requirePositiveReturnsPositiveValue() {
        assertThat(Arguments.requirePositive(5, "must be positive")).isEqualTo(5);
    }

    @Test
    @DisplayName("requirePositive(int, String) rejects zero")
    void requirePositiveRejectsZero() {
        assertThatThrownBy(() -> Arguments.requirePositive(0, "must be positive"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("must be positive");
    }

    @Test
    @DisplayName("requirePositive(int, String) rejects negative value")
    void requirePositiveRejectsNegative() {
        assertThatThrownBy(() -> Arguments.requirePositive(-1, "must be positive"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("must be positive");
    }

    @Test
    @DisplayName("requirePositive(long, String) returns positive value")
    void requirePositiveLongReturnsPositiveValue() {
        assertThat(Arguments.requirePositive(5L, "must be positive")).isEqualTo(5L);
    }

    @Test
    @DisplayName("requirePositive(long, String) rejects zero")
    void requirePositiveLongRejectsZero() {
        assertThatThrownBy(() -> Arguments.requirePositive(0L, "must be positive"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("must be positive");
    }

    @Test
    @DisplayName("requirePositive(long, String) rejects negative value")
    void requirePositiveLongRejectsNegative() {
        assertThatThrownBy(() -> Arguments.requirePositive(-1L, "must be positive"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("must be positive");
    }

    @Test
    @DisplayName("requireNonNegative(long, String) returns zero value")
    void requireNonNegativeReturnsZero() {
        assertThat(Arguments.requireNonNegative(0L, "must be non-negative")).isEqualTo(0L);
    }

    @Test
    @DisplayName("requireNonNegative(long, String) returns positive value")
    void requireNonNegativeReturnsPositive() {
        assertThat(Arguments.requireNonNegative(10L, "must be non-negative")).isEqualTo(10L);
    }

    @Test
    @DisplayName("requireNonNegative(long, String) rejects negative value")
    void requireNonNegativeRejectsNegative() {
        assertThatThrownBy(() -> Arguments.requireNonNegative(-1L, "must be non-negative"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("must be non-negative");
    }
}

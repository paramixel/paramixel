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

package org.paramixel.api.exception;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SkipException arguments")
class SkipExceptionArgumentsTest {

    @Test
    @DisplayName("skip(null) throws NullPointerException")
    void skipWithNullThrowsNullPointerException() {
        assertThatThrownBy(() -> SkipException.skip(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("skip(empty) throws IllegalArgumentException")
    void skipWithEmptyThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> SkipException.skip("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("skip(blank) throws IllegalArgumentException")
    void skipWithBlankThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> SkipException.skip("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("constructor rejects null")
    void constructorRejectsNull() {
        assertThatThrownBy(() -> new SkipException(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor rejects empty")
    void constructorRejectsEmpty() {
        assertThatThrownBy(() -> new SkipException("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("constructor rejects blank")
    void constructorRejectsBlank() {
        assertThatThrownBy(() -> new SkipException("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}

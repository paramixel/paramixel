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

package org.paramixel.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FailException")
class FailExceptionTest {

    @Test
    @DisplayName("fail() throws with default message")
    void failNoArgsThrowsWithDefaultMessage() {
        assertThatThrownBy(() -> FailException.fail())
                .isInstanceOf(FailException.class)
                .hasMessage("failed");
    }

    @Test
    @DisplayName("fail(String) throws with provided message")
    void failWithMessageThrowsWithProvidedMessage() {
        assertThatThrownBy(() -> FailException.fail("something broke"))
                .isInstanceOf(FailException.class)
                .hasMessage("something broke");
    }

    @Test
    @DisplayName("fail(null) throws NullPointerException")
    void failWithNullThrowsNullPointerException() {
        assertThatThrownBy(() -> FailException.fail(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("fail(empty) throws IllegalArgumentException")
    void failWithEmptyThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> FailException.fail("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fail(blank) throws IllegalArgumentException")
    void failWithBlankThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> FailException.fail("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String) returns FailException with provided message")
    void ofWithMessageReturnsFailExceptionWithMessage() {
        FailException exception = FailException.of("validation failed");
        assertThat(exception).hasMessage("validation failed");
    }

    @Test
    @DisplayName("of(null) throws NullPointerException")
    void ofWithNullThrowsNullPointerException() {
        assertThatThrownBy(() -> FailException.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(empty) throws IllegalArgumentException")
    void ofWithEmptyThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> FailException.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(blank) throws IllegalArgumentException")
    void ofWithBlankThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> FailException.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}

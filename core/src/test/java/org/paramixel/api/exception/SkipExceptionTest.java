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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SkipException")
class SkipExceptionTest {

    @Test
    @DisplayName("skip() throws with default message")
    void skipNoArgsThrowsWithDefaultMessage() {
        assertThatThrownBy(() -> SkipException.skip())
                .isInstanceOf(SkipException.class)
                .hasMessage("skipped");
    }

    @Test
    @DisplayName("skip(String) throws with provided message")
    void skipWithMessageThrowsWithProvidedMessage() {
        assertThatThrownBy(() -> SkipException.skip("not available"))
                .isInstanceOf(SkipException.class)
                .hasMessage("not available");
    }

    @Test
    @DisplayName("constructor creates SkipException with provided message")
    void constructorCreatesSkipExceptionWithMessage() {
        var exception = new SkipException("database unavailable");
        assertThat(exception).hasMessage("database unavailable");
    }
}

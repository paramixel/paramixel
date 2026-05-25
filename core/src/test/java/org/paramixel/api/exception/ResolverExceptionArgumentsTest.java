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

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ResolverException arguments")
class ResolverExceptionArgumentsTest {

    @Test
    @DisplayName("constructor rejects null message")
    void constructorRejectsNullMessage() {
        assertThatThrownBy(() -> new ResolverException(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor rejects null message and null cause")
    void constructorRejectsNullMessageAndNullCause() {
        assertThatThrownBy(() -> new ResolverException(null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor rejects message with null cause")
    void constructorRejectsMessageWithNullCause() {
        var message = "test message";
        assertThatThrownBy(() -> new ResolverException(message, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor rejects null message with cause")
    void constructorRejectsNullMessageWithNonNullCause() {
        var cause = new IOException("test cause");
        assertThatThrownBy(() -> new ResolverException(null, cause)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor(String) rejects blank message")
    void constructorRejectsBlankMessage() {
        assertThatThrownBy(() -> new ResolverException("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResolverException("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("constructor(String, Throwable) rejects blank message")
    void constructorRejectsBlankMessageWithCause() {
        var cause = new IOException("test cause");
        assertThatThrownBy(() -> new ResolverException("", cause)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResolverException("   ", cause)).isInstanceOf(IllegalArgumentException.class);
    }
}

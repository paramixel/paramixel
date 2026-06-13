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

@DisplayName("PolicyException")
class PolicyExceptionTest {

    @Test
    @DisplayName("constructor with message creates exception")
    void constructorWithMessageCreatesException() {
        var exception = new PolicyException("overflow");

        assertThat(exception).hasMessage("overflow");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("constructor with message and cause creates exception")
    void constructorWithMessageAndCauseCreatesException() {
        var cause = new ArithmeticException("too big");
        var exception = new PolicyException("overflow", cause);

        assertThat(exception).hasMessage("overflow");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("constructor with message rejects null")
    void constructorWithMessageRejectsNull() {
        assertThatThrownBy(() -> new PolicyException(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("message is null");
    }

    @Test
    @DisplayName("constructor with message rejects blank")
    void constructorWithMessageRejectsBlank() {
        assertThatThrownBy(() -> new PolicyException("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("message is blank");
    }

    @Test
    @DisplayName("constructor with message and cause rejects null message")
    void constructorWithMessageAndCauseRejectsNullMessage() {
        assertThatThrownBy(() -> new PolicyException(null, new ArithmeticException("cause")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("message is null");
    }

    @Test
    @DisplayName("constructor with message and cause rejects blank message")
    void constructorWithMessageAndCauseRejectsBlankMessage() {
        assertThatThrownBy(() -> new PolicyException("  ", new ArithmeticException("cause")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("message is blank");
    }

    @Test
    @DisplayName("constructor with message and cause rejects null cause")
    void constructorWithMessageAndCauseRejectsNullCause() {
        assertThatThrownBy(() -> new PolicyException("overflow", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cause is null");
    }
}

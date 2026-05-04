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

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.exception.ResolverException;

@DisplayName("ResolverException")
class ResolverExceptionTest {

    @Test
    @DisplayName("of with message sets message")
    void ofWithMessage() {
        var message = "test message";
        var exception = ResolverException.of(message);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("of rejects null message")
    void ofRejectsNullMessage() {
        assertThatThrownBy(() -> ResolverException.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of with message and cause sets both")
    void ofWithMessageAndCause() {
        var message = "test message";
        Throwable cause = new IOException("test cause");
        var exception = ResolverException.of(message, cause);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("of rejects null message and null cause")
    void ofRejectsNullMessageAndNullCause() {
        assertThatThrownBy(() -> ResolverException.of(null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of rejects message with null cause")
    void ofRejectsMessageWithNullCause() {
        var message = "test message";
        assertThatThrownBy(() -> ResolverException.of(message, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of rejects null message with cause")
    void ofRejectsNullMessageWithNonNullCause() {
        var cause = new IOException("test cause");
        assertThatThrownBy(() -> ResolverException.of(null, cause)).isInstanceOf(NullPointerException.class);
    }
}

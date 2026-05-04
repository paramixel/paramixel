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

package org.paramixel.core.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConfigurationException")
class ConfigurationExceptionTest {

    @Test
    @DisplayName("of(String) creates exception with message")
    void ofStringCreatesExceptionWithMessage() {
        ConfigurationException exception = ConfigurationException.of("config error");
        assertThat(exception).hasMessage("config error");
    }

    @Test
    @DisplayName("of(String) rejects null")
    void ofStringRejectsNull() {
        assertThatThrownBy(() -> ConfigurationException.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String) rejects blank")
    void ofStringRejectsBlank() {
        assertThatThrownBy(() -> ConfigurationException.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConfigurationException.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String, Throwable) creates exception with message and cause")
    void ofStringThrowableCreatesExceptionWithMessageAndCause() {
        Throwable cause = new RuntimeException("root cause");
        ConfigurationException exception = ConfigurationException.of("config error", cause);
        assertThat(exception).hasMessage("config error");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("of(String, Throwable) rejects null message")
    void ofStringThrowableRejectsNullMessage() {
        assertThatThrownBy(() -> ConfigurationException.of(null, new RuntimeException()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, Throwable) rejects blank message")
    void ofStringThrowableRejectsBlankMessage() {
        assertThatThrownBy(() -> ConfigurationException.of("", new RuntimeException()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(String, Throwable) rejects null cause")
    void ofStringThrowableRejectsNullCause() {
        assertThatThrownBy(() -> ConfigurationException.of("msg", null)).isInstanceOf(NullPointerException.class);
    }
}

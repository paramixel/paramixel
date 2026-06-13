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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConfigurationException")
class ConfigurationExceptionTest {

    @Test
    @DisplayName("constructor(String) creates exception with message")
    void constructorStringCreatesExceptionWithMessage() {
        var exception = new ConfigurationException("configuration error");
        assertThat(exception).hasMessage("configuration error");
    }

    @Test
    @DisplayName("constructor(String, Throwable) creates exception with message and cause")
    void constructorStringThrowableCreatesExceptionWithMessageAndCause() {
        var cause = new RuntimeException("root cause");
        var exception = new ConfigurationException("configuration error", cause);
        assertThat(exception).hasMessage("configuration error");
        assertThat(exception).hasCauseInstanceOf(RuntimeException.class);
        assertThat(exception.getCause()).hasMessage("root cause");
    }
}

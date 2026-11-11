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

@DisplayName("AbortException")
class AbortExceptionTest {

    @Test
    @DisplayName("creates exception with message")
    void createsExceptionWithMessage() {
        var exception = new AbortException("precondition failed");
        assertThat(exception.getMessage()).isEqualTo("precondition failed");
    }

    @Test
    @DisplayName("throws with default message")
    void throwsWithDefaultMessage() {
        assertThatThrownBy(() -> {
                    throw AbortException.abort();
                })
                .isInstanceOf(AbortException.class)
                .hasMessage("aborted");
    }

    @Test
    @DisplayName("abort(String) throws with custom message")
    void abortStringThrowsWithCustomMessage() {
        assertThatThrownBy(() -> {
                    throw AbortException.abort("custom reason");
                })
                .isInstanceOf(AbortException.class)
                .hasMessage("custom reason");
    }
}

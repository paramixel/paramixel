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

@DisplayName("AbortedException arguments")
class AbortedExceptionArgumentsTest {

    @Test
    @DisplayName("rejects null message")
    void rejectsNullMessage() {
        assertThatThrownBy(() -> new AbortedException(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects blank message")
    void rejectsBlankMessage() {
        assertThatThrownBy(() -> new AbortedException("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects empty message")
    void rejectsEmptyMessage() {
        assertThatThrownBy(() -> new AbortedException("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("abort(String) rejects null message")
    void abortStringRejectsNullMessage() {
        assertThatThrownBy(() -> AbortedException.abort(null)).isInstanceOf(NullPointerException.class);
    }
}

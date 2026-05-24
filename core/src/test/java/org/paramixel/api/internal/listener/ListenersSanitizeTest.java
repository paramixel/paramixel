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

package org.paramixel.api.internal.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Listeners sanitize")
class ListenersSanitizeTest {

    @Test
    @DisplayName("returns null for null input")
    void returnsNullForNullInput() {
        assertThat(Listeners.sanitizeMessage(null)).isNull();
    }

    @Test
    @DisplayName("replaces newlines with dash separator")
    void replacesNewlinesWithDashSeparator() {
        assertThat(Listeners.sanitizeMessage("line1\nline2\rline3")).isEqualTo("line1 - line2 - line3");
    }

    @Test
    @DisplayName("strips leading whitespace from each line")
    void stripsLeadingWhitespaceFromEachLine() {
        assertThat(Listeners.sanitizeMessage("line1\n  line2")).isEqualTo("line1 - line2");
    }

    @Test
    @DisplayName("collapses multiple consecutive newlines")
    void collapsesMultipleConsecutiveNewlines() {
        assertThat(Listeners.sanitizeMessage("a\n\nb")).isEqualTo("a - b");
    }

    @Test
    @DisplayName("handles carriage return and newline")
    void handlesCarriageReturnAndNewline() {
        assertThat(Listeners.sanitizeMessage("a\r\nb")).isEqualTo("a - b");
    }

    @Test
    @DisplayName("trims leading and trailing whitespace")
    void trimsLeadingAndTrailingWhitespace() {
        assertThat(Listeners.sanitizeMessage("  hello  ")).isEqualTo("hello");
    }

    @Test
    @DisplayName("passes through normal text unchanged")
    void passesThroughNormalTextUnchanged() {
        assertThat(Listeners.sanitizeMessage("normal text")).isEqualTo("normal text");
    }

    @Test
    @DisplayName("replaces tabs with spaces")
    void replacesTabsWithSpaces() {
        assertThat(Listeners.sanitizeMessage("hello\tworld")).isEqualTo("hello world");
    }
}

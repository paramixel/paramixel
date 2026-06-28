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

package nonapi.org.paramixel.listener;

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

    @Test
    @DisplayName("strips null byte")
    void stripsNullByte() {
        assertThat(Listeners.sanitizeMessage("hello\u0000world")).isEqualTo("helloworld");
    }

    @Test
    @DisplayName("strips BEL character")
    void stripsBelCharacter() {
        assertThat(Listeners.sanitizeMessage("a\u0007b")).isEqualTo("ab");
    }

    @Test
    @DisplayName("strips backspace")
    void stripsBackspace() {
        assertThat(Listeners.sanitizeMessage("a\u0008b")).isEqualTo("ab");
    }

    @Test
    @DisplayName("strips lone ESC character")
    void stripsLoneEscapeCharacter() {
        assertThat(Listeners.sanitizeMessage("a\u001Bb")).isEqualTo("ab");
    }

    @Test
    @DisplayName("strips DEL character")
    void stripsDelCharacter() {
        assertThat(Listeners.sanitizeMessage("a\u007Fb")).isEqualTo("ab");
    }

    @Test
    @DisplayName("strips C1 control characters")
    void stripsC1ControlCharacters() {
        assertThat(Listeners.sanitizeMessage("a\u0080b\u009Fc")).isEqualTo("abc");
    }

    @Test
    @DisplayName("strips ANSI SGR sequence")
    void stripsAnsiSgrSequence() {
        assertThat(Listeners.sanitizeMessage("\u001B[31mRED\u001B[0m")).isEqualTo("RED");
    }

    @Test
    @DisplayName("strips ANSI cursor movement sequences")
    void stripsAnsiCursorMovement() {
        assertThat(Listeners.sanitizeMessage("\u001B[2J\u001B[Hhello")).isEqualTo("hello");
    }

    @Test
    @DisplayName("strips mixed control characters with text")
    void stripsMixedControlCharactersWithText() {
        assertThat(Listeners.sanitizeMessage("ok\u0000\u001B[31mbad\u0007text")).isEqualTo("okbadtext");
    }

    @Test
    @DisplayName("preserves normal diacritics")
    void preservesNormalDiacritics() {
        assertThat(Listeners.sanitizeMessage("café résumé")).isEqualTo("café résumé");
    }

    @Test
    @DisplayName("preserves Unicode emoji")
    void preservesUnicodeEmoji() {
        assertThat(Listeners.sanitizeMessage("test \uD83D\uDE00 ok")).isEqualTo("test 😀 ok");
    }

    @Test
    @DisplayName("preserves printable ASCII")
    void preservesPrintableAscii() {
        assertThat(Listeners.sanitizeMessage("hello world 123")).isEqualTo("hello world 123");
    }

    @Test
    @DisplayName("all control characters returns empty string")
    void allControlCharactersReturnsEmptyString() {
        assertThat(Listeners.sanitizeMessage("\u0000\u0001\u0007\u001B")).isEqualTo("");
    }

    @Test
    @DisplayName("strips ANSI sequence with semicolon-separated parameters")
    void stripsAnsiSequenceWithSemicolons() {
        assertThat(Listeners.sanitizeMessage("\u001B[1;31;44mBOLD RED ON BLUE\u001B[0m"))
                .isEqualTo("BOLD RED ON BLUE");
    }

    @Test
    @DisplayName("strips vertical tab and form feed")
    void stripsVerticalTabAndFormFeed() {
        assertThat(Listeners.sanitizeMessage("a\u000Bb\u000Cc")).isEqualTo("abc");
    }

    @Test
    @DisplayName("stripUnsafe returns null for null input")
    void stripUnsafeReturnsNullForNullInput() {
        assertThat(Listeners.stripUnsafe(null)).isNull();
    }

    @Test
    @DisplayName("stripUnsafe strips null byte")
    void stripUnsafeStripsNullByte() {
        assertThat(Listeners.stripUnsafe("hello\u0000world")).isEqualTo("helloworld");
    }

    @Test
    @DisplayName("stripUnsafe strips BEL")
    void stripUnsafeStripsBel() {
        assertThat(Listeners.stripUnsafe("a\u0007b")).isEqualTo("ab");
    }

    @Test
    @DisplayName("stripUnsafe strips DEL")
    void stripUnsafeStripsDel() {
        assertThat(Listeners.stripUnsafe("a\u007Fb")).isEqualTo("ab");
    }

    @Test
    @DisplayName("stripUnsafe strips C1 control characters")
    void stripUnsafeStripsC1ControlCharacters() {
        assertThat(Listeners.stripUnsafe("a\u0080b\u009Fc")).isEqualTo("abc");
    }

    @Test
    @DisplayName("stripUnsafe strips ANSI SGR sequence")
    void stripUnsafeStripsAnsiSgrSequence() {
        assertThat(Listeners.stripUnsafe("\u001B[31mRED\u001B[0m")).isEqualTo("RED");
    }

    @Test
    @DisplayName("stripUnsafe strips ANSI cursor movement sequences")
    void stripUnsafeStripsAnsiCursorMovement() {
        assertThat(Listeners.stripUnsafe("\u001B[2J\u001B[Hhello")).isEqualTo("hello");
    }

    @Test
    @DisplayName("stripUnsafe preserves tab")
    void stripUnsafePreservesTab() {
        assertThat(Listeners.stripUnsafe("\tat com.example.Foo.bar(Foo.java:10)"))
                .isEqualTo("\tat com.example.Foo.bar(Foo.java:10)");
    }

    @Test
    @DisplayName("stripUnsafe preserves newlines and carriage returns")
    void stripUnsafePreservesNewlinesAndCarriageReturns() {
        assertThat(Listeners.stripUnsafe("line1\r\nline2")).isEqualTo("line1\r\nline2");
    }

    @Test
    @DisplayName("stripUnsafe preserves multiple spaces and does not trim")
    void stripUnsafePreservesMultipleSpacesAndDoesNotTrim() {
        assertThat(Listeners.stripUnsafe("  spaced   out  ")).isEqualTo("  spaced   out  ");
    }

    @Test
    @DisplayName("stripUnsafe preserves diacritics and emoji")
    void stripUnsafePreservesDiacriticsAndEmoji() {
        assertThat(Listeners.stripUnsafe("café \uD83D\uDE00 résumé")).isEqualTo("café \uD83D\uDE00 résumé");
    }

    @Test
    @DisplayName("stripUnsafe strips mixed control characters with text but keeps tab")
    void stripUnsafeStripsMixedControlCharsButKeepsTab() {
        assertThat(Listeners.stripUnsafe("\tat\u0000\u001B[31mx\u0007y")).isEqualTo("\tatxy");
    }
}

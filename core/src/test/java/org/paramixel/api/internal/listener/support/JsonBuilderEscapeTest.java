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

package org.paramixel.api.internal.listener.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.internal.listener.support.JsonBuilderTestSupport.buildHtmlScriptJson;
import static org.paramixel.api.internal.listener.support.JsonBuilderTestSupport.buildJson;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonBuilder escape")
class JsonBuilderEscapeTest {

    @Test
    @DisplayName("escapes double quote")
    void escapesDoubleQuote() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\"b"))).isEqualTo("\"a\\\"b\"");
    }

    @Test
    @DisplayName("escapes backslash")
    void escapesBackslash() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\\b"))).isEqualTo("\"a\\\\b\"");
    }

    @Test
    @DisplayName("escapes newline")
    void escapesNewline() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\nb"))).isEqualTo("\"a\\nb\"");
    }

    @Test
    @DisplayName("escapes carriage return")
    void escapesCarriageReturn() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\rb"))).isEqualTo("\"a\\rb\"");
    }

    @Test
    @DisplayName("escapes tab")
    void escapesTab() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\tb"))).isEqualTo("\"a\\tb\"");
    }

    @Test
    @DisplayName("escapes backspace")
    void escapesBackspace() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\bb"))).isEqualTo("\"a\\bb\"");
    }

    @Test
    @DisplayName("escapes form feed")
    void escapesFormFeed() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\fb"))).isEqualTo("\"a\\fb\"");
    }

    @Test
    @DisplayName("escapes Unicode line separator U+2028")
    void escapesLineSeparator() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u2028b"))).isEqualTo("\"a\\u2028b\"");
    }

    @Test
    @DisplayName("escapes Unicode paragraph separator U+2029")
    void escapesParagraphSeparator() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u2029b"))).isEqualTo("\"a\\u2029b\"");
    }

    @Test
    @DisplayName("escapes null character U+0000")
    void escapesNullChar() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0000b"))).isEqualTo("\"a\\u0000b\"");
    }

    @Test
    @DisplayName("escapes U+0001")
    void escapes0001() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0001b"))).isEqualTo("\"a\\u0001b\"");
    }

    @Test
    @DisplayName("escapes U+0002")
    void escapes0002() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0002b"))).isEqualTo("\"a\\u0002b\"");
    }

    @Test
    @DisplayName("escapes U+0003")
    void escapes0003() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0003b"))).isEqualTo("\"a\\u0003b\"");
    }

    @Test
    @DisplayName("escapes U+0004")
    void escapes0004() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0004b"))).isEqualTo("\"a\\u0004b\"");
    }

    @Test
    @DisplayName("escapes U+0005")
    void escapes0005() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0005b"))).isEqualTo("\"a\\u0005b\"");
    }

    @Test
    @DisplayName("escapes U+0006")
    void escapes0006() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0006b"))).isEqualTo("\"a\\u0006b\"");
    }

    @Test
    @DisplayName("escapes BEL U+0007")
    void escapesBel() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0007b"))).isEqualTo("\"a\\u0007b\"");
    }

    @Test
    @DisplayName("escapes U+000E")
    void escapes000E() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u000Eb"))).isEqualTo("\"a\\u000eb\"");
    }

    @Test
    @DisplayName("escapes U+000F")
    void escapes000F() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u000Fb"))).isEqualTo("\"a\\u000fb\"");
    }

    @Test
    @DisplayName("escapes U+0010")
    void escapes0010() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0010b"))).isEqualTo("\"a\\u0010b\"");
    }

    @Test
    @DisplayName("escapes U+0011")
    void escapes0011() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0011b"))).isEqualTo("\"a\\u0011b\"");
    }

    @Test
    @DisplayName("escapes U+0012")
    void escapes0012() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0012b"))).isEqualTo("\"a\\u0012b\"");
    }

    @Test
    @DisplayName("escapes U+0013")
    void escapes0013() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0013b"))).isEqualTo("\"a\\u0013b\"");
    }

    @Test
    @DisplayName("escapes U+0014")
    void escapes0014() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0014b"))).isEqualTo("\"a\\u0014b\"");
    }

    @Test
    @DisplayName("escapes U+0015")
    void escapes0015() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0015b"))).isEqualTo("\"a\\u0015b\"");
    }

    @Test
    @DisplayName("escapes U+0016")
    void escapes0016() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0016b"))).isEqualTo("\"a\\u0016b\"");
    }

    @Test
    @DisplayName("escapes U+0017")
    void escapes0017() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0017b"))).isEqualTo("\"a\\u0017b\"");
    }

    @Test
    @DisplayName("escapes U+0018")
    void escapes0018() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0018b"))).isEqualTo("\"a\\u0018b\"");
    }

    @Test
    @DisplayName("escapes U+0019")
    void escapes0019() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u0019b"))).isEqualTo("\"a\\u0019b\"");
    }

    @Test
    @DisplayName("escapes U+001A")
    void escapes001A() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u001Ab"))).isEqualTo("\"a\\u001ab\"");
    }

    @Test
    @DisplayName("escapes ESC U+001B")
    void escapesEsc() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u001Bb"))).isEqualTo("\"a\\u001bb\"");
    }

    @Test
    @DisplayName("escapes U+001C")
    void escapes001C() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u001Cb"))).isEqualTo("\"a\\u001cb\"");
    }

    @Test
    @DisplayName("escapes U+001D")
    void escapes001D() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u001Db"))).isEqualTo("\"a\\u001db\"");
    }

    @Test
    @DisplayName("escapes U+001E")
    void escapes001E() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u001Eb"))).isEqualTo("\"a\\u001eb\"");
    }

    @Test
    @DisplayName("escapes U+001F")
    void escapes001F() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\u001Fb"))).isEqualTo("\"a\\u001fb\"");
    }

    @Test
    @DisplayName("escapes control char after regular chars flushes buffer first")
    void controlCharAfterRegularCharsFlushesBuffer() throws IOException {
        String padding = "a".repeat(100);
        assertThat(buildJson(b -> b.writeString(padding + "\u0001"))).isEqualTo("\"" + padding + "\\u0001\"");
    }

    @Test
    @DisplayName("escapes </ when escapeForHtmlScript is true")
    void escapesClosingScriptTag() throws IOException {
        assertThat(buildHtmlScriptJson(b -> b.writeString("before</script>after")))
                .isEqualTo("\"before<\\/script>after\"");
    }

    @Test
    @DisplayName("does not escape < followed by non-slash when escapeForHtmlScript is true")
    void doesNotEscapeLessThanNotFollowedBySlash() throws IOException {
        assertThat(buildHtmlScriptJson(b -> b.writeString("a<b"))).isEqualTo("\"a<b\"");
    }

    @Test
    @DisplayName("does not escape < at end of string when escapeForHtmlScript is true")
    void doesNotEscapeLessThanAtEndOfString() throws IOException {
        assertThat(buildHtmlScriptJson(b -> b.writeString("a<"))).isEqualTo("\"a<\"");
    }

    @Test
    @DisplayName("does not escape </ when escapeForHtmlScript is false")
    void doesNotEscapeWhenHtmlScriptFalse() throws IOException {
        assertThat(buildJson(b -> b.writeString("a</b"))).isEqualTo("\"a</b\"");
    }

    @Test
    @DisplayName("escapes multiple </ sequences")
    void escapesMultipleClosingScriptTags() throws IOException {
        assertThat(buildHtmlScriptJson(b -> b.writeString("</a</b"))).isEqualTo("\"<\\/a<\\/b\"");
    }

    @Test
    @DisplayName("flushes buffer before </ escape")
    void flushesBufferBeforeScriptTagEscape() throws IOException {
        String padding = "x".repeat(100);
        assertThat(buildHtmlScriptJson(b -> b.writeString(padding + "</"))).isEqualTo("\"" + padding + "<\\/\"");
    }

    @Test
    @DisplayName("flushes default-branch buffer at 512 regular chars")
    void flushesDefaultBranchBuffer() throws IOException {
        String padding = "x".repeat(512);
        String value = padding + "y";
        assertThat(buildJson(b -> b.writeString(value))).isEqualTo("\"" + value + "\"");
    }

    @Test
    @DisplayName("flushes < else-branch buffer at 512 chars with escapeForHtmlScript true")
    void flushesLessThanBranchBuffer() throws IOException {
        String padding = "x".repeat(511);
        String value = padding + "<";
        assertThat(buildHtmlScriptJson(b -> b.writeString(value))).isEqualTo("\"" + value + "\"");
    }

    @Test
    @DisplayName("flushes < else-branch buffer at 512 chars with escapeForHtmlScript false")
    void flushesLessThanBranchBufferNoHtmlScript() throws IOException {
        String padding = "x".repeat(511);
        String value = padding + "<";
        assertThat(buildJson(b -> b.writeString(value))).isEqualTo("\"" + value + "\"");
    }

    @Test
    @DisplayName("empty buffer at end after escape flushes zero chars")
    void emptyBufferAtEndAfterEscape() throws IOException {
        assertThat(buildJson(b -> b.writeString("\n"))).isEqualTo("\"\\n\"");
    }

    @Test
    @DisplayName("single regular char writes to buffer and flushes at end")
    void singleRegularCharFlushesAtEnd() throws IOException {
        assertThat(buildJson(b -> b.writeString("a"))).isEqualTo("\"a\"");
    }

    @Test
    @DisplayName("empty string produces empty quoted value")
    void emptyStringProducesEmptyQuotedValue() throws IOException {
        assertThat(buildJson(b -> b.writeString(""))).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("multiple escape types in one string")
    void multipleEscapeTypes() throws IOException {
        assertThat(buildJson(b -> b.writeString("a\tb\nc\u0001d"))).isEqualTo("\"a\\tb\\nc\\u0001d\"");
    }

    @Test
    @DisplayName("escape at string start flushes empty buffer")
    void escapeAtStringStart() throws IOException {
        assertThat(buildJson(b -> b.writeString("\nhello"))).isEqualTo("\"\\nhello\"");
    }

    @Test
    @DisplayName("consecutive escapes")
    void consecutiveEscapes() throws IOException {
        assertThat(buildJson(b -> b.writeString("\n\r\t"))).isEqualTo("\"\\n\\r\\t\"");
    }
}

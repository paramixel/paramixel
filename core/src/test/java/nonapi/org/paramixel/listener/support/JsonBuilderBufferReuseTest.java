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

package nonapi.org.paramixel.listener.support;

import static nonapi.org.paramixel.listener.support.JsonBuilderTestSupport.buildJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonBuilder buffer reuse")
class JsonBuilderBufferReuseTest {

    @Test
    @DisplayName("buffer reuse across multiple writeString calls produces correct output")
    void bufferReuseAcrossMultipleCalls() throws IOException {
        String result = buildJson(b -> {
            b.writeString("first");
            b.writeString("second");
            b.writeString("third");
        });
        assertThat(result).isEqualTo("\"first\",\"second\",\"third\"");
    }

    @Test
    @DisplayName("buffer reuse with mixed escape sequences")
    void bufferReuseWithMixedEscapes() throws IOException {
        String result = buildJson(b -> {
            b.writeString("a\"b");
            b.writeString("c\\d");
            b.writeString("e\nf");
        });
        assertThat(result).isEqualTo("\"a\\\"b\",\"c\\\\d\",\"e\\nf\"");
    }

    @Test
    @DisplayName("buffer reuse with strings exceeding buffer size")
    void bufferReuseWithStringsExceedingBufferSize() throws IOException {
        String longString = "x".repeat(600);
        String result = buildJson(b -> b.writeString(longString));
        assertThat(result).isEqualTo("\"" + longString + "\"");
    }

    @Test
    @DisplayName("buffer reuse with alternating short and long strings")
    void bufferReuseWithAlternatingShortAndLong() throws IOException {
        String shortString = "a";
        String longString = "y".repeat(600);
        String result = buildJson(b -> {
            b.writeString(shortString);
            b.writeString(longString);
            b.writeString(shortString);
            b.writeString(longString);
        });
        assertThat(result)
                .isEqualTo("\"" + shortString + "\",\"" + longString + "\",\"" + shortString + "\",\"" + longString
                        + "\"");
    }

    @Test
    @DisplayName("buffer reuse with control characters")
    void bufferReuseWithControlCharacters() throws IOException {
        String result = buildJson(b -> {
            b.writeString("\u0001\u0002\u0003");
            b.writeString("normal");
            b.writeString("\u001F\u001E");
        });
        assertThat(result).isEqualTo("\"\\u0001\\u0002\\u0003\",\"normal\",\"\\u001f\\u001e\"");
    }

    @Test
    @DisplayName("buffer reuse with escapeForHtmlScript enabled")
    void bufferReuseWithHtmlScriptEscape() throws IOException {
        String result = nonapi.org.paramixel.listener.support.JsonBuilderTestSupport.buildHtmlScriptJson(b -> {
            b.writeString("</script>");
            b.writeString("normal");
            b.writeString("</html>");
        });
        assertThat(result).isEqualTo("\"<\\/script>\",\"normal\",\"<\\/html>\"");
    }
}

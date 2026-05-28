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

@DisplayName("JsonBuilder structure")
class JsonBuilderStructureTest {

    @Test
    @DisplayName("empty object")
    void emptyObject() throws IOException {
        assertThat(buildJson(b -> {
                    b.beginObject();
                    b.endObject();
                }))
                .isEqualTo("{}");
    }

    @Test
    @DisplayName("object with content on new line")
    void objectWithContentOnNewLine() throws IOException {
        String result = buildJson(b -> {
            b.beginObjectOnNewLine();
            b.stringField("key", "value");
            b.endObject();
        });
        assertThat(result).contains("\"key\": \"value\"");
    }

    @Test
    @DisplayName("endObject with needsComma true writes newline and indent")
    void endObjectWithNeedsComma() throws IOException {
        String result = buildJson(b -> {
            b.beginObjectOnNewLine();
            b.stringField("k", "v");
            b.endObject();
        });
        assertThat(result).endsWith("}");
        assertThat(result).contains("\n");
    }

    @Test
    @DisplayName("endObject with needsComma false skips newline")
    void endObjectWithoutNeedsComma() throws IOException {
        String result = buildJson(b -> {
            b.beginObject();
            b.endObject();
        });
        assertThat(result).isEqualTo("{}");
    }

    @Test
    @DisplayName("empty array")
    void emptyArray() throws IOException {
        assertThat(buildJson(b -> {
                    b.beginArray();
                    b.endArray();
                }))
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("array on new line with items")
    void arrayOnNewLineWithItems() throws IOException {
        String result = buildJson(b -> {
            b.beginArrayOnNewLine();
            b.writeString("item");
            b.endArray();
        });
        assertThat(result).contains("\"item\"");
    }

    @Test
    @DisplayName("endArray with needsComma true writes newline and indent")
    void endArrayWithNeedsComma() throws IOException {
        String result = buildJson(b -> {
            b.beginArrayOnNewLine();
            b.writeString("x");
            b.endArray();
        });
        assertThat(result).endsWith("]");
    }

    @Test
    @DisplayName("endArray with needsComma false skips newline")
    void endArrayWithoutNeedsComma() throws IOException {
        assertThat(buildJson(b -> {
                    b.beginArray();
                    b.endArray();
                }))
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("stringField with null value writes null")
    void stringFieldWithNull() throws IOException {
        String result = buildJson(b -> {
            b.beginObjectOnNewLine();
            b.stringField("key", null);
            b.endObject();
        });
        assertThat(result).contains("\"key\": null");
    }

    @Test
    @DisplayName("stringField with value writes quoted string")
    void stringFieldWithValue() throws IOException {
        String result = buildJson(b -> {
            b.beginObjectOnNewLine();
            b.stringField("key", "value");
            b.endObject();
        });
        assertThat(result).contains("\"key\": \"value\"");
    }

    @Test
    @DisplayName("numberField writes numeric value")
    void numberField() throws IOException {
        String result = buildJson(b -> {
            b.beginObjectOnNewLine();
            b.numberField("count", 42);
            b.endObject();
        });
        assertThat(result).contains("\"count\": 42");
    }

    @Test
    @DisplayName("rawField writes raw value without escaping")
    void rawField() throws IOException {
        String result = buildJson(b -> {
            b.beginObjectOnNewLine();
            b.rawField("data", "true");
            b.endObject();
        });
        assertThat(result).contains("\"data\": true");
    }

    @Test
    @DisplayName("beginObjectField writes named object field")
    void beginObjectField() throws IOException {
        String result = buildJson(b -> {
            b.beginObjectOnNewLine();
            b.beginObjectField("inner");
            b.endObject();
            b.endObject();
        });
        assertThat(result).contains("\"inner\"");
    }

    @Test
    @DisplayName("beginArrayField writes named array field")
    void beginArrayField() throws IOException {
        String result = buildJson(b -> {
            b.beginObjectOnNewLine();
            b.beginArrayField("items");
            b.endArray();
            b.endObject();
        });
        assertThat(result).contains("\"items\"");
    }

    @Test
    @DisplayName("writeString with null writes null token")
    void writeStringNull() throws IOException {
        assertThat(buildJson(b -> b.writeString(null))).isEqualTo("null");
    }

    @Test
    @DisplayName("writeString with value writes quoted string with comma")
    void writeStringWithValue() throws IOException {
        String result = buildJson(b -> {
            b.writeString("first");
            b.writeString("second");
        });
        assertThat(result).contains("\"first\"");
        assertThat(result).contains(",");
        assertThat(result).contains("\"second\"");
    }

    @Test
    @DisplayName("writeStringInline writes quoted string without comma")
    void writeStringInline() throws IOException {
        assertThat(buildJson(b -> b.writeStringInline("inline"))).isEqualTo("\"inline\"");
    }

    @Test
    @DisplayName("writeRaw writes value without quotes or escaping")
    void writeRaw() throws IOException {
        assertThat(buildJson(b -> b.writeRaw("42"))).isEqualTo("42");
    }

    @Test
    @DisplayName("no comma before first field")
    void noCommaBeforeFirstField() throws IOException {
        String result = buildJson(b -> {
            b.beginObjectOnNewLine();
            b.stringField("a", "1");
            b.endObject();
        });
        assertThat(result).doesNotStartWith(",");
    }

    @Test
    @DisplayName("comma between two fields")
    void commaBetweenTwoFields() throws IOException {
        String result = buildJson(b -> {
            b.beginObjectOnNewLine();
            b.stringField("a", "1");
            b.stringField("b", "2");
            b.endObject();
        });
        assertThat(result).contains(",");
    }

    @Test
    @DisplayName("nested object with indentation")
    void nestedObject() throws IOException {
        String result = buildJson(b -> {
            b.beginObjectOnNewLine();
            b.beginObjectField("inner");
            b.stringField("key", "val");
            b.endObject();
            b.endObject();
        });
        assertThat(result).contains("\"inner\"");
        assertThat(result).contains("\"key\": \"val\"");
    }

    @Test
    @DisplayName("nested array with indentation")
    void nestedArray() throws IOException {
        String result = buildJson(b -> {
            b.beginObjectOnNewLine();
            b.beginArrayField("items");
            b.writeString("x");
            b.endArray();
            b.endObject();
        });
        assertThat(result).contains("\"items\"");
        assertThat(result).contains("\"x\"");
    }

    @Test
    @DisplayName("default constructor uses escapeForHtmlScript false")
    void defaultConstructor() throws IOException {
        assertThat(buildJson(b -> b.writeString("</script>"))).isEqualTo("\"</script>\"");
    }
}

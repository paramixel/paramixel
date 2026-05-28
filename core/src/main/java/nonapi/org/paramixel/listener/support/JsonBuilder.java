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

import java.io.IOException;
import java.io.Writer;

/**
 * A minimal JSON builder that writes well-formed JSON to a {@link Writer} with proper escaping and indentation.
 *
 * <p>This builder manages commas, indentation, and character escaping internally so that callers do not need to
 * handle these details. It supports objects, arrays, strings, numbers, booleans, and null values.
 *
 * <p><strong>Buffering strategy:</strong> {@link #writeJsonString(String)} accumulates non-special characters into a
 * per-call {@code char[]} buffer and flushes in bulk at escape points or when the buffer fills, reducing per-character
 * write overhead compared to individual {@code writer.write(int)} calls.
 *
 * <p><strong>Thread safety:</strong> This class is <em>not</em> thread-safe. It is intended for single-threaded use;
 * each {@link JsonBuilder} instance should be confined to a single thread.
 *
 * <p><strong>HTML script escaping:</strong> When {@code escapeForHtmlScript} is {@code true}, {@code </} sequences
 * within string values are escaped as {@code <\\/} to prevent premature termination of an HTML {@code <script>}
 * element. This is required when embedding JSON output inside HTML script tags.
 */
public final class JsonBuilder {

    private static final String INDENT = "  ";

    private static final int STRING_BUFFER_SIZE = 512;

    private static final String[] CONTROL_CHAR_ESCAPES = new String[32];

    static {
        for (int i = 0; i < 32; i++) {
            CONTROL_CHAR_ESCAPES[i] = "\\u%04x".formatted(i);
        }
    }

    private final Writer writer;
    private final boolean escapeForHtmlScript;
    private int indentLevel;
    private boolean needsComma;

    /**
     * Creates a JSON builder that writes to the supplied writer with standard JSON escaping.
     *
     * @param writer the writer to output JSON to
     */
    public JsonBuilder(final Writer writer) {
        this(writer, false);
    }

    /**
     * Creates a JSON builder that writes to the supplied writer.
     *
     * @param writer the writer to output JSON to
     * @param escapeForHtmlScript when true, additionally escapes {@code </} sequences for safe embedding in HTML
     *     {@code <script>} elements
     */
    public JsonBuilder(final Writer writer, final boolean escapeForHtmlScript) {
        this.writer = writer;
        this.escapeForHtmlScript = escapeForHtmlScript;
        this.indentLevel = 0;
        this.needsComma = false;
    }

    /**
     * Begins a JSON object at the current position, writing an opening brace.
     *
     * @throws IOException if writing fails
     */
    public void beginObject() throws IOException {
        writeCommaIfNeeded();
        writer.write("{");
        indentLevel++;
        needsComma = false;
    }

    /**
     * Begins a JSON object on a new indented line, writing a newline, indent, and opening brace.
     *
     * @throws IOException if writing fails
     */
    public void beginObjectOnNewLine() throws IOException {
        writeCommaIfNeeded();
        writer.write("\n");
        writeIndent();
        writer.write("{");
        indentLevel++;
        needsComma = false;
    }

    /**
     * Ends the current JSON object, writing a newline, indent, and closing brace.
     *
     * @throws IOException if writing fails
     */
    public void endObject() throws IOException {
        indentLevel--;
        if (needsComma) {
            writer.write("\n");
            writeIndent();
        }
        writer.write("}");
        needsComma = true;
    }

    /**
     * Begins a JSON array at the current position, writing an opening bracket.
     *
     * @throws IOException if writing fails
     */
    public void beginArray() throws IOException {
        writeCommaIfNeeded();
        writer.write("[");
        indentLevel++;
        needsComma = false;
    }

    /**
     * Begins a JSON array on a new indented line, writing an opening bracket followed by a newline.
     *
     * @throws IOException if writing fails
     */
    public void beginArrayOnNewLine() throws IOException {
        writeCommaIfNeeded();
        writer.write("[\n");
        indentLevel++;
        needsComma = false;
    }

    /**
     * Ends the current JSON array, writing a newline, indent, and closing bracket.
     *
     * @throws IOException if writing fails
     */
    public void endArray() throws IOException {
        indentLevel--;
        if (needsComma) {
            writer.write("\n");
            writeIndent();
        }
        writer.write("]");
        needsComma = true;
    }

    /**
     * Begins a named JSON object field, writing a newline, indent, quoted name, colon, and opening brace.
     *
     * @param name the field name
     * @throws IOException if writing fails
     */
    public void beginObjectField(final String name) throws IOException {
        writeCommaIfNeeded();
        writer.write("\n");
        writeIndent();
        writeJsonString(name);
        writer.write(": {\n");
        indentLevel++;
        needsComma = false;
    }

    /**
     * Begins a named JSON array field, writing a newline, indent, quoted name, colon, and opening bracket.
     *
     * @param name the field name
     * @throws IOException if writing fails
     */
    public void beginArrayField(final String name) throws IOException {
        writeCommaIfNeeded();
        writer.write("\n");
        writeIndent();
        writeJsonString(name);
        writer.write(": [\n");
        indentLevel++;
        needsComma = false;
    }

    /**
     * Writes a named string field with proper JSON escaping. A {@code null} value produces a JSON
     * {@code null} token.
     *
     * @param name the field name
     * @param value the field value, or {@code null}
     * @throws IOException if writing fails
     */
    public void stringField(final String name, final String value) throws IOException {
        writeCommaIfNeeded();
        writer.write("\n");
        writeIndent();
        writeJsonString(name);
        writer.write(": ");
        if (value == null) {
            writer.write("null");
        } else {
            writeJsonString(value);
        }
        needsComma = true;
    }

    /**
     * Writes a named numeric field with the given long value.
     *
     * @param name the field name
     * @param value the numeric value
     * @throws IOException if writing fails
     */
    public void numberField(final String name, final long value) throws IOException {
        writeCommaIfNeeded();
        writer.write("\n");
        writeIndent();
        writeJsonString(name);
        writer.write(": ");
        writer.write(String.valueOf(value));
        needsComma = true;
    }

    /**
     * Writes a named field with a raw (unquoted, unescaped) value. Use this for JSON literals,
     * pre-formatted numbers, or nested structures.
     *
     * @param name the field name
     * @param rawValue the raw JSON value to write without escaping
     * @throws IOException if writing fails
     */
    public void rawField(final String name, final String rawValue) throws IOException {
        writeCommaIfNeeded();
        writer.write("\n");
        writeIndent();
        writeJsonString(name);
        writer.write(": ");
        writer.write(rawValue);
        needsComma = true;
    }

    /**
     * Writes a JSON string value with proper escaping. A {@code null} value produces a JSON
     * {@code null} token.
     *
     * @param value the string value, or {@code null}
     * @throws IOException if writing fails
     */
    public void writeString(final String value) throws IOException {
        writeCommaIfNeeded();
        if (value == null) {
            writer.write("null");
        } else {
            writeJsonString(value);
        }
        needsComma = true;
    }

    /**
     * Writes a raw JSON value without escaping, quotes, or comma management.
     *
     * @param value the raw JSON text to write
     * @throws IOException if writing fails
     */
    public void writeRaw(final String value) throws IOException {
        writeCommaIfNeeded();
        writer.write(value);
        needsComma = true;
    }

    /**
     * Writes a JSON string value with proper escaping, without comma or newline management.
     * Intended for inline use within composite structures.
     *
     * @param value the string value; must not be {@code null}
     * @throws IOException if writing fails
     */
    public void writeStringInline(final String value) throws IOException {
        writeJsonString(value);
    }

    private void writeCommaIfNeeded() throws IOException {
        if (needsComma) {
            writer.write(",");
        }
    }

    private void writeIndent() throws IOException {
        for (int i = 0; i < indentLevel; i++) {
            writer.write(INDENT);
        }
    }

    /**
     * Writes a JSON-escaped string surrounded by double quotes.
     *
     * <p>Escaping rules:
     * <ul>
     *   <li>The eight JSON-mandated escapes: {@code "}, {@code \}, {@code \n}, {@code \r}, {@code \t}, {@code \b},
     *       {@code \f}, and the line separators U+2028 and U+2029.</li>
     *   <li>Control characters (U+0000–U+001F) are escaped as Unicode escapes ({@code \\uXXXX}) using a pre-computed
     *       lookup table.</li>
     *   <li>When {@code escapeForHtmlScript} is enabled, the sequence {@code </} is escaped as {@code <\\/} to prevent
     *       premature {@code </script>} termination.</li>
     * </ul>
     *
     * <p>Non-special characters are accumulated into a per-call buffer and flushed in bulk, reducing per-character
     * write overhead.
     *
     * @param value the string value to write; must not be {@code null}
     * @throws IOException if writing fails
     */
    private void writeJsonString(final String value) throws IOException {
        writer.write('"');
        char[] buffer = new char[STRING_BUFFER_SIZE];
        int bufferPos = 0;
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> {
                    writer.write(buffer, 0, bufferPos);
                    bufferPos = 0;
                    writer.write("\\\"");
                }
                case '\\' -> {
                    writer.write(buffer, 0, bufferPos);
                    bufferPos = 0;
                    writer.write("\\\\");
                }
                case '\n' -> {
                    writer.write(buffer, 0, bufferPos);
                    bufferPos = 0;
                    writer.write("\\n");
                }
                case '\r' -> {
                    writer.write(buffer, 0, bufferPos);
                    bufferPos = 0;
                    writer.write("\\r");
                }
                case '\t' -> {
                    writer.write(buffer, 0, bufferPos);
                    bufferPos = 0;
                    writer.write("\\t");
                }
                case '\b' -> {
                    writer.write(buffer, 0, bufferPos);
                    bufferPos = 0;
                    writer.write("\\b");
                }
                case '\f' -> {
                    writer.write(buffer, 0, bufferPos);
                    bufferPos = 0;
                    writer.write("\\f");
                }
                case '\u2028' -> {
                    writer.write(buffer, 0, bufferPos);
                    bufferPos = 0;
                    writer.write("\\u2028");
                }
                case '\u2029' -> {
                    writer.write(buffer, 0, bufferPos);
                    bufferPos = 0;
                    writer.write("\\u2029");
                }
                case '<' -> {
                    if (escapeForHtmlScript && i + 1 < value.length() && value.charAt(i + 1) == '/') {
                        writer.write(buffer, 0, bufferPos);
                        bufferPos = 0;
                        writer.write("<\\/");
                        i += 2;
                        continue;
                    } else {
                        buffer[bufferPos++] = c;
                        if (bufferPos == buffer.length) {
                            writer.write(buffer, 0, bufferPos);
                            bufferPos = 0;
                        }
                    }
                }
                default -> {
                    if (c < ' ') {
                        writer.write(buffer, 0, bufferPos);
                        bufferPos = 0;
                        writer.write(CONTROL_CHAR_ESCAPES[c]);
                    } else {
                        buffer[bufferPos++] = c;
                        if (bufferPos == buffer.length) {
                            writer.write(buffer, 0, bufferPos);
                            bufferPos = 0;
                        }
                    }
                }
            }
            i++;
        }
        writer.write(buffer, 0, bufferPos);
        writer.write('"');
    }
}

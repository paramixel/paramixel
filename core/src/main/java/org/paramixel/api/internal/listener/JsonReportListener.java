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

import java.io.IOException;
import java.io.Writer;
import org.paramixel.api.Result;
import org.paramixel.api.action.Descriptor;

/**
 * Writes a JSON descriptor report.
 */
public final class JsonReportListener extends AbstractReportFileListener {

    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /**
     * Creates a JSON report listener.
     *
     * @param reportFile the output file path
     */
    public JsonReportListener(final String reportFile) {
        super(reportFile);
    }

    @Override
    protected void writeReport(final Writer writer, final Result result) throws IOException {
        if (result.descriptor().isEmpty()) {
            writer.write("{\"root\":null}\n");
            return;
        }
        writeDescriptor(writer, result.descriptor().orElseThrow());
        writer.write(LINE_SEPARATOR);
    }

    @Override
    protected String formatName() {
        return "json";
    }

    private static void writeDescriptor(final Writer writer, final Descriptor descriptor) throws IOException {
        writer.write("{\"id\":\"");
        writer.write(escape(descriptor.metadata().id()));
        writer.write("\",\"name\":\"");
        writer.write(escape(descriptor.metadata().name()));
        writer.write("\",\"status\":\"");
        writer.write(descriptor.metadata().status().name());
        writer.write("\",\"children\":[");
        for (int i = 0; i < descriptor.children().size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writeDescriptor(writer, descriptor.children().get(i));
        }
        writer.write("]}");
    }

    private static String escape(final String value) {
        final var firstEscapedCharacter = firstEscapedCharacter(value);
        if (firstEscapedCharacter == -1) {
            return value;
        }
        var escaped = new StringBuilder(value.length() + 16);
        escaped.append(value, 0, firstEscapedCharacter);
        for (var i = firstEscapedCharacter; i < value.length(); i++) {
            final var c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                default -> {
                    if (c < ' ') {
                        appendUnicodeEscape(escaped, c);
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static int firstEscapedCharacter(final String value) {
        for (var i = 0; i < value.length(); i++) {
            final var c = value.charAt(i);
            if (c == '\\' || c == '"' || c == '\n' || c == '\r' || c == '\t' || c == '\b' || c == '\f' || c < ' ') {
                return i;
            }
        }
        return -1;
    }

    private static void appendUnicodeEscape(final StringBuilder escaped, final char c) {
        escaped.append("\\u")
                .append(HEX_DIGITS[(c >>> 12) & 0xF])
                .append(HEX_DIGITS[(c >>> 8) & 0xF])
                .append(HEX_DIGITS[(c >>> 4) & 0xF])
                .append(HEX_DIGITS[c & 0xF]);
    }
}

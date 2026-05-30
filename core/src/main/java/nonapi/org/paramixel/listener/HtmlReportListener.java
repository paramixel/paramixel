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

import java.io.IOException;
import java.io.Writer;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Result;

/**
 * Writes an HTML descriptor report.
 */
public final class HtmlReportListener extends AbstractReportFileListener {

    private static final String INDENT = "  ";
    private static final String LINE_SEPARATOR = System.lineSeparator();

    /**
     * Creates an HTML report listener.
     */
    public HtmlReportListener() {}

    @Override
    protected void writeReport(final Writer writer, final Result result) throws IOException {
        writer.write("<!DOCTYPE html><html><body><pre>");
        if (result.descriptor().isEmpty()) {
            writer.write("No Paramixel tests found");
        } else {
            writeDescriptor(writer, result.descriptor().orElseThrow(), "");
        }
        writer.write("</pre></body></html>");
    }

    @Override
    protected String formatName() {
        return "html";
    }

    private static void writeDescriptor(final Writer writer, final Descriptor descriptor, final String indent)
            throws IOException {
        writer.write(indent);
        writer.write(escape(descriptor.metadata().status().name()));
        writer.write(" | ");
        writer.write(escape(descriptor.metadata().name()));
        writer.write(LINE_SEPARATOR);
        final var childIndent = indent + INDENT;
        if (descriptor.before().isPresent()) {
            writeDescriptor(writer, descriptor.before().get(), childIndent);
        }
        for (Descriptor child : descriptor.children()) {
            writeDescriptor(writer, child, childIndent);
        }
        if (descriptor.after().isPresent()) {
            writeDescriptor(writer, descriptor.after().get(), childIndent);
        }
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
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static int firstEscapedCharacter(final String value) {
        for (var i = 0; i < value.length(); i++) {
            final var c = value.charAt(i);
            if (c == '&' || c == '<' || c == '>') {
                return i;
            }
        }
        return -1;
    }
}

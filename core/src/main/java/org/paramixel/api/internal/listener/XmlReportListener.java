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
 * Writes an XML descriptor report.
 */
public final class XmlReportListener extends AbstractReportFileListener {

    private static final String INDENT = "  ";
    private static final String LINE_SEPARATOR = System.lineSeparator();

    /**
     * Creates an XML report listener.
     *
     * @param reportFile the output file path
     */
    public XmlReportListener(final String reportFile) {
        super(reportFile);
    }

    @Override
    protected void writeReport(final Writer writer, final Result result) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        writer.write(LINE_SEPARATOR);
        if (result.descriptor().isEmpty()) {
            writer.write("<paramixel/>");
            return;
        }
        writeDescriptor(writer, result.descriptor().orElseThrow(), "");
    }

    @Override
    protected String formatName() {
        return "xml";
    }

    private static void writeDescriptor(final Writer writer, final Descriptor descriptor, final String indent)
            throws IOException {
        writer.write(indent);
        writer.write("<descriptor id=\"");
        writer.write(escape(descriptor.metadata().id()));
        writer.write("\" name=\"");
        writer.write(escape(descriptor.metadata().name()));
        writer.write("\" status=\"");
        writer.write(descriptor.metadata().status().name());
        writer.write("\">");
        writer.write(LINE_SEPARATOR);
        final var childIndent = indent + INDENT;
        for (Descriptor child : descriptor.children()) {
            writeDescriptor(writer, child, childIndent);
        }
        writer.write(indent);
        writer.write("</descriptor>");
        writer.write(LINE_SEPARATOR);
    }

    private static String escape(final String value) {
        var sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                case '\t', '\n', '\r' -> sb.append(c);
                default -> {
                    if (c >= ' ') {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}

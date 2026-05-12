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

package org.paramixel.core.internal.listener;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Version;

/**
 * Writes an XML end-of-run summary report to a configured file.
 *
 * <p>The output is a single root {@code <result>} element with nested {@code <children>} elements. Each result element
 * contains attributes for {@code name}, {@code kind}, {@code status}, and {@code runDuration}.
 * Optional child elements {@code <message>} and {@code <exception>} are included when the status carries a message or
 * throwable. Time values are expressed as whole milliseconds.
 */
public class XmlReportListener extends AbstractReportFileListener {

    /**
     * Creates an XML report listener for the supplied file.
     *
     * @param reportFile the file that will contain the generated report
     */
    public XmlReportListener(final String reportFile) {
        super(reportFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeReport(Writer writer, Runner runner, Result result) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<paramixel version=\"" + escapeXml(Version.getVersion()) + "\">\n");

        writeResult(writer, result, 1);

        writer.write("</paramixel>\n");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String formatName() {
        return "XML";
    }

    private void writeResult(Writer writer, Result result, int indent) throws IOException {
        String pad = "  ".repeat(indent);
        String padInner = pad + "  ";

        var action = result.getAction();
        writer.write(pad + "<result");
        writer.write(" name=\"" + escapeXml(action.getName()) + "\"");
        writer.write(" kind=\"" + escapeXml(Listeners.formatKind(action)) + "\"");

        var status = result.getStatus();
        writer.write(" status=\"" + Listeners.formatStatus(status) + "\"");
        writer.write(" runDuration=\"" + result.getRunDuration().toMillis() + "\"");
        writer.write(">");

        String message = status.getMessage().orElse(null);
        String exception = Listeners.formatException(status);

        boolean hasMessage = message != null;
        boolean hasException = exception != null;
        List<Result> children = result.getChildren();

        if (!hasMessage && !hasException && children.isEmpty()) {
            writer.write("</result>\n");
            return;
        }

        writer.write("\n");

        if (hasMessage) {
            writer.write(padInner + "<message>" + escapeXml(message) + "</message>\n");
        }

        if (hasException) {
            writer.write(padInner + "<exception>" + escapeXml(exception) + "</exception>\n");
        }

        if (!children.isEmpty()) {
            writer.write(padInner + "<children>\n");
            for (Result child : children) {
                writeResult(writer, child, indent + 2);
            }
            writer.write(padInner + "</children>\n");
        }

        writer.write(pad + "</result>\n");
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return null;
        }
        var sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}

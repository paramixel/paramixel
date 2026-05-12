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
 * Writes a JSON end-of-run summary report to a configured file.
 *
 * <p>The output is a single JSON object representing the root result with nested {@code children} arrays. Each result
 * node contains {@code name}, {@code kind}, {@code status}, {@code runDuration}, {@code message}, and {@code exception}
 * fields. Time values are expressed as whole milliseconds. Exception values are formatted as
 * {@code ExceptionClass: message}.
 */
public class JsonReportListener extends AbstractReportFileListener {

    /**
     * Creates a JSON report listener for the supplied file.
     *
     * @param reportFile the file that will contain the generated report
     */
    public JsonReportListener(final String reportFile) {
        super(reportFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeReport(Writer writer, Runner runner, Result result) throws IOException {
        writer.write("{\n");
        writer.write("  \"version\": \"");
        writer.write(Listeners.escapeJson(Version.getVersion()));
        writer.write("\",\n");
        writer.write("  \"result\": ");
        writeResult(writer, result, 1);
        writer.write("\n}\n");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String formatName() {
        return "JSON";
    }

    private void writeResult(Writer writer, Result result, int indent) throws IOException {
        String pad = "  ".repeat(indent);
        String padInner = pad + "  ";

        writer.write(pad + "{\n");

        var action = result.getAction();
        writer.write(padInner + "\"name\": \"");
        writer.write(Listeners.escapeJson(action.getName()));
        writer.write("\",\n");

        writer.write(padInner + "\"kind\": \"");
        writer.write(Listeners.escapeJson(Listeners.formatKind(action)));
        writer.write("\",\n");

        var status = result.getStatus();
        writer.write(padInner + "\"status\": \"");
        writer.write(Listeners.formatStatus(status));
        writer.write("\",\n");

        writer.write(padInner + "\"runDuration\": ");
        writer.write(String.valueOf(result.getRunDuration().toMillis()));
        writer.write(",\n");

        writer.write(padInner + "\"message\": ");
        writeNullableString(writer, status.getMessage().orElse(null));
        writer.write(",\n");

        writer.write(padInner + "\"exception\": ");
        writeNullableString(writer, Listeners.formatException(status));
        writer.write(",\n");

        List<Result> children = result.getChildren();
        writer.write(padInner + "\"children\": [");

        if (!children.isEmpty()) {
            writer.write("\n");
            for (int i = 0; i < children.size(); i++) {
                writeResult(writer, children.get(i), indent + 2);
                if (i < children.size() - 1) {
                    writer.write(",");
                }
                writer.write("\n");
            }
            writer.write(padInner + "]");
        } else {
            writer.write("]");
        }

        writer.write("\n");
        writer.write(pad + "}");
    }

    private void writeNullableString(Writer writer, String value) throws IOException {
        if (value == null) {
            writer.write("null");
        } else {
            writer.write("\"");
            writer.write(Listeners.escapeJson(value));
            writer.write("\"");
        }
    }
}

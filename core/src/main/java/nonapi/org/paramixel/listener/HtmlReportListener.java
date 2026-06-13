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
import java.time.Duration;
import nonapi.org.paramixel.support.ElapsedTimeFormatter;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Result;
import org.paramixel.api.Version;

/**
 * Writes an HTML descriptor report with minimal CSS styling.
 *
 * <p>The report includes a summary line with status, timestamp, and elapsed time,
 * followed by a table of descriptors with color-coded status rows and
 * hierarchical indentation.
 */
public final class HtmlReportListener extends AbstractReportFileListener {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private static final String CSS = """
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: system-ui, -apple-system, sans-serif; max-width: 960px; margin: 2rem auto; padding: 0 1rem; color: #1a1a1a; background: #fff; }
            h1 { font-size: 1.5rem; margin-bottom: 0.5rem; }
            .summary { color: #555; margin-bottom: 1rem; font-size: 0.9rem; }
            table { width: 100%; border-collapse: collapse; }
            th, td { padding: 0.4rem 0.6rem; text-align: left; border-bottom: 1px solid #e0e0e0; }
            th { font-weight: 600; background: #f5f5f5; }
            tr.passed { }
            tr.failed { background: #fff0f0; }
            tr.skipped { background: #fff8e0; }
            tr.aborted { background: #fff0e8; }
            tr.pending, tr.running { background: #f0f4ff; }
            td:first-child { font-weight: 600; white-space: nowrap; }
            """;

    /**
     * Creates an HTML report listener.
     */
    public HtmlReportListener() {}

    @Override
    protected void writeReport(final Writer writer, final Result result) throws IOException {
        writer.write("<!DOCTYPE html>");
        writer.write(LINE_SEPARATOR);
        writer.write("<html lang=\"en\">");
        writer.write(LINE_SEPARATOR);
        writer.write("<head>");
        writer.write(LINE_SEPARATOR);
        writer.write("<meta charset=\"UTF-8\">");
        writer.write(LINE_SEPARATOR);
        writer.write("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        writer.write(LINE_SEPARATOR);
        writer.write("<title>Paramixel Report");
        var version = Version.version();
        if (!Version.UNKNOWN.equals(version)) {
            writer.write(" — v");
            writer.write(escape(version));
        }
        writer.write("</title>");
        writer.write(LINE_SEPARATOR);
        writer.write("<style>");
        writer.write(LINE_SEPARATOR);
        writer.write(CSS);
        writer.write("</style>");
        writer.write(LINE_SEPARATOR);
        writer.write("</head>");
        writer.write(LINE_SEPARATOR);
        writer.write("<body>");
        writer.write(LINE_SEPARATOR);
        writer.write("<h1>Paramixel Report</h1>");
        writer.write(LINE_SEPARATOR);
        writeSummaryLine(writer, result);
        if (result.descriptor().isEmpty()) {
            writer.write("<p>No Paramixel tests found</p>");
            writer.write(LINE_SEPARATOR);
        } else {
            writer.write("<table>");
            writer.write(LINE_SEPARATOR);
            writer.write("<thead><tr><th>Status</th><th>Action</th><th>Elapsed</th></tr></thead>");
            writer.write(LINE_SEPARATOR);
            writer.write("<tbody>");
            writer.write(LINE_SEPARATOR);
            writeDescriptor(writer, result.descriptor().orElseThrow(), 0);
            writer.write("</tbody>");
            writer.write(LINE_SEPARATOR);
            writer.write("</table>");
            writer.write(LINE_SEPARATOR);
        }
        writer.write("</body>");
        writer.write(LINE_SEPARATOR);
        writer.write("</html>");
        writer.write(LINE_SEPARATOR);
    }

    @Override
    protected String formatName() {
        return "html";
    }

    private static void writeSummaryLine(final Writer writer, final Result result) throws IOException {
        writer.write("<p class=\"summary\">Status: ");
        writer.write(escape(Listeners.formatStatus(result)));
        var completedAt = result.completedAt();
        if (completedAt.isPresent()) {
            writer.write(" | Finished: ");
            writer.write(escape(completedAt.orElseThrow().toString()));
        }
        var startedAt = result.startedAt();
        if (startedAt.isPresent() && completedAt.isPresent()) {
            var elapsed = Duration.between(startedAt.orElseThrow(), completedAt.orElseThrow())
                    .toMillis();
            if (elapsed > 0) {
                writer.write(" | Total time: ");
                writer.write(escape(ElapsedTimeFormatter.formatDuration(elapsed)));
            }
        }
        writer.write("</p>");
        writer.write(LINE_SEPARATOR);
    }

    private static void writeDescriptor(final Writer writer, final Descriptor descriptor, final int depth)
            throws IOException {
        writer.write("<tr class=\"");
        writer.write(statusClass(descriptor));
        writer.write("\"><td>");
        writer.write(escape(Listeners.formatStatus(descriptor)));
        writer.write("</td><td style=\"padding-left:");
        writer.write(Integer.toString(depth * 2));
        writer.write("em\">");
        writer.write(escape(descriptor.action().displayName()));
        writer.write("</td><td>");
        var elapsed = Listeners.elapsedMillis(descriptor);
        if (elapsed > 0) {
            writer.write(escape(ElapsedTimeFormatter.formatDuration(elapsed)));
        }
        writer.write("</td></tr>");
        writer.write(LINE_SEPARATOR);
        int childDepth = depth + 1;
        if (descriptor.before().isPresent()) {
            writeDescriptor(writer, descriptor.before().orElseThrow(), childDepth);
        }
        for (Descriptor child : descriptor.children()) {
            writeDescriptor(writer, child, childDepth);
        }
        if (descriptor.after().isPresent()) {
            writeDescriptor(writer, descriptor.after().orElseThrow(), childDepth);
        }
    }

    private static String statusClass(final Descriptor descriptor) {
        if (descriptor.isFailed()) {
            return "failed";
        }
        if (descriptor.isAborted()) {
            return "aborted";
        }
        if (descriptor.isSkipped()) {
            return "skipped";
        }
        if (descriptor.isCompleted()) {
            return "passed";
        }
        return descriptor.startedAt().isPresent() ? "running" : "pending";
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

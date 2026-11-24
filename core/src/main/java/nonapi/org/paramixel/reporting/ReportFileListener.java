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

package nonapi.org.paramixel.reporting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.paramixel.api.Configuration;
import org.paramixel.api.Failure;
import org.paramixel.api.Listener;
import org.paramixel.api.PhaseResult;
import org.paramixel.api.Result;
import org.paramixel.api.TaskResult;

/**
 * Writes a machine-readable or printable report file when the run completes.
 *
 * <p>The format follows the file extension: {@code .json}, {@code .xml}, {@code .html}; anything else is plain text.
 * Every format is generated from the same immutable {@link Result}, so a report cannot disagree with console output.
 * Secrets are not written: the report contains the redacted configuration summary, statuses, reasons, and stack traces
 * only.
 */
public final class ReportFileListener implements Listener {

    private static final String HTML_TITLE_END_AND_STYLE = "</title>\n"
            + "<style>body{font-family:sans-serif}table{border-collapse:collapse}"
            + "td,th{border:1px solid #ccc;padding:4px 8px;text-align:left}"
            + ".FAILED{background:#fdd}.PASSED{background:#dfd}.SKIPPED{background:#ffe}"
            + ".ABORTED{background:#fec}.CANCELED{background:#eee}</style>\n"
            + "</head>\n<body>\n<h1>Paramixel run ";

    private final Path file;

    /**
     * Creates a listener.
     *
     * @param configuration the effective configuration, used to resolve {@link Configuration#REPORT_FILE}; must not be
     *     {@code null}
     */
    public ReportFileListener(final Configuration configuration) {
        this(Path.of(configuration
                .getString(Configuration.REPORT_FILE)
                .orElseThrow(() -> new IllegalArgumentException("report file is not configured"))));
    }

    /**
     * Creates a listener for a file.
     *
     * @param file the target file; must not be {@code null}
     */
    public ReportFileListener(final Path file) {
        this.file = Objects.requireNonNull(file, "file is null");
    }

    @Override
    public void onRunCompleted(final Result result) {
        final var name =
                file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        final String content;
        if (name.endsWith(".json")) {
            content = json(result);
        } else if (name.endsWith(".xml")) {
            content = xml(result);
        } else if (name.endsWith(".html") || name.endsWith(".htm")) {
            content = html(result);
        } else {
            content = text(result);
        }
        try {
            final var parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // String.getBytes replaces an unpaired surrogate (for example from a truncated failure message) instead of
            // throwing, so one malformed message cannot prevent the whole report from being written.
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write report file " + file, e);
        }
    }

    // ------------------------------------------------------------------------------------------
    // JSON
    // ------------------------------------------------------------------------------------------

    private static String json(final Result result) {
        final var out = new StringBuilder(1024);
        out.append("{\n  \"schemaVersion\": 1,\n  \"run\": {\n    \"id\": ")
                .append(quote(result.runId()))
                .append(",\n    \"status\": ")
                .append(quote(result.status().kind().name()))
                .append(",\n    \"exitCode\": ")
                .append(result.exitCode())
                .append(",\n    \"quiescent\": ")
                .append(result.isQuiescent())
                .append(",\n    \"startedAt\": ")
                .append(optionalInstant(result.startedAt()))
                .append(",\n    \"completedAt\": ")
                .append(optionalInstant(result.completedAt()))
                .append(",\n    \"shuffleSeed\": ")
                .append(result.shuffleSeed().map(String::valueOf).orElse("null"))
                .append(",\n    \"counts\": {\"occurrences\": ")
                .append(result.occurrenceCount())
                .append(", \"attempts\": ")
                .append(result.attemptCount())
                .append(", \"passed\": ")
                .append(result.passedCount())
                .append(", \"failed\": ")
                .append(result.failedCount())
                .append(", \"skipped\": ")
                .append(result.skippedCount())
                .append(", \"aborted\": ")
                .append(result.abortedCount())
                .append(", \"canceled\": ")
                .append(result.canceledCount())
                .append(", \"timedOut\": ")
                .append(result.timedOutCount())
                .append("},\n    \"configuration\": {");
        var first = true;
        for (var entry : result.configurationSummary().entrySet()) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(quote(entry.getKey())).append(": ").append(quote(entry.getValue()));
        }
        out.append("},\n    \"diagnostics\": ")
                .append(quoteList(result.diagnostics()))
                .append(",\n    \"failures\": ")
                .append(jsonFailures(result.failures()))
                .append("\n  },\n  \"root\": ")
                .append(result.root()
                        .map(root -> jsonNode(root, false, attemptTotalOf(root)))
                        .orElse("null"))
                .append("\n}\n");
        return out.toString();
    }

    private static String jsonNode(final TaskResult node, final boolean superseded, final int attemptTotal) {
        final var out = new StringBuilder(1024);
        out.append("{\n    \"kind\": ")
                .append(quote(node.kind().name()))
                .append(",\n    \"occurrenceId\": ")
                .append(quote(node.occurrenceId()))
                .append(",\n    \"definitionId\": ")
                .append(quote(node.definitionId()))
                .append(",\n    \"parentOccurrenceId\": ")
                .append(node.parentOccurrenceId().map(ReportFileListener::quote).orElse("null"))
                .append(",\n    \"name\": ")
                .append(quote(node.name()))
                .append(",\n    \"path\": ")
                .append(quote(node.path()))
                .append(",\n    \"mode\": ")
                .append(quote(node.mode().name()))
                .append(",\n    \"pool\": ")
                .append(node.poolName().map(ReportFileListener::quote).orElse("null"))
                .append(",\n    \"lock\": ")
                .append(node.exclusiveLock().map(ReportFileListener::quote).orElse("null"))
                .append(",\n    \"shuffleSeed\": ")
                .append(node.shuffleSeed().map(String::valueOf).orElse("null"))
                .append(",\n    \"executionOrder\": ")
                .append(quoteList(node.executionOrder()))
                .append(",\n    \"status\": ")
                .append(quote(node.status().kind().name()))
                .append(",\n    \"bodyStatus\": ")
                .append(node.bodyStatus()
                        .map(status -> quote(status.kind().name()))
                        .orElse("null"))
                .append(",\n    \"attemptIndex\": ")
                .append(node.attemptIndex())
                .append(",\n    \"attemptTotal\": ")
                .append(attemptTotal)
                .append(",\n    \"superseded\": ")
                .append(superseded)
                .append(",\n    \"timedOut\": ")
                .append(node.timedOut())
                .append(",\n    \"reason\": ")
                .append(node.reason().map(ReportFileListener::quote).orElse("null"))
                .append(",\n    \"phases\": [");
        var first = true;
        for (PhaseResult phase : node.phases()) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append("{\"phase\": ")
                    .append(quote(phase.phase().name()))
                    .append(", \"name\": ")
                    .append(quote(phase.name()))
                    .append(", \"status\": ")
                    .append(quote(phase.status().kind().name()))
                    .append('}');
        }
        out.append("],\n    \"failures\": ")
                .append(jsonFailures(node.failures()))
                .append(",\n    \"children\": [");
        var childFirst = true;
        for (TaskResult child : node.children()) {
            if (!childFirst) {
                out.append(", ");
            }
            childFirst = false;
            final var childSuperseded =
                    child.kind() == TaskResult.Kind.ATTEMPT && child.attemptIndex() < attemptTotalOf(node);
            out.append(jsonNode(child, childSuperseded, attemptTotalOf(child)));
        }
        out.append("]\n  }");
        return out.toString();
    }

    private static int attemptTotalOf(final TaskResult node) {
        return (int) node.children().stream()
                .filter(child -> child.kind() == TaskResult.Kind.ATTEMPT)
                .count();
    }

    private static String jsonFailures(final List<Failure> failures) {
        final var out = new StringBuilder(128).append('[');
        var first = true;
        for (Failure failure : failures) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append("{\"phase\": ")
                    .append(quote(failure.phase()))
                    .append(", \"message\": ")
                    .append(quote(failure.message()))
                    .append(", \"primary\": ")
                    .append(failure.isPrimary())
                    .append(", \"throwable\": ")
                    .append(failure.throwable().map(t -> quote(t.toString())).orElse("null"))
                    .append('}');
        }
        return out.append(']').toString();
    }

    private static String optionalInstant(final Optional<Instant> instant) {
        return instant.map(value -> quote(value.toString())).orElse("null");
    }

    private static String quoteList(final List<String> values) {
        final var out = new StringBuilder("[");
        var first = true;
        for (String value : values) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(quote(value));
        }
        return out.append(']').toString();
    }

    private static String quote(final String value) {
        final var out = new StringBuilder("\"");
        for (var i = 0; i < value.length(); i++) {
            final var c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    // ------------------------------------------------------------------------------------------
    // XML
    // ------------------------------------------------------------------------------------------

    private static String xml(final Result result) {
        final var out = new StringBuilder(256);
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<paramixel schemaVersion=\"1\">\n  <run id=\"")
                .append(escapeXml(result.runId()))
                .append("\" status=\"")
                .append(result.status().kind().name())
                .append("\" exitCode=\"")
                .append(result.exitCode())
                .append("\" quiescent=\"")
                .append(result.isQuiescent())
                .append("\"/>\n");
        result.root().ifPresent(root -> {
            out.append("  <tasks>\n");
            appendXml(out, root, 2, false);
            out.append("  </tasks>\n");
        });
        out.append("</paramixel>\n");
        return out.toString();
    }

    private static void appendXml(
            final StringBuilder out, final TaskResult node, final int depth, final boolean superseded) {
        final var indent = "  ".repeat(depth);
        out.append(indent)
                .append("<task kind=\"")
                .append(node.kind().name())
                .append("\" id=\"")
                .append(escapeXml(node.occurrenceId()))
                .append("\" name=\"")
                .append(escapeXml(node.name()))
                .append("\" status=\"")
                .append(node.status().kind().name())
                .append('"');
        node.reason()
                .ifPresent(reason ->
                        out.append(" reason=\"").append(escapeXml(reason)).append('"'));
        if (node.timedOut()) {
            out.append(" timedOut=\"true\"");
        }
        if (node.kind() == TaskResult.Kind.ATTEMPT) {
            out.append(" attemptIndex=\"")
                    .append(node.attemptIndex())
                    .append("\" superseded=\"")
                    .append(superseded)
                    .append('"');
        }
        out.append(">\n");
        for (Failure failure : node.failures()) {
            out.append(indent)
                    .append("  <failure phase=\"")
                    .append(escapeXml(failure.phase()))
                    .append("\" primary=\"")
                    .append(failure.isPrimary())
                    .append("\">")
                    .append(escapeXml(failure.message()))
                    .append("</failure>\n");
        }
        final var attemptTotal = attemptTotalOf(node);
        for (TaskResult child : node.children()) {
            final var childSuperseded = child.kind() == TaskResult.Kind.ATTEMPT && child.attemptIndex() < attemptTotal;
            appendXml(out, child, depth + 1, childSuperseded);
        }
        out.append(indent).append("</task>\n");
    }

    private static String escapeXml(final String value) {
        return escape(value, true);
    }

    private static String escape(final String value) {
        return escape(value, false);
    }

    private static String escape(final String value, final boolean xml) {
        final var out = new StringBuilder(value.length() + 16);
        var i = 0;
        while (i < value.length()) {
            final var codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);
            switch (codePoint) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                // XML parsers normalize literal whitespace in attributes and carriage returns in text.
                case '\t' -> out.append(xml ? "&#9;" : "\t");
                case '\n' -> out.append(xml ? "&#10;" : "\n");
                case '\r' -> out.append(xml ? "&#13;" : "\r");
                default -> {
                    if (isXmlCharacter(codePoint)) {
                        out.appendCodePoint(codePoint);
                    } else {
                        // XML 1.0 cannot represent this character at all, not even as a character reference.
                        out.append('\uFFFD');
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * Returns whether a code point is a legal XML 1.0 {@code Char}.
     *
     * <p>Control characters other than tab, line feed and carriage return, unpaired surrogates, and U+FFFE/U+FFFF are
     * illegal; ANSI color codes in failure messages are a common source of them.
     *
     * @param codePoint the code point
     * @return {@code true} if the code point may appear in an XML document
     */
    private static boolean isXmlCharacter(final int codePoint) {
        return codePoint == 0x9
                || codePoint == 0xA
                || codePoint == 0xD
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }

    // ------------------------------------------------------------------------------------------
    // HTML and text
    // ------------------------------------------------------------------------------------------

    private static String html(final Result result) {
        final var out = new StringBuilder(1024);
        out.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n<title>Paramixel report ")
                .append(escape(result.runId()))
                .append(HTML_TITLE_END_AND_STYLE)
                .append(escape(result.runId()))
                .append("</h1>\n<p>Status: <strong>")
                .append(result.status().kind())
                .append("</strong>, exit code ")
                .append(result.exitCode())
                .append(", quiescent: ")
                .append(result.isQuiescent())
                .append("</p>\n<p>Occurrences: ")
                .append(result.occurrenceCount())
                .append(" (passed ")
                .append(result.passedCount())
                .append(", failed ")
                .append(result.failedCount())
                .append(", skipped ")
                .append(result.skippedCount())
                .append(", aborted ")
                .append(result.abortedCount())
                .append(", canceled ")
                .append(result.canceledCount())
                .append(", timed out ")
                .append(result.timedOutCount())
                .append(")</p>\n");
        result.root().ifPresent(root -> {
            out.append("<table>\n<tr><th>Task</th><th>Kind</th><th>Status</th><th>Reason</th></tr>\n");
            appendHtml(out, root, 0, 0);
            out.append("</table>\n");
        });
        for (String diagnostic : result.diagnostics()) {
            out.append("<p class=\"diagnostic\">").append(escape(diagnostic)).append("</p>\n");
        }
        out.append("</body>\n</html>\n");
        return out.toString();
    }

    private static void appendHtml(
            final StringBuilder out, final TaskResult node, final int depth, final int attemptTotal) {
        out.append("<tr class=\"")
                .append(node.status().kind())
                .append("\"><td>")
                .append("&nbsp;".repeat(depth * 2))
                .append(escape(node.name()))
                .append("</td><td>")
                .append(
                        node.kind() == TaskResult.Kind.ATTEMPT && node.attemptIndex() < attemptTotal
                                ? "ATTEMPT (superseded)"
                                : node.kind())
                .append("</td><td>")
                .append(node.status().kind())
                .append("</td><td>")
                .append(node.reason().map(ReportFileListener::escape).orElse(""))
                .append("</td></tr>\n");
        final var childAttemptTotal = attemptTotalOf(node);
        for (TaskResult child : node.children()) {
            appendHtml(out, child, depth + 1, childAttemptTotal);
        }
    }

    private static String text(final Result result) {
        final var lines = new ArrayList<String>();
        lines.add("Paramixel run " + result.runId());
        lines.add("status: " + result.status().kind() + " exitCode: " + result.exitCode() + " quiescent: "
                + result.isQuiescent());
        result.root()
                .ifPresent(root -> lines.addAll(TreeRenderer.renderDetailed(
                        root, new ConsoleSupport(Configuration.of(Map.of("paramixel.ansi", "false"))))));
        for (String diagnostic : result.diagnostics()) {
            lines.add("diagnostic: " + diagnostic);
        }
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }
}

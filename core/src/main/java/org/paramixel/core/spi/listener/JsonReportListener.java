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

package org.paramixel.core.spi.listener;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Status;
import org.paramixel.core.Version;
import org.paramixel.core.support.Arguments;

/**
 * Writes a JSON end-of-run summary report to a configured file.
 *
 * <p>The output is a single JSON object representing the root result with nested {@code children} arrays. Each result
 * node contains {@code name}, {@code kind}, {@code status}, {@code runDuration}, {@code message}, and {@code exception}
 * fields. Time values are expressed as whole milliseconds. Exception values are formatted as
 * {@code ExceptionClass: message}.
 */
public class JsonReportListener implements Listener {

    private final Path reportFile;

    /**
     * Creates a JSON report listener for the supplied file.
     *
     * @param reportFile the file that will contain the generated report
     */
    public JsonReportListener(final String reportFile) {
        Objects.requireNonNull(reportFile, "reportFile must not be null");
        Arguments.requireNonBlank(reportFile, "reportFile must not be blank");
        this.reportFile = TildePathExpander.expand(reportFile);
    }

    @Override
    public void runCompleted(Runner runner, Result result) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(result, "result must not be null");

        try {
            Path parent = reportFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (Writer writer = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8)) {
                writer.write("{\n");
                writer.write("  \"version\": \"");
                writer.write(escapeJson(Version.getVersion()));
                writer.write("\",\n");
                writer.write("  \"result\": ");
                writeResult(writer, result, 1);
                writer.write("\n}\n");
            }
        } catch (IOException e) {
            System.err.println(Constants.PARAMIXEL_PLAIN + "Unable to write JSON report file: " + e.getMessage());
        }
    }

    private void writeResult(Writer writer, Result result, int indent) throws IOException {
        String pad = "  ".repeat(indent);
        String padInner = pad + "  ";

        writer.write(pad + "{\n");

        writer.write(padInner + "\"name\": \"");
        writer.write(escapeJson(result.getAction().getName()));
        writer.write("\",\n");

        writer.write(padInner + "\"kind\": \"");
        writer.write(escapeJson(formatKind(result)));
        writer.write("\",\n");

        writer.write(padInner + "\"status\": \"");
        writer.write(formatStatus(result.getStatus()));
        writer.write("\",\n");

        writer.write(padInner + "\"runDuration\": ");
        writer.write(String.valueOf(result.getRunDuration().toMillis()));
        writer.write(",\n");

        writer.write(padInner + "\"message\": ");
        writeNullableString(writer, result.getStatus().getMessage().orElse(null));
        writer.write(",\n");

        writer.write(padInner + "\"exception\": ");
        writeNullableString(writer, formatException(result.getStatus()));
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
            writer.write(escapeJson(value));
            writer.write("\"");
        }
    }

    private static String formatStatus(Status status) {
        if (status.isStaged()) {
            return "STAGED";
        } else if (status.isPass()) {
            return "PASS";
        } else if (status.isFailure()) {
            return "FAIL";
        } else {
            return "SKIP";
        }
    }

    private static String formatKind(Result result) {
        Class<?> actionClass = result.getAction().getClass();
        if (actionClass.getPackageName().equals("org.paramixel.core.action")) {
            return actionClass.getSimpleName();
        }
        return actionClass.getName();
    }

    private static String formatException(Status status) {
        if (status.isFailure()) {
            return status.getThrowable()
                    .map(f -> f.getClass().getSimpleName() + ": " + f.getMessage())
                    .or(() -> status.getMessage())
                    .orElse(null);
        }
        return null;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}

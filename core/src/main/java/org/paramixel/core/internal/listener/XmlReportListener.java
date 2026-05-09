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
import java.io.UncheckedIOException;
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
import org.paramixel.core.internal.TildePathExpander;
import org.paramixel.core.support.Arguments;

/**
 * Writes an XML end-of-run summary report to a configured file.
 *
 * <p>The output is a single root {@code <result>} element with nested {@code <children>} elements. Each result element
 * contains attributes for {@code name}, {@code kind}, {@code status}, and {@code runDuration}.
 * Optional child elements {@code <message>} and {@code <exception>} are included when the status carries a message or
 * throwable. Time values are expressed as whole milliseconds.
 */
public class XmlReportListener implements Listener {

    private final Path reportFile;

    /**
     * Creates an XML report listener for the supplied file.
     *
     * @param reportFile the file that will contain the generated report
     */
    public XmlReportListener(final String reportFile) {
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
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writer.write("<paramixel version=\"" + escapeXml(Version.getVersion()) + "\">\n");

                writeResult(writer, result, 1);

                writer.write("</paramixel>\n");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write XML report file: " + reportFile, e);
        }
    }

    private void writeResult(Writer writer, Result result, int indent) throws IOException {
        String pad = "  ".repeat(indent);
        String padInner = pad + "  ";

        writer.write(pad + "<result");
        writer.write(" name=\"" + escapeXml(result.getAction().getName()) + "\"");
        writer.write(" kind=\"" + escapeXml(formatKind(result)) + "\"");
        writer.write(" status=\"" + formatStatus(result.getStatus()) + "\"");
        writer.write(" runDuration=\"" + result.getRunDuration().toMillis() + "\"");
        writer.write(">");

        String message = result.getStatus().getMessage().orElse(null);
        String exception = formatException(result.getStatus());

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
        if ("org.paramixel.core.action".equals(actionClass.getPackageName())) {
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

    private static String escapeXml(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
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

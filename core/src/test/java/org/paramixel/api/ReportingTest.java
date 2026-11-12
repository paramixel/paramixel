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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.Tasks.task;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Report files are generated from the same immutable result as console output.
 */
class ReportingTest {

    @TempDir
    Path tempDir;

    private static Task definition() {
        return task("root")
                .step("pass", () -> {})
                .step(
                        "fail",
                        () -> {
                            throw new IllegalStateException("boom");
                        },
                        children -> children.step("nested", () -> {}))
                .build();
    }

    private Path report(final String fileName) {
        final var file = tempDir.resolve(fileName);
        final var result = Runner.builder()
                .listener(ReportFileListenerFactory.listener(file))
                .build()
                .run(definition());
        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(file).exists();
        return file;
    }

    @Test
    @DisplayName("JSON report contains run metadata, tasks, and failures")
    void jsonReport() throws Exception {
        final var content = Files.readString(report("report.json"));

        assertThat(content).contains("\"schemaVersion\": 1");
        assertThat(content).contains("\"status\": \"FAILED\"");
        assertThat(content).contains("\"occurrenceId\": \"0\"");
        assertThat(content).contains("\"path\": \"root > fail\"");
        assertThat(content).contains("boom");
        assertThat(content).contains("\"quiescent\": true");
    }

    @Test
    @DisplayName("XML report is well-formed and carries identities")
    void xmlReport() throws Exception {
        final var file = report("report.xml");
        final var document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(Files.readString(file).getBytes(StandardCharsets.UTF_8)));

        assertThat(document.getDocumentElement().getNodeName()).isEqualTo("paramixel");
        assertThat(document.getElementsByTagName("task").getLength()).isGreaterThanOrEqualTo(3);
        assertThat(document.getElementsByTagName("failure").getLength()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("HTML report renders the tree with status classes")
    void htmlReport() throws Exception {
        final var content = Files.readString(report("report.html"));

        assertThat(content).contains("<!DOCTYPE html>");
        assertThat(content).contains("FAILED");
        assertThat(content).contains("&nbsp;&nbsp;fail");
    }

    @Test
    @DisplayName("plain text report renders the tree")
    void textReport() throws Exception {
        final var content = Files.readString(report("report.log"));

        assertThat(content).contains("Paramixel run");
        assertThat(content.lines().anyMatch(line -> line.matches("^PASS +pass \\(sequential\\)$")))
                .isTrue();
    }

    @Test
    @DisplayName("console listener writes status and summary from the same result")
    void consoleListener() {
        final var out = new java.io.ByteArrayOutputStream();
        final var listener = nonapi.org.paramixel.reporting.DefaultListeners.create(
                Configuration.of(Map.of(Configuration.ANSI, "false")), new java.io.PrintStream(out));

        final var result = Runner.builder().listener(listener).build().run(definition());
        final var text = out.toString(StandardCharsets.UTF_8);

        assertThat(text).contains("Paramixel");
        assertThat(text).contains("[PARAMIXEL] FAIL root > fail");
        assertThat(text).contains("occurrences:");
        assertThat(text).contains("exitCode: " + result.exitCode());
    }

    @Test
    @DisplayName("detailed reports keep superseded attempts with explicit markers")
    void detailedReportsKeepSupersededAttempts() throws Exception {
        final var attempts = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.function.Supplier<Task> definition = () -> task("root")
                .child(Tasks.sequential("retried")
                        .retry(org.paramixel.api.support.RetryPolicy.attempts(3))
                        .step("probe", () -> {
                            if (attempts.incrementAndGet() < 3) {
                                throw new IllegalStateException("not yet");
                            }
                        }))
                .build();

        final var textFile = tempDir.resolve("retry.log");
        Runner.builder()
                .listener(new nonapi.org.paramixel.reporting.ReportFileListener(textFile))
                .build()
                .run(definition.get());

        final var text = Files.readString(textFile);
        assertThat(text).contains("3 attempts, 2 failed");
        assertThat(text).contains("attempt 1 of 3");
        assertThat(text).contains("superseded");
        assertThat(text).contains("attempt 3 of 3");

        attempts.set(0);
        final var jsonFile = tempDir.resolve("retry.json");
        Runner.builder()
                .listener(new nonapi.org.paramixel.reporting.ReportFileListener(jsonFile))
                .build()
                .run(definition.get());

        final var json = Files.readString(jsonFile);
        assertThat(json).contains("\"superseded\": true");
        assertThat(json).contains("\"superseded\": false");
        assertThat(json).contains("\"attemptTotal\": 3");
    }

    /**
     * Creates a report-file listener for a path.
     */
    private static final class ReportFileListenerFactory {

        private ReportFileListenerFactory() {
            // Intentionally empty
        }

        static Listener listener(final Path file) {
            return new nonapi.org.paramixel.reporting.ReportFileListener(file);
        }
    }
}

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
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.xml.parsers.DocumentBuilderFactory;
import nonapi.org.paramixel.reporting.DefaultListeners;
import nonapi.org.paramixel.reporting.ReportFileListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.api.support.RetryPolicy;
import org.w3c.dom.Element;

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
    void xmlReportPreservesWhitespaceInTaskAttributes() throws Exception {
        final var name = "task\twith\na\rname";
        final var reason = "reason\r\nsecond\tline";
        final var file = tempDir.resolve("attributes.xml");
        final var definition = task(name)
                .before(() -> {
                    throw new IllegalStateException(reason);
                })
                .step("unstarted", () -> {})
                .build();
        final var result = Runner.builder()
                .listener(ReportFileListenerFactory.listener(file))
                .build()
                .run(definition);
        final var document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(Files.readAllBytes(file)));
        final var root = (Element) document.getElementsByTagName("task").item(0);
        final var failure = root.getElementsByTagName("failure").item(0);
        final var rootResult = result.root().orElseThrow();
        assertThat(root.getAttribute("name")).isEqualTo(rootResult.name()).isEqualTo(name);
        assertThat(failure.getTextContent())
                .isEqualTo(rootResult.failures().get(0).message())
                .contains(reason);
    }

    @Test
    void xmlReportPreservesSupplementaryCharactersAndMarkupAsText() throws Exception {
        final var name = "<test> & \"name\" 🎉";
        final var file = tempDir.resolve("characters.xml");
        Runner.builder()
                .listener(ReportFileListenerFactory.listener(file))
                .build()
                .run(task(name).step("work", () -> {}).build());
        final var document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(Files.readAllBytes(file)));
        final var root = (Element) document.getElementsByTagName("task").item(0);
        assertThat(root.getAttribute("name")).isEqualTo(name);
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
        final var out = new ByteArrayOutputStream();
        final var listener =
                DefaultListeners.create(Configuration.of(Map.of(Configuration.ANSI, "false")), new PrintStream(out));

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
        final var attempts = new AtomicInteger();
        final Supplier<Task> definition = () -> task("root")
                .child(Tasks.sequential("retried")
                        .retry(RetryPolicy.attempts(3))
                        .step("probe", () -> {
                            if (attempts.incrementAndGet() < 3) {
                                throw new IllegalStateException("not yet");
                            }
                        }))
                .build();

        final var textFile = tempDir.resolve("retry.log");
        Runner.builder().listener(new ReportFileListener(textFile)).build().run(definition.get());

        final var text = Files.readString(textFile);
        assertThat(text).contains("3 attempts, 2 failed");
        assertThat(text).contains("attempt 1 of 3");
        assertThat(text).contains("superseded");
        assertThat(text).contains("attempt 3 of 3");

        attempts.set(0);
        final var jsonFile = tempDir.resolve("retry.json");
        Runner.builder().listener(new ReportFileListener(jsonFile)).build().run(definition.get());

        final var json = Files.readString(jsonFile);
        assertThat(json).contains("\"superseded\": true");
        assertThat(json).contains("\"superseded\": false");
        assertThat(json).contains("\"attemptTotal\": 3");
    }

    @Test
    @DisplayName("the JSON report gives a retried root task its attempt total")
    void jsonReportsRootAttemptTotal() throws Exception {
        final Task definition = task("root")
                .retry(RetryPolicy.attempts(2))
                .step("fail", () -> {
                    throw new IllegalStateException("boom");
                })
                .build();

        final var jsonFile = tempDir.resolve("root-retry.json");
        Runner.builder().listener(new ReportFileListener(jsonFile)).build().run(definition);

        final var json = Files.readString(jsonFile);
        final var rootNode = json.substring(json.indexOf("\"root\": {"));
        final var rootHeader = rootNode.substring(0, rootNode.indexOf("\"children\""));
        assertThat(rootHeader).contains("\"attemptTotal\": 2");
    }

    @Test
    @DisplayName("console summary and report file include infrastructure diagnostics such as NON_QUIESCENT")
    void reportsIncludeInfrastructureDiagnostics() throws Exception {
        final var configuration =
                Configuration.of(Map.of(Configuration.ANSI, "false", Configuration.SHUTDOWN_GRACE_MILLIS, "80"));

        final var out = new ByteArrayOutputStream();
        final var console = DefaultListeners.create(configuration, new PrintStream(out, true, StandardCharsets.UTF_8));
        final var consoleResult = Runner.builder()
                .listener(console)
                .configuration(configuration)
                .build()
                .run(nonQuiescentDefinition());

        assertThat(consoleResult.isQuiescent()).isFalse();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("diagnostic NON_QUIESCENT");

        final var file = tempDir.resolve("non-quiescent.log");
        final var fileResult = Runner.builder()
                .listener(ReportFileListenerFactory.listener(file))
                .configuration(configuration)
                .build()
                .run(nonQuiescentDefinition());

        assertThat(fileResult.isQuiescent()).isFalse();
        assertThat(Files.readString(file)).contains("NON_QUIESCENT");
    }

    @Test
    @DisplayName("the XML report stays well-formed when names and messages contain control characters")
    void xmlReportReplacesIllegalControlCharacters() throws Exception {
        final Task definition = task("root")
                .step("bell\u0007step", () -> {
                    throw new IllegalStateException("bad \u0001 byte and \u001b[31mANSI\u001b[0m color");
                })
                .build();

        final var file = tempDir.resolve("control.xml");
        Runner.builder()
                .listener(ReportFileListenerFactory.listener(file))
                .build()
                .run(definition);

        final var document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(Files.readAllBytes(file)));
        assertThat(document.getElementsByTagName("failure").getLength()).isGreaterThanOrEqualTo(1);
        assertThat(document.getDocumentElement().getTextContent()).contains("ANSI");
    }

    @Test
    @DisplayName("a report is still written when a failure message contains an unpaired surrogate")
    void reportWrittenDespiteUnpairedSurrogate() throws Exception {
        final Task definition = task("root")
                .step("truncated", () -> {
                    // A message cut in the middle of a surrogate pair, as naive truncation of an emoji produces.
                    throw new IllegalStateException("cut \uD83D");
                })
                .build();

        final var file = tempDir.resolve("surrogate.json");
        final var result = Runner.builder()
                .listener(ReportFileListenerFactory.listener(file))
                .build()
                .run(definition);

        assertThat(result.diagnostics()).noneMatch(diagnostic -> diagnostic.contains("Failed to write report"));
        assertThat(file).exists();
        assertThat(Files.readString(file)).contains("\"status\": \"FAILED\"");
    }

    private static Task nonQuiescentDefinition() {
        return task("root")
                .timeout(Duration.ofMillis(80))
                .step("stuck", () -> {
                    final var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(400);
                    while (System.nanoTime() < deadline) {
                        try {
                            Thread.sleep(10L);
                        } catch (InterruptedException e) {
                            // Deliberately ignore interruption: this callback is uncooperative.
                        }
                    }
                })
                .build();
    }

    /**
     * Creates a report-file listener for a path.
     */
    private static final class ReportFileListenerFactory {

        private ReportFileListenerFactory() {
            // Intentionally empty
        }

        static Listener listener(final Path file) {
            return new ReportFileListener(file);
        }
    }
}

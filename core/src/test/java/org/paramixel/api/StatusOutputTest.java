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
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.task;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.exception.SkipException;

/**
 * Console status-line format: short padded labels, no indentation, and elapsed time.
 */
class StatusOutputTest {

    private static final String PREFIX = "[PARAMIXEL] ";

    private static final Pattern LABEL_LINE =
            Pattern.compile("^\\[PARAMIXEL\\] (PASS|FAIL|SKIP|ABORT|CANCEL|PEND|RUN|warn)\\s{1,}.*");

    private List<String> run(final Task definition) {
        final var buffer = new ByteArrayOutputStream();
        final var listener = nonapi.org.paramixel.reporting.DefaultListeners.create(
                Configuration.of(Map.of(Configuration.ANSI, "false")), new PrintStream(buffer));
        Runner.builder().listener(listener).pool(Pool.named("p"), 4).build().run(definition);
        return buffer.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    @DisplayName("status lines are not indented and start with an unpadded short label")
    void statusLinesAreLeftAlignedWithoutPadding() {
        final var definition = parallel("root", Pool.named("p"))
                .step("pass", () -> {})
                .step("skip", () -> {
                    throw SkipException.skip("not applicable");
                })
                .step("abort", () -> {
                    throw AbortException.abort("assumption");
                })
                .step("fail", () -> {
                    throw new IllegalStateException("boom");
                })
                .build();

        final var lines = run(definition);

        final var statusLines = lines.stream()
                .filter(line -> LABEL_LINE.matcher(line).matches())
                .toList();
        assertThat(statusLines).isNotEmpty();
        for (String line : statusLines) {
            assertThat(line).startsWith(PREFIX);
            final var rest = line.substring(PREFIX.length());
            final var label = rest.substring(0, rest.indexOf(' '));
            assertThat(label).isIn("PASS", "FAIL", "SKIP", "ABORT", "CANCEL", "PEND", "RUN", "warn");
            assertThat(rest.charAt(label.length())).isEqualTo(' ');
        }
        final var labels = statusLines.stream()
                .map(line -> {
                    final var rest = line.substring(PREFIX.length());
                    return rest.substring(0, rest.indexOf(' '));
                })
                .toList();
        assertThat(labels).contains("PASS", "FAIL", "SKIP", "ABORT");
    }

    @Test
    @DisplayName("terminal status lines end with the elapsed time in milliseconds")
    void terminalLinesCarryElapsedTime() {
        final var definition =
                task("root").step("slow", () -> Thread.sleep(15L)).build();

        final var lines = run(definition);

        final var terminal = lines.stream()
                .filter(line -> line.startsWith(PREFIX + "PASS") && line.contains("root / slow"))
                .toList();
        assertThat(terminal).hasSize(1);
        assertThat(terminal.get(0)).matches("^\\[PARAMIXEL\\] PASS root / slow \\(\\d+ ms\\)$");
    }

    @Test
    @DisplayName("failures keep the reason after the elapsed time")
    void failureLinesCarryElapsedAndReason() {
        final var definition = task("root")
                .step("failing", () -> {
                    throw new IllegalStateException("boom");
                })
                .build();

        final var lines = run(definition);

        final var failure = lines.stream()
                .filter(line -> line.startsWith(PREFIX + "FAIL") && line.contains("root / failing"))
                .toList();
        assertThat(failure).hasSize(1);
        assertThat(failure.get(0))
                .matches("^\\[PARAMIXEL\\] FAIL root / failing \\(\\d+ ms\\) — IllegalStateException: boom$");
    }

    @Test
    @DisplayName("summary tree lines also start with the label at column zero")
    void summaryTreeKeepsTheLeftColumn() {
        final var definition = task("group")
                .step("parent", () -> {}, children -> children.step("child", () -> {}))
                .build();

        final var lines = run(definition);

        final var treeLines =
                lines.stream().filter(line -> line.contains("(sequential)")).toList();
        assertThat(treeLines).isNotEmpty();
        for (String line : treeLines) {
            assertThat(line).matches("^\\[PARAMIXEL\\] (PASS|FAIL|SKIP|ABORT|CANCEL) .*");
        }
    }

    @Test
    @DisplayName("console summary shows final outcomes only and notes attempt history")
    void consoleSummaryHidesSupersededAttempts() {
        final var attempts = new java.util.concurrent.atomic.AtomicInteger();
        final var definition = task("root")
                .child(org.paramixel.api.Tasks.sequential("retried")
                        .retry(org.paramixel.api.support.RetryPolicy.attempts(3))
                        .step("probe", () -> {
                            if (attempts.incrementAndGet() < 3) {
                                throw new IllegalStateException("not yet");
                            }
                        }))
                .build();

        final var lines = run(definition);

        assertThat(attempts).hasValue(3);
        assertThat(lines).noneMatch(line -> line.matches("^\\[PARAMIXEL\\] (PASS|FAIL) +attempt .*"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("3 attempts, 2 failed"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("result: PASS exitCode: 0"));
        assertThat(lines)
                .anySatisfy(line -> assertThat(line).matches("^\\[PARAMIXEL\\] FAIL root / retried / probe .*"));
    }

    @Test
    @DisplayName("run footer uses the same short label")
    void footerUsesShortLabel() {
        final var definition = task("root").step("pass", () -> {}).build();

        final var lines = run(definition);

        assertThat(lines).anySatisfy(line -> assertThat(line).startsWith(PREFIX + "PASS run "));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("result: PASS exitCode: 0"));
    }
}

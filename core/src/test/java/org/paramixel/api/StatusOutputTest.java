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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import nonapi.org.paramixel.reporting.DefaultListeners;
import nonapi.org.paramixel.reporting.StatusListener;
import nonapi.org.paramixel.reporting.SummaryListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.exception.SkipException;
import org.paramixel.api.support.RetryPolicy;

/**
 * Console status-line format: short padded labels, no indentation, and elapsed time.
 */
class StatusOutputTest {

    private static final String PREFIX = "[PARAMIXEL] ";

    private static final Pattern LABEL_LINE =
            Pattern.compile("^\\[PARAMIXEL\\] (PASS|FAIL|SKIP|ABORT|CANCEL|PEND|RUN|warn)\\s{1,}.*");

    private List<String> run(final Task definition) {
        final var buffer = new ByteArrayOutputStream();
        final var listener =
                DefaultListeners.create(Configuration.of(Map.of(Configuration.ANSI, "false")), new PrintStream(buffer));
        Runner.builder().listener(listener).pool(Pool.named("p"), 4).build().run(definition);
        return buffer.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    @DisplayName("status lines are not indented and start with an unpadded short label")
    void statusLinesAreLeftAlignedWithoutPadding() {
        final var definition = Parallel.builder("root", Pool.named("p"))
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
                Sequential.builder("root").step("slow", () -> Thread.sleep(15L)).build();

        final var lines = run(definition);

        final var terminal = lines.stream()
                .filter(line -> line.startsWith(PREFIX + "PASS") && line.contains("root > slow"))
                .toList();
        assertThat(terminal).hasSize(1);
        assertThat(terminal.get(0)).matches("^\\[PARAMIXEL\\] PASS root > slow \\(\\d+ ms\\)$");
    }

    @Test
    @DisplayName("failures keep the reason after the elapsed time")
    void failureLinesCarryElapsedAndReason() {
        final var definition = Sequential.builder("root")
                .step("failing", () -> {
                    throw new IllegalStateException("boom");
                })
                .build();

        final var lines = run(definition);

        final var failure = lines.stream()
                .filter(line -> line.startsWith(PREFIX + "FAIL") && line.contains("root > failing"))
                .toList();
        assertThat(failure).hasSize(1);
        assertThat(failure.get(0))
                .matches("^\\[PARAMIXEL\\] FAIL root > failing \\(\\d+ ms\\) — IllegalStateException: boom$");
    }

    @Test
    @DisplayName("summary tree draws children under connectors at the left margin")
    void summaryTreeUsesConnectors() {
        final var definition = Sequential.builder("group")
                .step(Sequential.builder("parent").step("child", () -> {}).build())
                .step("sibling", () -> {})
                .build();

        final var lines = run(definition);

        assertThat(lines).anyMatch(line -> isTreeLine(line, "group \\(sequential\\) PASS \\(\\d+ ms\\)"));
        assertThat(lines).anyMatch(line -> isTreeLine(line, "├─ parent \\(sequential\\) PASS \\(\\d+ ms\\)"));
        assertThat(lines).anyMatch(line -> isTreeLine(line, "│  └─ child PASS \\(\\d+ ms\\)"));
        assertThat(lines).anyMatch(line -> isTreeLine(line, "└─ sibling PASS \\(\\d+ ms\\)"));
    }

    @Test
    @DisplayName("only tasks that schedule children carry a mode suffix")
    void onlyTasksThatScheduleChildrenCarryAModeSuffix() {
        final var definition =
                Sequential.builder("group").step("leaf", () -> {}).build();

        final var lines = run(definition);

        assertThat(lines).anyMatch(line -> isTreeLine(line, "group \\(sequential\\) PASS \\(\\d+ ms\\)"));
        assertThat(lines).anyMatch(line -> isTreeLine(line, "└─ leaf PASS \\(\\d+ ms\\)"));
        assertThat(lines).noneMatch(line -> line.contains("leaf (sequential)"));
        assertThat(lines).noneMatch(line -> line.contains("leaf (parallel)"));
    }

    @Test
    @DisplayName("a retried task keeps its mode suffix in the summary")
    void retriedTaskKeepsItsModeSuffix() {
        final var attempts = new AtomicInteger();
        final var definition = Sequential.builder("root")
                .step(Sequential.builder("retried")
                        .retry(RetryPolicy.attempts(2))
                        .step("probe", () -> {
                            if (attempts.incrementAndGet() < 2) {
                                throw new IllegalStateException("not yet");
                            }
                        }))
                .build();

        final var lines = run(definition);

        assertThat(lines).anyMatch(line -> isTreeLine(line, "root \\(sequential\\) PASS \\(\\d+ ms\\)"));
        assertThat(lines)
                .anyMatch(line ->
                        isTreeLine(line, "└─ retried \\(sequential\\) PASS \\(\\d+ ms\\) — 2 attempts, 1 failed"));
        assertThat(lines).anyMatch(line -> isTreeLine(line, "   └─ probe PASS \\(\\d+ ms\\)"));
    }

    @Test
    @DisplayName("console summary shows final outcomes only and notes attempt history")
    void consoleSummaryHidesSupersededAttempts() {
        final var attempts = new AtomicInteger();
        final var definition = Sequential.builder("root")
                .step(Sequential.builder("retried")
                        .retry(RetryPolicy.attempts(3))
                        .step("probe", () -> {
                            if (attempts.incrementAndGet() < 3) {
                                throw new IllegalStateException("not yet");
                            }
                        }))
                .build();

        final var lines = run(definition);

        assertThat(attempts).hasValue(3);
        assertThat(lines).noneMatch(line -> line.contains("attempt "));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("3 attempts, 2 failed"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("result: PASS exitCode: 0"));
        assertThat(lines)
                .anySatisfy(line -> assertThat(line).matches("^\\[PARAMIXEL\\] FAIL root > retried > probe .*"));
    }

    @Test
    @DisplayName("run footer uses the same short label")
    void footerUsesShortLabel() {
        final var definition = Sequential.builder("root").step("pass", () -> {}).build();

        final var lines = run(definition);

        assertThat(lines).anySatisfy(line -> assertThat(line).startsWith(PREFIX + "PASS run "));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("result: PASS exitCode: 0"));
    }

    @Test
    @DisplayName("footers tolerate a wall clock that stepped backwards during the run")
    void footersTolerateBackwardClockStep() {
        final var startedAt = Instant.parse("2026-01-01T00:00:10Z");
        final var result = Result.builder()
                .runId("clock-step")
                .status(Status.PASSED, 0, true)
                .timings(startedAt, startedAt.minusSeconds(5))
                .build();
        final var configuration = Configuration.of(Map.of(Configuration.ANSI, "false"));
        final var buffer = new ByteArrayOutputStream();
        final var out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

        new StatusListener(configuration, out).onRunCompleted(result);
        new SummaryListener(configuration, out).onRunCompleted(result);

        final var lines = buffer.toString(StandardCharsets.UTF_8).lines().toList();
        assertThat(lines).anySatisfy(line -> assertThat(line).startsWith(PREFIX + "PASS run clock-step in 0 ms"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("result: PASS exitCode: 0 elapsed: 0 ms"));
    }

    @Test
    @DisplayName("the run totals are separated from the result tree by a rule")
    void totalsAreSeparatedFromTheTreeByRule() {
        final var definition = Sequential.builder("consume").build();

        final var lines = run(definition);

        final var totals = lines.indexOf(lines.stream()
                .filter(line -> line.startsWith(PREFIX + "occurrences: "))
                .findFirst()
                .orElseThrow());
        assertThat(totals).isGreaterThan(0);
        assertThat(lines.get(totals - 1)).isEqualTo(PREFIX + "-------");
        assertThat(lines).doesNotContain("");
    }

    @Test
    @DisplayName("the summary header is a bracketed title separated from the run footer by a rule")
    void summaryHeaderIsSeparatedFromFooterByRule() {
        final var definition = Sequential.builder("root").step("pass", () -> {}).build();

        final var lines = run(definition);

        final var footer = lines.indexOf(lines.stream()
                .filter(line -> line.startsWith(PREFIX + "PASS run "))
                .findFirst()
                .orElseThrow());
        final var titleRule = PREFIX + "-".repeat("Paramixel Summary".length());
        assertThat(lines.get(footer + 1)).isEqualTo(titleRule);
        assertThat(lines.get(footer + 2)).isEqualTo(PREFIX + "Paramixel Summary");
        assertThat(lines.get(footer + 3)).isEqualTo(titleRule);
        assertThat(lines.get(footer + 4)).startsWith(PREFIX + "root (sequential) PASS");
    }

    private static boolean isTreeLine(final String line, final String tree) {
        return line.matches(Pattern.quote(PREFIX) + tree);
    }
}

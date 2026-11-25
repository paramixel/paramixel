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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.reporting.DefaultListeners;
import nonapi.org.paramixel.reporting.ReportFileListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.api.support.RetryPolicy;

/**
 * The summary and the text report render the result as a tree: connector, name, status, elapsed time, and reason.
 */
class SummaryTreeTest {

    private static final String PREFIX = "[PARAMIXEL] ";

    private static final String TOTALS_RULE = PREFIX + "-------";

    @Test
    @DisplayName("nested children are drawn with branches and their status follows the name")
    void nestedTreeShape() {
        final var definition = Sequential.builder("root")
                .step(Sequential.builder("alpha")
                        .step("a1", () -> {})
                        .step("a2", () -> {})
                        .build())
                .step("beta", () -> {})
                .build();

        final var tree = tree(definition);

        assertThat(tree)
                .containsExactly(
                        "root (sequential) PASS (N ms)",
                        "├── alpha (sequential) PASS (N ms)",
                        "│   ├── a1 PASS (N ms)",
                        "│   └── a2 PASS (N ms)",
                        "└── beta PASS (N ms)");
    }

    @Test
    @DisplayName("a node that never ran has no elapsed time")
    void skippedNodeHasNoElapsedTime() {
        final var definition = Sequential.builder("root")
                .step("fail", () -> {
                    throw new IllegalStateException("boom");
                })
                .step("never", () -> {})
                .build();

        final var tree = tree(definition);

        assertThat(tree).anySatisfy(line -> {
            assertThat(line).startsWith("└── never SKIP");
            assertThat(line).doesNotContain(" ms)");
        });
    }

    @Test
    @DisplayName("children begin with branch characters at the left margin")
    void branchesStartAtTheLeftMargin() {
        final var definition = Sequential.builder("root")
                .step("pass", () -> {})
                .step(Sequential.builder("nested").step("child", () -> {}).build())
                .build();

        final var tree = tree(definition);

        assertThat(tree)
                .filteredOn(line -> !line.startsWith("root"))
                .allSatisfy(line -> assertThat(line)
                        .matches(
                                "^(│   |    )*(├── |└── )\\S.* (PASS|FAIL|SKIP|ABORT|CANCEL)( \\([^)]*\\))?( — .*)?$"));
    }

    @Test
    @DisplayName("the text report keeps the same tree, including phases and every attempt")
    void detailedReportUsesTheSameTree(@TempDir final Path tempDir) throws Exception {
        final var attempts = new AtomicInteger();
        final var definition = Sequential.builder("root")
                .before(Sequential.builder("setup").step("s1", () -> {}).build())
                .step(Sequential.builder("retried")
                        .retry(RetryPolicy.attempts(2))
                        .step("probe", () -> {
                            if (attempts.incrementAndGet() < 2) {
                                throw new IllegalStateException("not yet");
                            }
                        })
                        .build())
                .after(Sequential.builder("cleanup").step("c1", () -> {}).build())
                .build();

        final var file = tempDir.resolve("tree.log");
        Runner.builder().listener(new ReportFileListener(file)).build().run(definition);

        final var tree = Files.readString(file)
                .lines()
                .filter(line -> !line.startsWith("Paramixel run "))
                .filter(line -> !line.startsWith("status: "))
                .map(line -> line.replaceAll(" \\(\\d+ ms\\)", " (N ms)"))
                .toList();

        assertThat(tree)
                .containsExactly(
                        "root (sequential) PASS (N ms)",
                        "├── setup (sequential) [before] PASS (N ms)",
                        "│   └── s1 PASS (N ms)",
                        "├── retried (sequential) PASS (N ms) — 2 attempts, 1 failed",
                        "│   ├── attempt 1 of 2 retried FAIL (N ms) — superseded",
                        "│   │   └── probe FAIL (N ms)",
                        "│   └── attempt 2 of 2 retried PASS (N ms)",
                        "│       └── probe PASS (N ms)",
                        "└── cleanup (sequential) [after] PASS (N ms)",
                        "    └── c1 PASS (N ms)");
    }

    private static List<String> tree(final Task definition) {
        final var buffer = new ByteArrayOutputStream();
        final var listener = DefaultListeners.create(
                Configuration.of(Map.of(Configuration.ANSI, "false")),
                new PrintStream(buffer, true, StandardCharsets.UTF_8));
        Runner.builder().listener(listener).build().run(definition);

        final var lines = buffer.toString(StandardCharsets.UTF_8).lines().toList();
        final var title = lines.indexOf(PREFIX + "Paramixel Summary");
        final var end = lines.indexOf(TOTALS_RULE);
        assertThat(title).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(title);
        return lines.subList(title + 2, end).stream()
                .map(line -> line.substring(PREFIX.length()))
                .map(line -> line.replaceAll(" \\(\\d+ ms\\)", " (N ms)"))
                .toList();
    }
}

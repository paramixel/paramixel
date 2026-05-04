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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Sequential;
import org.paramixel.core.support.AnsiColor;

@DisplayName("TableSummaryRenderer")
class TableSummaryRendererTest {

    private static final String HIDDEN_ROOT = "7e5c6b4c-1428-3fee-abd4-24a245687061";

    private String runAndCaptureOutput(Action action) {
        SummaryListener listener = new SummaryListener(new TableSummaryRenderer());
        Runner runner = Runner.builder().listener(listener).build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("renders summary header")
    void rendersSummaryHeader() {
        Noop noop = Noop.of("test-action");
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("Summary:");
    }

    @Test
    @DisplayName("hidden root UUID is filtered from output but children are shown")
    void hiddenRootUuidFilteredFromOutput() {
        Action child = Noop.of("visible-child");
        Sequential seq = Sequential.of(HIDDEN_ROOT, child);
        String output = runAndCaptureOutput(seq);

        assertThat(output).doesNotContain(HIDDEN_ROOT);
        assertThat(output).contains("visible-child");
    }

    @Test
    @DisplayName("name truncation at 30 characters")
    void nameTruncationAt30Chars() {
        String longName = "a".repeat(31);
        Noop noop = Noop.of(longName);
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("a".repeat(27) + "...");
    }

    @Test
    @DisplayName("PASS status formatted with green")
    void passStatusFormattedWithGreen() {
        Noop noop = Noop.of("passing");
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains(AnsiColor.GREEN_TEXT.format("PASS"));
    }

    @Test
    @DisplayName("FAIL status formatted with red")
    void failStatusFormattedWithRed() {
        Action failing = Direct.of("failing", context -> {
            throw new RuntimeException("error message");
        });
        String output = runAndCaptureOutput(failing);

        assertThat(output).contains(AnsiColor.RED_TEXT.format("FAIL"));
    }

    @Test
    @DisplayName("SKIP status formatted with orange")
    void skipStatusFormattedWithOrange() {
        Action skipping = Direct.of("skipping", context -> {
            throw org.paramixel.core.exception.SkipException.of("skip reason");
        });
        String output = runAndCaptureOutput(skipping);

        assertThat(output).contains(AnsiColor.ORANGE_TEXT.format("SKIP"));
    }

    @Test
    @DisplayName("failure info with throwable shows exception class and message")
    void failureInfoWithThrowable() {
        Action failing = Direct.of("failing", context -> {
            throw new IllegalStateException("state is illegal");
        });
        String output = runAndCaptureOutput(failing);

        assertThat(output).contains("IllegalStateException");
        assertThat(output).contains("state is illegal");
    }

    @Test
    @DisplayName("skip info with message shows reason")
    void skipInfoWithMessage() {
        Action skipping = Direct.of("skipper", context -> {
            throw org.paramixel.core.exception.SkipException.of("disabled feature");
        });
        String output = runAndCaptureOutput(skipping);

        assertThat(output).contains("disabled feature");
    }

    @Test
    @DisplayName("kind shows simple name for core actions")
    void kindShowsSimpleNameForCoreActions() {
        Noop noop = Noop.of("noop-action");
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("(Noop)");
    }

    @Test
    @DisplayName("results summary line")
    void resultsSummaryLine() {
        Noop noop = Noop.of("test");
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("Results:");
        assertThat(output).contains("passed");
        assertThat(output).contains("Total:");
    }

    @Test
    @DisplayName("total time uses root elapsed time not sum of node durations")
    void totalTimeUsesRootElapsedTime() {
        Action child1 = Direct.of("child1", context -> {
            Thread.sleep(50);
        });
        Action child2 = Direct.of("child2", context -> {
            Thread.sleep(50);
        });
        Sequential parent = Sequential.of("parent", child1, child2);

        SummaryListener listener = new SummaryListener(new TableSummaryRenderer());
        Runner runner = Runner.builder().listener(listener).build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        Result rootResult;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            rootResult = runner.run(parent);
        } finally {
            System.setOut(originalOut);
        }

        String outputStr = output.toString(StandardCharsets.UTF_8);
        long rootElapsedMillis = rootResult.getElapsedTime().toMillis();
        long sumOfNodesMillis = rootResult.getElapsedTime().toMillis()
                + rootResult.getChildren().get(0).getElapsedTime().toMillis()
                + rootResult.getChildren().get(1).getElapsedTime().toMillis();

        String totalLine = outputStr
                .lines()
                .filter(line -> line.contains("Total:"))
                .findFirst()
                .orElse("");
        assertThat(totalLine).contains(String.valueOf(rootElapsedMillis));
        assertThat(rootElapsedMillis).isLessThan(sumOfNodesMillis);
    }

    @Test
    @DisplayName("single action with no children renders correctly")
    void singleActionWithNoChildren() {
        Noop noop = Noop.of("solo");
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("solo");
        assertThat(output).contains("PASS");
    }

    @Test
    @DisplayName("renderSummary rejects null arguments")
    void renderSummaryRejectsNullArguments() {
        TableSummaryRenderer renderer = new TableSummaryRenderer();
        Runner runner = Runner.builder().build();
        Result result = runner.run(Noop.of("test"));

        assertThatThrownBy(() -> renderer.renderSummary(null, result))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("runner must not be null");
        assertThatThrownBy(() -> renderer.renderSummary(runner, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("result must not be null");
    }
}

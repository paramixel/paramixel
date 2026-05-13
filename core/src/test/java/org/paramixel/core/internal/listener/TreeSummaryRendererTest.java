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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.support.AnsiColor;

@DisplayName("TreeSummaryRenderer")
class TreeSummaryRendererTest {

    private static final String ROOT_NAME = Constants.ROOT_NAME;

    private String runAndCaptureOutput(Action action) {
        var listener = new SummaryListener(new TreeSummaryRenderer());
        Runner runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        return output.toString(StandardCharsets.UTF_8);
    }

    private String runAndCapturePlainOutput(Action action) {
        var output = new ByteArrayOutputStream();
        var printStream = new PrintStream(output, true, StandardCharsets.UTF_8);
        var listener = new SummaryListener(new TreeSummaryRenderer(printStream, false), printStream, false);
        Runner runner = Runner.builder().listener(listener).build();
        runner.run(action);
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("root node name appears in output")
    void rootNodeNameAppearsInOutput() {
        Action child = Noop.of("visible-child");
        Parallel parallel =
                Parallel.builder(ROOT_NAME).parallelism(1).child(child).build();
        String output = runAndCaptureOutput(parallel);

        assertThat(output).contains(ROOT_NAME);
        assertThat(output).contains("visible-child");
    }

    @Test
    @DisplayName("non-hidden root rendered")
    void nonHiddenRootRendered() {
        Noop noop = Noop.of("my-root");
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("my-root");
    }

    @Test
    @DisplayName("single action with no children renders without tree connectors")
    void singleActionWithNoChildren() {
        Noop noop = Noop.of("leaf");
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("leaf");
        assertThat(output).contains("PASS");
    }

    @Test
    @DisplayName("PASS status rendered with bold green")
    void passStatusRenderedWithBoldGreen() {
        Noop noop = Noop.of("passing");
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains(AnsiColor.BOLD_GREEN_TEXT.format("PASS"));
    }

    @Test
    @DisplayName("FAIL status rendered with bold red")
    void failStatusRenderedWithBoldRed() {
        Action failing = Direct.builder("failing")
                .runnable(context -> {
                    throw new RuntimeException("error");
                })
                .build();
        String output = runAndCaptureOutput(failing);

        assertThat(output).contains(AnsiColor.BOLD_RED_TEXT.format("FAIL"));
    }

    @Test
    @DisplayName("SKIP status rendered with bold orange")
    void skipStatusRenderedWithBoldOrange() {
        Action skipping = Direct.builder("skipper")
                .runnable(context -> {
                    throw org.paramixel.core.exception.SkipException.of("reason");
                })
                .build();
        String output = runAndCaptureOutput(skipping);

        assertThat(output).contains(AnsiColor.BOLD_ORANGE_TEXT.format("SKIP"));
    }

    @Test
    @DisplayName("plain output disables ANSI formatting")
    void plainOutputDisablesAnsiFormatting() {
        String output = runAndCapturePlainOutput(Noop.of("plain"));

        assertThat(output).contains(Constants.PARAMIXEL_PLAIN);
        assertThat(output).contains("PASS");
        assertThat(output).doesNotContain("\u001B[");
    }

    @Test
    @DisplayName("failure info with throwable shows fully qualified exception class and message")
    void failureInfoWithThrowable() {
        Action failing = Direct.builder("fail-action")
                .runnable(context -> {
                    throw new IllegalStateException("bad state");
                })
                .build();
        String output = runAndCaptureOutput(failing);

        assertThat(output).contains("java.lang.IllegalStateException");
        assertThat(output).contains("bad state");
    }

    @Test
    @DisplayName("failure info with multi-line message renders on single line")
    void failureInfoWithMultiLineMessageRendersOnSingleLine() {
        Action failing = Direct.builder("multiline-fail")
                .runnable(context -> {
                    throw new RuntimeException("\nline2\nline3");
                })
                .build();
        String output = runAndCaptureOutput(failing);

        assertThat(output).contains("java.lang.RuntimeException");
        assertThat(output).contains("line2 line3");
        assertThat(output).doesNotContain("RuntimeException:\n");
    }

    @Test
    @DisplayName("failure info shows fully qualified exception class name")
    void failureInfoShowsFullyQualifiedExceptionClassName() {
        Action failing = Direct.builder("fqcn-fail")
                .runnable(context -> {
                    throw new IllegalStateException("fqcn test");
                })
                .build();
        String output = runAndCaptureOutput(failing);

        assertThat(output).contains("java.lang.IllegalStateException: fqcn test");
    }

    @Test
    @DisplayName("kind shows simple name for core actions")
    void kindShowsSimpleNameForCoreActions() {
        Noop noop = Noop.of("noop-action");
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("(Noop)");
    }

    @Test
    @DisplayName("full action names are preserved without truncation")
    void fullActionNamesPreserved() {
        String thirtyFiveCharName = "a".repeat(35);
        Noop noop = Noop.of(thirtyFiveCharName);
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains(thirtyFiveCharName);
    }

    @Test
    @DisplayName("timing shows milliseconds")
    void timingShowsMilliseconds() {
        Noop noop = Noop.of("timed");
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("ms");
    }

    @Test
    @DisplayName("two children rendered with tree connectors")
    void twoChildrenRenderedWithTreeConnectors() {
        Action child1 = Noop.of("child-1");
        Action child2 = Noop.of("child-2");
        Container seq = Container.builder("parent")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build())
                .child(child1)
                .child(child2)
                .build();
        String output = runAndCaptureOutput(seq);

        assertThat(output).contains("child-1");
        assertThat(output).contains("child-2");
        assertThat(output).contains("parent");
    }

    @Test
    @DisplayName("renderSummary rejects null arguments")
    void renderSummaryRejectsNullArguments() {
        var renderer = new TreeSummaryRenderer();
        Runner runner = Runner.builder().build();
        var result = runner.run(Noop.of("test"));

        assertThatThrownBy(() -> renderer.renderSummary(null, result))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("runner must not be null");
        assertThatThrownBy(() -> renderer.renderSummary(runner, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("result must not be null");
    }
}

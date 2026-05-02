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

package org.paramixel.core.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Sequential;
import org.paramixel.core.support.AnsiColor;

@DisplayName("TreeSummaryRenderer")
class TreeSummaryRendererTest {

    private static final String HIDDEN_ROOT = "7e5c6b4c-1428-3fee-abd4-24a245687061";

    private String runAndCaptureOutput(Action action) {
        SummaryListener listener = new SummaryListener(new TreeSummaryRenderer());
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
    @DisplayName("hidden root UUID stripped, children rendered at top level")
    void hiddenRootUuidStrippedChildrenAtTopLevel() {
        Action child = Noop.of("visible-child");
        Sequential seq = Sequential.of(HIDDEN_ROOT, child);
        String output = runAndCaptureOutput(seq);

        assertThat(output).doesNotContain(HIDDEN_ROOT);
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
        Action failing = Direct.of("failing", context -> {
            throw new RuntimeException("error");
        });
        String output = runAndCaptureOutput(failing);

        assertThat(output).contains(AnsiColor.BOLD_RED_TEXT.format("FAIL"));
    }

    @Test
    @DisplayName("SKIP status rendered with bold orange")
    void skipStatusRenderedWithBoldOrange() {
        Action skipping = Direct.of("skipper", context -> {
            org.paramixel.core.SkipException.skip("reason");
        });
        String output = runAndCaptureOutput(skipping);

        assertThat(output).contains(AnsiColor.BOLD_ORANGE_TEXT.format("SKIP"));
    }

    @Test
    @DisplayName("failure info with throwable shows exception class and message")
    void failureInfoWithThrowable() {
        Action failing = Direct.of("fail-action", context -> {
            throw new IllegalStateException("bad state");
        });
        String output = runAndCaptureOutput(failing);

        assertThat(output).contains("IllegalStateException");
        assertThat(output).contains("bad state");
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
        Sequential seq = Sequential.of("parent", child1, child2);
        String output = runAndCaptureOutput(seq);

        assertThat(output).contains("child-1");
        assertThat(output).contains("child-2");
        assertThat(output).contains("parent");
    }
}

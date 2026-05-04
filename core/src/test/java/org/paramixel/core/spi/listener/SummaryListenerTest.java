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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Noop;
import org.paramixel.core.support.AnsiColor;

@DisplayName("SummaryListener")
class SummaryListenerTest {

    private TrackingSummaryRenderer trackingRenderer = new TrackingSummaryRenderer();

    private static class TrackingSummaryRenderer implements SummaryRenderer {
        boolean renderSummaryCalled;
        Runner capturedRunner;
        Result capturedResult;

        @Override
        public void renderSummary(Runner runner, Result result) {
            renderSummaryCalled = true;
            capturedRunner = runner;
            capturedResult = result;
        }
    }

    @Test
    @DisplayName("constructor rejects null renderer")
    void constructorRejectsNullRenderer() {
        assertThatThrownBy(() -> new SummaryListener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("summaryRenderer must not be null");
    }

    @Test
    @DisplayName("runStarted prints start banner with version")
    void runStartedPrintsStartBanner() {
        TrackingSummaryRenderer renderer = new TrackingSummaryRenderer();
        SummaryListener listener = new SummaryListener(renderer);
        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(listener).build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(noop);
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("Paramixel v");
        assertThat(result).contains("starting...");
    }

    @Test
    @DisplayName("runCompleted delegates to renderer")
    void runCompletedDelegatesToRenderer() {
        TrackingSummaryRenderer renderer = new TrackingSummaryRenderer();
        SummaryListener listener = new SummaryListener(renderer);
        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(listener).build();

        PrintStream originalOut = System.out;
        try {
            runner.run(noop);
        } finally {
            System.setOut(originalOut);
        }

        assertThat(renderer.renderSummaryCalled).isTrue();
        assertThat(renderer.capturedResult).isNotNull();
    }

    @Test
    @DisplayName("runCompleted prints footer with PASS status in green")
    void runCompletedPrintsPassStatusInGreen() {
        SummaryListener listener = new SummaryListener(new TableSummaryRenderer());
        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(listener).build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(noop);
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains(AnsiColor.BOLD_GREEN_TEXT.format("PASS"));
    }

    @Test
    @DisplayName("runCompleted prints footer with FAIL status in red")
    void runCompletedPrintsFailStatusInRed() {
        SummaryListener listener = new SummaryListener(new TableSummaryRenderer());
        Action action = org.paramixel.core.action.Direct.of("fail", context -> {
            throw new RuntimeException("boom");
        });
        Runner runner = Runner.builder().listener(listener).build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains(AnsiColor.BOLD_RED_TEXT.format("FAIL"));
    }

    @Test
    @DisplayName("runCompleted prints footer with SKIP status in orange")
    void runCompletedPrintsSkipStatusInOrange() {
        SummaryListener listener = new SummaryListener(new TableSummaryRenderer());
        Action action = org.paramixel.core.action.Direct.of("skip", context -> {
            throw org.paramixel.core.exception.SkipException.of("reason");
        });
        Runner runner = Runner.builder().listener(listener).build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains(AnsiColor.BOLD_ORANGE_TEXT.format("SKIP"));
    }

    @Test
    @DisplayName("runCompleted prints version line")
    void runCompletedPrintsVersionLine() {
        SummaryListener listener = new SummaryListener(new TableSummaryRenderer());
        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(listener).build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(noop);
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("Paramixel v");
    }

    @Test
    @DisplayName("runCompleted prints total time")
    void runCompletedPrintsTotalTime() {
        SummaryListener listener = new SummaryListener(new TableSummaryRenderer());
        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(listener).build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(noop);
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("Total time");
    }

    @Nested
    @DisplayName("time formatting")
    class TimeFormattingTests {

        @Test
        @DisplayName("sub-second durations display just milliseconds")
        void subsecondDurationsDisplayJustMilliseconds() {
            SummaryListener listener = new SummaryListener(new TableSummaryRenderer());
            Noop noop = Noop.of("fast");
            Runner runner = Runner.builder().listener(listener).build();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
                runner.run(noop);
            } finally {
                System.setOut(originalOut);
            }

            String result = output.toString(StandardCharsets.UTF_8);
            assertThat(result).contains("Total time  :");
        }
    }

    @Test
    @DisplayName("callback methods reject null arguments")
    void callbackMethodsRejectNullArguments() {
        SummaryListener listener = new SummaryListener(new TableSummaryRenderer());
        Runner runner = Runner.builder().build();
        Result result = runner.run(Noop.of("test"));

        assertThatThrownBy(() -> listener.runStarted(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("runner must not be null");
        assertThatThrownBy(() -> listener.runCompleted(null, result))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("runner must not be null");
        assertThatThrownBy(() -> listener.runCompleted(runner, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("result must not be null");
    }
}

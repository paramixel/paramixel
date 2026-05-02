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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Sequential;
import org.paramixel.core.support.AnsiColor;

@DisplayName("StatusListener")
class StatusListenerTest {

    private static final String HIDDEN_ROOT = "7e5c6b4c-1428-3fee-abd4-24a245687061";

    @Test
    @DisplayName("of returns non-null instance")
    void ofReturnsNonNullInstance() {
        assertThat(StatusListener.of()).isNotNull();
    }

    @Nested
    @DisplayName("beforeAction")
    class BeforeActionTests {

        @Test
        @DisplayName("prints TEST line with action details")
        void printsTestLine() {
            StatusListener listener = StatusListener.of();
            Noop noop = Noop.of("my-action");
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
            assertThat(result).contains("TEST");
            assertThat(result).contains("my-action");
            assertThat(result).contains("Noop");
        }

        @Test
        @DisplayName("suppresses output for hidden root action")
        void suppressesOutputForHiddenRoot() {
            StatusListener listener = StatusListener.of();
            Action hiddenRoot = Direct.of(HIDDEN_ROOT, context -> {});
            Action child = Noop.of("child");
            Sequential seq = Sequential.of(HIDDEN_ROOT, child);
            Runner runner = Runner.builder().listener(listener).build();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
                runner.run(seq);
            } finally {
                System.setOut(originalOut);
            }

            String result = output.toString(StandardCharsets.UTF_8);
            assertThat(result).doesNotContain("TEST | " + HIDDEN_ROOT);
        }
    }

    @Nested
    @DisplayName("afterAction")
    class AfterActionTests {

        @Test
        @DisplayName("prints PASS status with green color")
        void printsPassStatusWithGreenColor() {
            StatusListener listener = StatusListener.of();
            Noop noop = Noop.of("passing-action");
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
        @DisplayName("prints FAIL status with red color")
        void printsFailStatusWithRedColor() {
            StatusListener listener = StatusListener.of();
            Action action = Direct.of("failing-action", context -> {
                throw new RuntimeException("fail");
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
        @DisplayName("prints SKIP status with orange color")
        void printsSkipStatusWithOrangeColor() {
            StatusListener listener = StatusListener.of();
            Action action = Direct.of("skipping-action", context -> {
                org.paramixel.core.SkipException.skip("skipped");
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
        @DisplayName("suppresses output for hidden root in afterAction")
        void suppressesOutputForHiddenRootInAfterAction() {
            StatusListener listener = StatusListener.of();
            Sequential seq = Sequential.of(HIDDEN_ROOT, Noop.of("child"));
            Runner runner = Runner.builder().listener(listener).build();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
                runner.run(seq);
            } finally {
                System.setOut(originalOut);
            }

            String result = output.toString(StandardCharsets.UTF_8);
            assertThat(result).doesNotContain(HIDDEN_ROOT);
        }
    }

    @Nested
    @DisplayName("actionThrowable")
    class ActionThrowableTests {

        @Test
        @DisplayName("prints EXCEPTION line to System.err")
        void printsExceptionLineToStderr() {
            StatusListener listener = StatusListener.of();
            Action action = Direct.of("error-action", context -> {
                throw new RuntimeException("action error");
            });
            Runner runner = Runner.builder().listener(listener).build();

            ByteArrayOutputStream outputErr = new ByteArrayOutputStream();
            PrintStream originalErr = System.err;
            PrintStream originalOut = System.out;
            try {
                System.setErr(new PrintStream(outputErr, true, StandardCharsets.UTF_8));
                runner.run(action);
            } finally {
                System.setErr(originalErr);
                System.setOut(originalOut);
            }

            String errResult = outputErr.toString(StandardCharsets.UTF_8);
            assertThat(errResult).contains("EXCEPTION");
            assertThat(errResult).contains("error-action");
        }

        @Test
        @DisplayName("suppresses output for hidden root in actionThrowable")
        void suppressesOutputForHiddenRootInActionThrowable() {
            StatusListener listener = StatusListener.of();
            Action failingChild = Direct.of("child-fail", context -> {
                throw new RuntimeException("child error");
            });
            Sequential seq = Sequential.of(HIDDEN_ROOT, failingChild);
            Runner runner = Runner.builder().listener(listener).build();

            ByteArrayOutputStream outputErr = new ByteArrayOutputStream();
            PrintStream originalErr = System.err;
            PrintStream originalOut = System.out;
            try {
                System.setErr(new PrintStream(outputErr, true, StandardCharsets.UTF_8));
                runner.run(seq);
            } finally {
                System.setErr(originalErr);
                System.setOut(originalOut);
            }

            String errResult = outputErr.toString(StandardCharsets.UTF_8);
            assertThat(errResult).doesNotContain(HIDDEN_ROOT);
        }
    }

    @Nested
    @DisplayName("action path and kind resolution")
    class ActionPathAndKindTests {

        @Test
        @DisplayName("shows simple name for core action classes")
        void showsSimpleNameForCoreActionClasses() {
            StatusListener listener = StatusListener.of();
            Noop noop = Noop.of("simple");
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
            assertThat(result).contains("(Noop)");
        }

        @Test
        @DisplayName("shows FQCN for non-core package action classes")
        void showsFQCNForNonCorePackageActionClasses() {
            StatusListener listener = StatusListener.of();
            Action customAction = Noop.of("custom");
            Runner runner = Runner.builder().listener(listener).build();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
                runner.run(customAction);
            } finally {
                System.setOut(originalOut);
            }

            String result = output.toString(StandardCharsets.UTF_8);
            assertThat(result).contains("Noop");
        }

        @Test
        @DisplayName("id path excludes hidden root ancestors")
        void idPathExcludesHiddenRootAncestors() {
            StatusListener listener = StatusListener.of();
            Action child = Noop.of("visible-child");
            Sequential seq = Sequential.of(HIDDEN_ROOT, child);
            Runner runner = Runner.builder().listener(listener).build();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
                runner.run(seq);
            } finally {
                System.setOut(originalOut);
            }

            String result = output.toString(StandardCharsets.UTF_8);
            assertThat(result).contains("visible-child");
            String testLine = result.lines()
                    .filter(line -> line.contains("TEST"))
                    .findFirst()
                    .orElse("");
            assertThat(testLine).contains(child.getId());
            assertThat(testLine).doesNotContain(HIDDEN_ROOT);
        }

        @Test
        @DisplayName("action path excludes hidden root ancestors")
        void actionPathExcludesHiddenRootAncestors() {
            StatusListener listener = StatusListener.of();
            Action child = Noop.of("visible-child");
            Sequential seq = Sequential.of(HIDDEN_ROOT, child);
            Runner runner = Runner.builder().listener(listener).build();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
                runner.run(seq);
            } finally {
                System.setOut(originalOut);
            }

            String result = output.toString(StandardCharsets.UTF_8);
            assertThat(result).contains("visible-child");
            assertThat(result).doesNotContain(HIDDEN_ROOT);
        }
    }

    @Nested
    @DisplayName("timing format")
    class TimingFormatTests {

        @Test
        @DisplayName("timing shows milliseconds")
        void timingShowsMilliseconds() {
            StatusListener listener = StatusListener.of();
            Noop noop = Noop.of("timed-action");
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
            assertThat(result).contains("ms");
        }
    }
}

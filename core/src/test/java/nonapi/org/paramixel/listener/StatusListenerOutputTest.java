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

package nonapi.org.paramixel.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import nonapi.org.paramixel.ConcreteConfiguration;
import nonapi.org.paramixel.support.AnsiColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("StatusListener output")
class StatusListenerOutputTest {

    private static final String ROOT_NAME = Constants.ROOT_NAME;

    @Test
    @DisplayName("constructor returns non-null instance")
    void constructorReturnsNonNullInstance() {
        assertThat(new StatusListener()).isNotNull();
    }

    @Test
    @DisplayName("prints before-action line with action details")
    void printsBeforeActionLineWithActionDetails() {
        var listener = new StatusListener();
        var noop = Step.of("my-action", context -> {});
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(noop);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).doesNotContain("RUN |");
        assertThat(result).contains("my-action");
    }

    @Test
    @DisplayName("suppresses output for root action")
    void suppressesOutputForRoot() {
        var listener = new StatusListener();
        var child = Step.of("child", context -> {});
        var parallel = Parallel.builder(ROOT_NAME).parallelism(1).child(child).build();
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(parallel);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).doesNotContain(ROOT_NAME);
    }

    @Test
    @DisplayName("prints PASSED status with green color")
    void printsSuccessfulStatusWithGreenColor() {
        var listener = new StatusListener();
        var noop = Step.of("passing-action", context -> {});
        var runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of(Configuration.ANSI, "true")))
                .listener(listener)
                .build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(noop);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains(AnsiColor.BOLD_GREEN_TEXT.format("PASSED"));
    }

    @Test
    @DisplayName("prints FAILED status with red color")
    void printsFailStatusWithRedColor() {
        var listener = new StatusListener();
        var action = Step.of("failing-action", context -> {
            throw new RuntimeException("fail");
        });
        var runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of(Configuration.ANSI, "true")))
                .listener(listener)
                .build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains(AnsiColor.BOLD_RED_TEXT.format("FAILED"));
    }

    @Test
    @DisplayName("prints SKIPPED status with orange color")
    void printsSkipStatusWithOrangeColor() {
        var listener = new StatusListener();
        var action = Step.of("skipping-action", context -> {
            throw new SkipException("skipped");
        });
        var runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of(Configuration.ANSI, "true")))
                .listener(listener)
                .build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains(AnsiColor.BOLD_YELLOW_TEXT.format("SKIPPED"));
    }

    @Test
    @DisplayName("suppresses output for root in afterAction")
    void suppressesOutputForRootInAfterAction() {
        var listener = new StatusListener();
        var parallel = Parallel.builder(ROOT_NAME)
                .parallelism(1)
                .child(Step.of("child", context -> {}))
                .build();
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(parallel);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).doesNotContain(ROOT_NAME);
    }

    @Test
    @DisplayName("prints exception details appended to FAILED status line")
    void printsExceptionDetailsAppendedToFailedStatusLine() {
        var listener = new StatusListener();
        var action = Step.of("error-action", context -> {
            throw new RuntimeException("action error");
        });
        var runner = Runner.builder().listener(listener).build();

        var outputOut = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(outputOut, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var outResult = outputOut.toString(StandardCharsets.UTF_8);
        assertThat(outResult).contains("FAILED");
        assertThat(outResult).contains("error-action");
        assertThat(outResult).contains(RuntimeException.class.getName() + ": action error");
    }

    @Test
    @DisplayName("prints EXCEPTION stack trace with prefix on each line")
    void printsExceptionStackTraceWithPrefixOnEachLine() {
        var listener = new StatusListener();
        var action = Step.of("error-action", context -> {
            throw new RuntimeException("action error");
        });
        var runner = Runner.builder().listener(listener).build();

        var outputOut = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(outputOut, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var outResult = outputOut.toString(StandardCharsets.UTF_8);
        String[] lines = outResult.split(System.lineSeparator());
        int failedLineIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("FAILED")) {
                failedLineIndex = i;
                break;
            }
        }
        assertThat(failedLineIndex).isNotEqualTo(-1);
        for (int i = failedLineIndex + 1; i < lines.length; i++) {
            var line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            assertThat(line).as("Stack trace line should start with prefix").startsWith(Constants.PARAMIXEL_PLAIN);
        }
        for (int i = failedLineIndex + 1; i < lines.length; i++) {
            var line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            assertThat(line)
                    .as("Stack trace line should not contain nonapi.org.paramixel")
                    .doesNotContain("nonapi.org.paramixel.");
            assertThat(line)
                    .as("Stack trace line should not contain org.paramixel")
                    .doesNotContain("org.paramixel.");
        }
    }

    @Test
    @DisplayName("prints FailException message and exception class in status line")
    void printsFailExceptionMessageAndExceptionClass() {
        var listener = new StatusListener();
        var action = Step.of("fail-action", context -> {
            FailException.fail("custom failure");
        });
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("FAILED");
        assertThat(result).contains("fail-action");
        assertThat(result).contains(FailException.class.getName() + ": custom failure");
    }

    @Test
    @DisplayName("prints SkipException message and exception class in status line")
    void printsSkipExceptionMessageAndExceptionClass() {
        var listener = new StatusListener();
        var action = Step.of("skip-action", context -> {
            throw new SkipException("skipped reason");
        });
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("SKIPPED");
        assertThat(result).contains("skip-action");
        assertThat(result).contains(SkipException.class.getName() + ": skipped reason");
    }

    @Test
    @DisplayName("prints AbortedException message and exception class in status line")
    void printsAbortedExceptionMessageAndExceptionClass() {
        var listener = new StatusListener();
        var action = Step.of("abort-action", context -> {
            throw new AbortedException("abort reason");
        });
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("ABORTED");
        assertThat(result).contains("abort-action");
        assertThat(result).contains(AbortedException.class.getName() + ": abort reason");
    }

    @Test
    @DisplayName("FAILED status line ends with newline before stack trace")
    void failedStatusLineEndsWithNewlineBeforeStackTrace() {
        var listener = new StatusListener();
        var action = Step.of("error-action", context -> {
            throw new RuntimeException("action error");
        });
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        String[] lines = result.split(System.lineSeparator());
        int failedLineIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("FAILED")) {
                failedLineIndex = i;
                break;
            }
        }
        assertThat(failedLineIndex).isNotEqualTo(-1);
        String failedLine = lines[failedLineIndex];
        String marker = "[PARAMIXEL]";
        int count = 0;
        int idx = 0;
        while ((idx = failedLine.indexOf(marker, idx)) != -1) {
            count++;
            idx += marker.length();
        }
        assertThat(count)
                .as(
                        "FAILED line should contain exactly one PARAMIXEL prefix, but found %d. Line: %s",
                        count, failedLine)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("suppresses output for root in actionThrowable")
    void suppressesOutputForRootInActionThrowable() {
        var listener = new StatusListener();
        var failingChild = Step.of("child-fail", context -> {
            throw new RuntimeException("child error");
        });
        var parallel =
                Parallel.builder(ROOT_NAME).parallelism(1).child(failingChild).build();
        var runner = Runner.builder().listener(listener).build();

        var outputOut = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(outputOut, true, StandardCharsets.UTF_8));
            runner.run(parallel);
        } finally {
            System.setOut(originalOut);
        }

        var outResult = outputOut.toString(StandardCharsets.UTF_8);
        assertThat(outResult).doesNotContain(ROOT_NAME);
    }

    @Test
    @DisplayName("exception message with ANSI sequences is sanitized in output")
    void exceptionMessageWithAnsiSequencesIsSanitized() {
        var listener = new StatusListener();
        var action = Step.of("ansi-fail-action", context -> {
            throw new RuntimeException("\u001B[31merror\u001B[0m");
        });
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("FAILED");
        assertThat(result).contains(RuntimeException.class.getName() + ": error");
        assertThat(result).doesNotContain("\u001B");
    }

    @Test
    @DisplayName("action display name with control characters is sanitized in output")
    void actionDisplayNameWithControlCharactersIsSanitized() {
        var listener = new StatusListener();
        var action = Step.of("clean\u0000name", context -> {});
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("cleanname");
        assertThat(result).doesNotContain("\u0000");
    }

    @Test
    @DisplayName("exception message with null bytes is sanitized in output")
    void exceptionMessageWithNullBytesIsSanitized() {
        var listener = new StatusListener();
        var action = Step.of("null-fail-action", context -> {
            throw new RuntimeException("bad\u0000message\u0000");
        });
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("FAILED");
        assertThat(result).contains(RuntimeException.class.getName() + ": badmessage");
        assertThat(result).doesNotContain("\u0000");
    }

    @Test
    @DisplayName("stack trace preserves tab indentation while stripping control characters")
    void stackTracePreservesTabIndentationWhileStrippingControlCharacters() {
        var listener = new StatusListener();
        var action = Step.of("indent-action", context -> {
            throw new RuntimeException("boom\u0000");
        });
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("FAILED");
        assertThat(result).doesNotContain("\u0000");
        assertThat(result).contains("\tat ");
    }

    @Test
    @DisplayName("stack trace first line strips ANSI escapes from exception message")
    void stackTraceFirstLineStripsAnsiFromExceptionMessage() {
        var listener = new StatusListener();
        var action = Step.of("ansi-trace-action", context -> {
            throw new RuntimeException("\u001B[31mboom\u001B[0m");
        });
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains(RuntimeException.class.getName() + ": boom");
        assertThat(result).doesNotContain("\u001B");
        assertThat(result).contains("\tat ");
    }

    @Test
    @DisplayName("output is free of grep-binary triggers for a control-char exception message")
    void outputIsFreeOfGrepBinaryTriggers() {
        var listener = new StatusListener();
        var action = Step.of("grep-action", context -> {
            throw new RuntimeException("bad\u0000\u0007\u001B[31mmsg\u001B[0m");
        });
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).doesNotContain("\u0000");
        assertThat(result).doesNotContain("\u0007");
        assertThat(result).doesNotContain("\u001B");
        assertThat(result).contains(RuntimeException.class.getName() + ": badmsg");
    }

    @Test
    @DisplayName("null throwable message does not render null text")
    void nullThrowableMessageDoesNotRenderNullText() {
        var action = Step.of("throws", context -> {
            throw new RuntimeException();
        });
        var listener = new StatusListener();
        var configuration = new ConcreteConfiguration(Map.of(Configuration.ANSI, "false"));
        listener.initialize(configuration);
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).doesNotContain("RuntimeException: null");
        assertThat(result).contains("RuntimeException");
    }
}

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
import nonapi.org.paramixel.listener.support.Constants;
import nonapi.org.paramixel.support.AnsiColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Step;
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
        StatusListener listener = new StatusListener();
        Step<?> noop = Step.of("my-action", obj -> {});
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
        assertThat(result).doesNotContain("RUN |");
        assertThat(result).contains("my-action");
        assertThat(result).contains("Step");
    }

    @Test
    @DisplayName("suppresses output for root action")
    void suppressesOutputForRoot() {
        StatusListener listener = new StatusListener();
        Action<?> child = Step.of("child", obj -> {});
        Parallel<?> parallel =
                Parallel.of(ROOT_NAME).parallelism(1).child(child).resolve();
        Runner runner = Runner.builder().listener(listener).build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(parallel);
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).doesNotContain(ROOT_NAME);
    }

    @Test
    @DisplayName("prints PASSED status with green color")
    void printsSuccessfulStatusWithGreenColor() {
        StatusListener listener = new StatusListener();
        Step<?> noop = Step.of("passing-action", obj -> {});
        Runner runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of(Configuration.ANSI, "true")))
                .listener(listener)
                .build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(noop);
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains(AnsiColor.BOLD_GREEN_TEXT.format("PASSED"));
    }

    @Test
    @DisplayName("prints FAILED status with red color")
    void printsFailStatusWithRedColor() {
        StatusListener listener = new StatusListener();
        Action<?> action = Step.of("failing-action", context -> {
            throw new RuntimeException("fail");
        });
        Runner runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of(Configuration.ANSI, "true")))
                .listener(listener)
                .build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains(AnsiColor.BOLD_RED_TEXT.format("FAILED"));
    }

    @Test
    @DisplayName("prints SKIPPED status with orange color")
    void printsSkipStatusWithOrangeColor() {
        StatusListener listener = new StatusListener();
        Action<?> action = Step.of("skipping-action", context -> {
            throw new SkipException("skipped");
        });
        Runner runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of(Configuration.ANSI, "true")))
                .listener(listener)
                .build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains(AnsiColor.BOLD_YELLOW_TEXT.format("SKIPPED"));
    }

    @Test
    @DisplayName("suppresses output for root in afterAction")
    void suppressesOutputForRootInAfterAction() {
        StatusListener listener = new StatusListener();
        Parallel<?> parallel = Parallel.of(ROOT_NAME)
                .parallelism(1)
                .child(Step.of("child", obj -> {}))
                .resolve();
        Runner runner = Runner.builder().listener(listener).build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(parallel);
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).doesNotContain(ROOT_NAME);
    }

    @Test
    @DisplayName("prints EXCEPTION line to System.err")
    void printsExceptionLineToStderr() {
        StatusListener listener = new StatusListener();
        Action<?> action = Step.of("error-action", context -> {
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
    @DisplayName("suppresses output for root in actionThrowable")
    void suppressesOutputForRootInActionThrowable() {
        StatusListener listener = new StatusListener();
        Action<?> failingChild = Step.of("child-fail", context -> {
            throw new RuntimeException("child error");
        });
        Parallel<?> parallel =
                Parallel.of(ROOT_NAME).parallelism(1).child(failingChild).resolve();
        Runner runner = Runner.builder().listener(listener).build();

        ByteArrayOutputStream outputErr = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        PrintStream originalOut = System.out;
        try {
            System.setErr(new PrintStream(outputErr, true, StandardCharsets.UTF_8));
            runner.run(parallel);
        } finally {
            System.setErr(originalErr);
            System.setOut(originalOut);
        }

        String errResult = outputErr.toString(StandardCharsets.UTF_8);
        assertThat(errResult).doesNotContain(ROOT_NAME);
    }
}

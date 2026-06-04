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
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Stream;
import nonapi.org.paramixel.ConcreteConfiguration;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;

@DisplayName("StatusListener excludes")
class StatusListenerExcludesTest {

    @Test
    @DisplayName("constructor returns non-null instance")
    void constructorReturnsNonNullInstance() {
        assertThat(new StatusListener()).isNotNull();
    }

    @Test
    @DisplayName("prints normal output when excludes is empty")
    void normalOutputWhenNoExcludes() {
        StatusListener listener = new StatusListener();
        Step noop = Step.of("normal-action", context -> {});
        Runner runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of()))
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
        assertThat(result).contains("normal-action");
    }

    @Test
    @DisplayName("suppresses onBeforeExecution and onAfterExecution when STATUS excluded")
    void suppressesStatusLinesWhenStatusExcluded() {
        StatusListener listener = new StatusListener();
        Step noop = Step.of("silent-action", context -> {});
        Runner runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of(Configuration.LISTENER_EXCLUDE, "status")))
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
        assertThat(result).doesNotContain("silent-action");
    }

    @Test
    @DisplayName("suppresses status lines but prints exception when STATUS excluded")
    void suppressesStatusLinesButPrintsExceptionWhenStatusExcluded() {
        StatusListener listener = new StatusListener();
        Action action = Step.of("test-action", context -> {
            throw new RuntimeException("test error");
        });
        Runner runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of(Configuration.LISTENER_EXCLUDE, "status")))
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
        assertThat(result).doesNotContain("PASSED");
        assertThat(result).doesNotContain("FAILED");
        assertThat(result).doesNotContain("test-action");
        assertThat(result).contains(RuntimeException.class.getName() + ": test error");
    }

    @Test
    @DisplayName("prints stack trace to stdout when STATUS_FOOTER excluded")
    void printsStackTraceToStdoutWhenStatusFooterExcluded() {
        StatusListener listener = new StatusListener();
        Action action = Step.of("error-action", context -> {
            throw new RuntimeException("expected error");
        });
        Runner runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of(Configuration.LISTENER_EXCLUDE, "status.footer")))
                .listener(listener)
                .build();

        ByteArrayOutputStream outputOut = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outputOut, true, StandardCharsets.UTF_8));
            runner.run(action);
        } finally {
            System.setOut(originalOut);
        }

        String outResult = outputOut.toString(StandardCharsets.UTF_8);
        assertThat(outResult).contains("error-action");
        assertThat(outResult).contains(RuntimeException.class.getName() + ": expected error");
    }

    @Test
    @DisplayName("quiet shorthand suppresses status lines")
    void quietShorthandSuppressesStatus() {
        StatusListener listener = new StatusListener();
        Step noop = Step.of("quiet-action", context -> {});
        Runner runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of(Configuration.LISTENER_EXCLUDE, "quiet")))
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
        assertThat(result).doesNotContain("quiet-action");
    }

    @Test
    @DisplayName("suppresses onBeforeExecution but keeps onAfterExecution when STATUS_HEADER excluded")
    void suppressesBeforeButNotAfterWhenStatusHeaderExcluded() {
        StatusListener listener = new StatusListener();
        Step noop = Step.of("header-split", context -> {});
        Runner runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of(Configuration.LISTENER_EXCLUDE, "status.header")))
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
        assertThat(result).contains("PASSED");
        assertThat(result).contains("header-split");
    }

    @Test
    @DisplayName("suppresses onAfterExecution but keeps onBeforeExecution when STATUS_FOOTER excluded")
    void suppressesAfterButNotBeforeWhenStatusFooterExcluded() {
        StatusListener listener = new StatusListener();
        Step noop = Step.of("footer-split", context -> {});
        Runner runner = Runner.builder()
                .configuration(new ConcreteConfiguration(Map.of(Configuration.LISTENER_EXCLUDE, "status.footer")))
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
        assertThat(result).doesNotContain("PASSED");
        assertThat(result).contains("footer-split");
    }

    @ParameterizedTest
    @MethodSource("allStatusCombinations")
    @DisplayName("exhaustively guards with correct combinations of STATUS_HEADER and STATUS_FOOTER")
    void allStatusCombinations(EnumSet<ExcludeTarget> excludeSet) {
        var configValue = new StringBuilder();
        for (var target : excludeSet) {
            if (configValue.length() > 0) {
                configValue.append(",");
            }
            configValue.append(target.name().toLowerCase().replace('_', '.'));
        }

        var listener = new StatusListener();
        listener.initialize(new ConcreteConfiguration(Map.of(Configuration.LISTENER_EXCLUDE, configValue.toString())));
        var descriptor = new ConcreteDescriptor(Step.of("combo-action", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.PASSED);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            listener.onBeforeExecution(descriptor);
            listener.onAfterExecution(descriptor);
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        boolean expectBefore = !excludeSet.contains(ExcludeTarget.STATUS_HEADER);
        boolean expectAfter = !excludeSet.contains(ExcludeTarget.STATUS_FOOTER);

        if (expectBefore || expectAfter) {
            assertThat(result).contains("combo-action");
        } else {
            assertThat(result).doesNotContain("combo-action");
        }

        if (expectAfter) {
            assertThat(result).contains("PASSED");
        } else {
            assertThat(result).doesNotContain("PASSED");
        }
    }

    private static Stream<Arguments> allStatusCombinations() {
        return Stream.of(
                Arguments.of(EnumSet.noneOf(ExcludeTarget.class)),
                Arguments.of(EnumSet.of(ExcludeTarget.STATUS_HEADER)),
                Arguments.of(EnumSet.of(ExcludeTarget.STATUS_FOOTER)),
                Arguments.of(EnumSet.of(ExcludeTarget.STATUS_HEADER, ExcludeTarget.STATUS_FOOTER)));
    }
}

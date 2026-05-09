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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.support.AnsiColor;

@DisplayName("StatusListener")
class StatusListenerTest {

    private static final String ROOT_NAME = Constants.ROOT_NAME;

    @Test
    @DisplayName("constructor returns non-null instance")
    void constructorReturnsNonNullInstance() {
        assertThat(new StatusListener()).isNotNull();
    }

    @Nested
    @DisplayName("beforeAction")
    class BeforeActionTests {

        @Test
        @DisplayName("prints RUN line with action details")
        void printsTestLine() {
            StatusListener listener = new StatusListener();
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
            assertThat(result).contains("RUN ");
            assertThat(result).contains("my-action");
            assertThat(result).contains("Noop");
        }

        @Test
        @DisplayName("suppresses output for root action")
        void suppressesOutputForRoot() {
            StatusListener listener = new StatusListener();
            Action child = Noop.of("child");
            Parallel parallel =
                    Parallel.builder(ROOT_NAME).parallelism(1).child(child).build();
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
            assertThat(result).doesNotContain("RUN  | " + ROOT_NAME);
        }
    }

    @Nested
    @DisplayName("afterAction")
    class AfterActionTests {

        @Test
        @DisplayName("prints PASS status with green color")
        void printsPassStatusWithGreenColor() {
            StatusListener listener = new StatusListener();
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
            StatusListener listener = new StatusListener();
            Action action = Direct.builder("failing-action")
                    .execute(context -> {
                        throw new RuntimeException("fail");
                    })
                    .build();
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
            StatusListener listener = new StatusListener();
            Action action = Direct.builder("skipping-action")
                    .execute(context -> {
                        throw org.paramixel.core.exception.SkipException.of("skipped");
                    })
                    .build();
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
        @DisplayName("suppresses output for root in afterAction")
        void suppressesOutputForRootInAfterAction() {
            StatusListener listener = new StatusListener();
            Parallel parallel = Parallel.builder(ROOT_NAME)
                    .parallelism(1)
                    .child(Noop.of("child"))
                    .build();
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
    }

    @Nested
    @DisplayName("actionThrowable")
    class ActionThrowableTests {

        @Test
        @DisplayName("prints EXCEPTION line to System.err")
        void printsExceptionLineToStderr() {
            StatusListener listener = new StatusListener();
            Action action = Direct.builder("error-action")
                    .execute(context -> {
                        throw new RuntimeException("action error");
                    })
                    .build();
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
            Action failingChild = Direct.builder("child-fail")
                    .execute(context -> {
                        throw new RuntimeException("child error");
                    })
                    .build();
            Parallel parallel = Parallel.builder(ROOT_NAME)
                    .parallelism(1)
                    .child(failingChild)
                    .build();
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

    @Nested
    @DisplayName("action path and kind resolution")
    class ActionPathAndKindTests {

        @Test
        @DisplayName("shows simple name for core action classes")
        void showsSimpleNameForCoreActionClasses() {
            StatusListener listener = new StatusListener();
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
            StatusListener listener = new StatusListener();
            Action custom = Noop.of("custom");
            Runner runner = Runner.builder().listener(listener).build();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
                runner.run(custom);
            } finally {
                System.setOut(originalOut);
            }

            String result = output.toString(StandardCharsets.UTF_8);
            assertThat(result).contains("Noop");
        }

        @Test
        @DisplayName("id path excludes root ancestors")
        void idPathExcludesRootAncestors() {
            StatusListener listener = new StatusListener();
            Action child = Noop.of("visible-child");
            Parallel parallel =
                    Parallel.builder(ROOT_NAME).parallelism(1).child(child).build();
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
            assertThat(result).contains("visible-child");
            String childLine = result.lines()
                    .filter(line -> line.contains("visible-child"))
                    .findFirst()
                    .orElse("");
            assertThat(childLine).contains(child.getId());
            assertThat(childLine).doesNotContain(ROOT_NAME);
        }

        @Test
        @DisplayName("action path excludes root ancestors")
        void actionPathExcludesRootAncestors() {
            StatusListener listener = new StatusListener();
            Action child = Noop.of("visible-child");
            Parallel parallel =
                    Parallel.builder(ROOT_NAME).parallelism(1).child(child).build();
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
            assertThat(result).contains("visible-child");
            assertThat(result).doesNotContain(ROOT_NAME);
        }

        @Test
        @DisplayName("nested action id path includes parent id")
        void nestedActionIdPathIncludesParentId() {
            StatusListener listener = new StatusListener();
            Action child = Noop.of("child");
            Container parent = Container.builder("parent")
                    .policy(Container.Policy.builder()
                            .childMode(Container.ChildMode.INDEPENDENT)
                            .build())
                    .child(child)
                    .build();
            Runner runner = Runner.builder().listener(listener).build();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
                runner.run(parent);
            } finally {
                System.setOut(originalOut);
            }

            String result = output.toString(StandardCharsets.UTF_8);
            String childLine = result.lines()
                    .filter(line -> line.contains("child") && line.contains("Noop"))
                    .findFirst()
                    .orElse("");
            assertThat(childLine).contains(parent.getId() + "-" + child.getId());
        }

        @Test
        @DisplayName("nested action path includes parent name")
        void nestedActionPathIncludesParentName() {
            StatusListener listener = new StatusListener();
            Action child = Noop.of("child");
            Container parent = Container.builder("parent")
                    .policy(Container.Policy.builder()
                            .childMode(Container.ChildMode.INDEPENDENT)
                            .build())
                    .child(child)
                    .build();
            Runner runner = Runner.builder().listener(listener).build();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
                runner.run(parent);
            } finally {
                System.setOut(originalOut);
            }

            String result = output.toString(StandardCharsets.UTF_8);
            assertThat(result).contains("parent / child (Noop)");
        }

        @Test
        @DisplayName("deep nesting produces full hierarchical path")
        void deepNestingProducesFullHierarchicalPath() {
            StatusListener listener = new StatusListener();
            Action leaf = Noop.of("leaf");
            Action mid = Container.builder("mid")
                    .policy(Container.Policy.builder()
                            .childMode(Container.ChildMode.INDEPENDENT)
                            .build())
                    .child(leaf)
                    .build();
            Action top = Container.builder("top")
                    .policy(Container.Policy.builder()
                            .childMode(Container.ChildMode.INDEPENDENT)
                            .build())
                    .child(mid)
                    .build();
            Runner runner = Runner.builder().listener(listener).build();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
                runner.run(top);
            } finally {
                System.setOut(originalOut);
            }

            String result = output.toString(StandardCharsets.UTF_8);
            String leafLine = result.lines()
                    .filter(line -> line.contains("leaf") && line.contains("Noop"))
                    .findFirst()
                    .orElse("");
            assertThat(leafLine).contains(top.getId() + "-" + mid.getId() + "-" + leaf.getId());
            assertThat(leafLine).contains("top / mid / leaf (Noop)");
        }
    }

    @Nested
    @DisplayName("timing format")
    class TimingFormatTests {

        @Test
        @DisplayName("timing shows milliseconds")
        void timingShowsMilliseconds() {
            StatusListener listener = new StatusListener();
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

    @Test
    @DisplayName("callback methods reject null arguments")
    void callbackMethodsRejectNullArguments() {
        StatusListener listener = new StatusListener();
        Runner runner = Runner.builder().build();
        var result = runner.run(Noop.of("test"));

        assertThatThrownBy(() -> listener.beforeAction(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("result must not be null");
        assertThatThrownBy(() -> listener.afterAction(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("result must not be null");
        assertThatThrownBy(() -> listener.skipAction(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("result must not be null");
        assertThatThrownBy(() -> listener.actionThrowable(null, new RuntimeException("boom")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("result must not be null");
        assertThatThrownBy(() -> listener.actionThrowable(result, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("throwable must not be null");
    }
}

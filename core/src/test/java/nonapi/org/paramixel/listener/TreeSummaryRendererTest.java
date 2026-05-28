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
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.listener.support.Constants;
import nonapi.org.paramixel.support.AnsiColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;
import org.paramixel.api.Version;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Static;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.SkipException;

@DisplayName("TreeSummaryRenderer")
class TreeSummaryRendererTest {

    private static final String ROOT_NAME = Constants.ROOT_NAME;

    private String runAndCaptureOutput(final Action<?> action) {
        var listener = new SummaryListener();
        var config = new ConcreteConfiguration(Map.of(Configuration.ANSI, "true"));
        listener.initialize(config);
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

    private String runAndCapturePlainOutput(final Action<?> action) {
        var listener = new SummaryListener();
        var config = new ConcreteConfiguration(Map.of(Configuration.ANSI, "false"));
        listener.initialize(config);
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

    @Test
    @DisplayName("root node name appears in output")
    void rootNodeNameAppearsInOutput() {
        Action<?> child = Step.of("visible-child", obj -> {});
        Parallel<?> parallel =
                Parallel.of(ROOT_NAME).parallelism(1).child(child).resolve();
        String output = runAndCaptureOutput(parallel);

        assertThat(output).contains("visible-child");
    }

    @Test
    @DisplayName("non-hidden root rendered")
    void nonHiddenRootRendered() {
        Step<?> noop = Step.of("my-root", obj -> {});
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("my-root");
    }

    @Test
    @DisplayName("single action renders with PASSED status")
    void singleActionWithNoChildren() {
        Step<?> noop = Step.of("leaf", obj -> {});
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("leaf");
        assertThat(output).contains("PASSED");
    }

    @Test
    @DisplayName("PASSED status rendered with green")
    void successfulStatusRenderedWithGreen() {
        Step<?> noop = Step.of("passing", obj -> {});
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains(AnsiColor.BOLD_GREEN_TEXT.format("PASSED"));
    }

    @Test
    @DisplayName("FAILED status rendered with red")
    void failStatusRenderedWithRed() {
        Action<?> failing = Step.of("failing", context -> {
            throw new RuntimeException("error");
        });
        String output = runAndCaptureOutput(failing);

        assertThat(output).contains(AnsiColor.BOLD_RED_TEXT.format("FAILED"));
    }

    @Test
    @DisplayName("SKIPPED status rendered with bold yellow")
    void skipStatusRenderedWithBoldYellow() {
        Action<?> skipping = Step.of("skipper", context -> {
            throw new SkipException("reason");
        });
        String output = runAndCaptureOutput(skipping);

        assertThat(output).contains(AnsiColor.BOLD_YELLOW_TEXT.format("SKIPPED"));
    }

    @Test
    @DisplayName("plain output disables ANSI formatting")
    void plainOutputDisablesAnsiFormatting() {
        String output = runAndCapturePlainOutput(Step.of("plain", obj -> {}));

        assertThat(output).contains(Constants.PARAMIXEL_PLAIN);
        assertThat(output).contains("PASSED");
        assertThat(output).doesNotContain("\u001B[");
    }

    @Test
    @DisplayName("failure info shows exception class and message on same line")
    void failureInfoWithThrowable() {
        Action<?> failing = Step.of("fail-action", context -> {
            throw new IllegalStateException("bad state");
        });
        String output = runAndCaptureOutput(failing);

        assertThat(output).contains("java.lang.IllegalStateException");
        assertThat(output).contains("bad state");
    }

    @Test
    @DisplayName("failure info with multi-line message renders on single line")
    void failureInfoWithMultiLineMessageRendersOnSingleLine() {
        Action<?> failing = Step.of("multiline-fail", context -> {
            throw new RuntimeException("\nline2\nline3");
        });
        String output = runAndCaptureOutput(failing);

        assertThat(output).contains("java.lang.RuntimeException");
        assertThat(output).contains("line2 - line3");
    }

    @Test
    @DisplayName("kind shows simple name for core actions")
    void kindShowsSimpleNameForCoreActions() {
        Step<?> noop = Step.of("noop-action", obj -> {});
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("(Step)");
    }

    @Test
    @DisplayName("timing shows milliseconds")
    void timingShowsMilliseconds() {
        Step<?> noop = Step.of("timed", obj -> {});
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains("ms");
    }

    @Test
    @DisplayName("full action names are preserved without truncation")
    void fullActionNamesPreserved() {
        String thirtyFiveCharName = "a".repeat(35);
        Step<?> noop = Step.of(thirtyFiveCharName, obj -> {});
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains(thirtyFiveCharName);
    }

    @Test
    @DisplayName("two children rendered with tree connectors")
    void twoChildrenRenderedWithTreeConnectors() {
        Action<?> child1 = Step.of("child-1", obj -> {});
        Action<?> child2 = Step.of("child-2", obj -> {});
        Sequential<?> seq = Sequential.of("parent").child(child1).child(child2).resolve();
        String output = runAndCaptureOutput(seq);

        assertThat(output).contains("child-1");
        assertThat(output).contains("child-2");
        assertThat(output).contains("parent");
    }

    @Test
    @DisplayName("lifecycle with before and after renders with standard connectors")
    void lifecycleWithBeforeAndAfterRendersStandardConnectors() {
        Action<?> before = Step.of("before", ctx -> {});
        Action<?> after = Step.of("after", ctx -> {});
        Lifecycle<?> lifecycle =
                Lifecycle.of("myLifecycle").before(before).after(after).resolve();
        String output = runAndCapturePlainOutput(lifecycle);

        assertThat(output).contains("\u251C\u2500 PASSED before");
        assertThat(output).contains("\u2514\u2500 PASSED after");
    }

    @Test
    @DisplayName("lifecycle with before, body, and after nests body under before")
    void lifecycleWithBeforeBodyAfterNestsBodyUnderBefore() {
        Action<?> before = Step.of("setUp()", ctx -> {});
        Action<?> body = Step.of("testGet()", ctx -> {});
        Action<?> after = Step.of("tearDown()", ctx -> {});
        Lifecycle<?> lifecycle = Lifecycle.of("myLifecycle")
                .before(before)
                .child(body)
                .after(after)
                .resolve();
        String output = runAndCapturePlainOutput(lifecycle);

        assertThat(output).contains("\u251C\u2500 PASSED setUp()");
        assertThat(output).contains("\u2502  \u2514\u2500 PASSED testGet()");
        assertThat(output).contains("\u2514\u2500 PASSED tearDown()");
    }

    @Test
    @DisplayName("lifecycle with before, multiple body, and after nests all body under before")
    void lifecycleWithBeforeMultipleBodyAfterNestsAllBodyUnderBefore() {
        Action<?> before = Step.of("setUp()", ctx -> {});
        Action<?> body1 = Step.of("testA()", ctx -> {});
        Action<?> body2 = Step.of("testB()", ctx -> {});
        Action<?> after = Step.of("tearDown()", ctx -> {});
        Lifecycle<?> lifecycle = Lifecycle.of("myLifecycle")
                .before(before)
                .child(body1)
                .child(body2)
                .after(after)
                .resolve();
        String output = runAndCapturePlainOutput(lifecycle);

        assertThat(output).contains("\u251C\u2500 PASSED setUp()");
        assertThat(output).contains("\u2502  \u251C\u2500 PASSED testA()");
        assertThat(output).contains("\u2502  \u2514\u2500 PASSED testB()");
        assertThat(output).contains("\u2514\u2500 PASSED tearDown()");
    }

    @Test
    @DisplayName("lifecycle with before and no after renders with standard connectors")
    void lifecycleWithBeforeNoAfterRendersStandardConnectors() {
        Action<?> before = Step.of("before", ctx -> {});
        Lifecycle<?> lifecycle = Lifecycle.of("myLifecycle").before(before).resolve();
        String output = runAndCapturePlainOutput(lifecycle);

        assertThat(output).contains("\u2514\u2500 PASSED before");
    }

    @Test
    @DisplayName("lifecycle with before, body, and no after nests body under before")
    void lifecycleWithBeforeBodyNoAfterNestsBodyUnderBefore() {
        Action<?> before = Step.of("setUp()", ctx -> {});
        Action<?> body = Step.of("testGet()", ctx -> {});
        Lifecycle<?> lifecycle =
                Lifecycle.of("myLifecycle").before(before).child(body).resolve();
        String output = runAndCapturePlainOutput(lifecycle);

        assertThat(output).contains("\u251C\u2500 PASSED setUp()");
        assertThat(output).contains("\u2502  \u2514\u2500 PASSED testGet()");
    }

    @Test
    @DisplayName("lifecycle with after and no before renders with standard connectors")
    void lifecycleWithAfterNoBeforeRendersStandardConnectors() {
        Action<?> after = Step.of("after", ctx -> {});
        Lifecycle<?> lifecycle = Lifecycle.of("myLifecycle").after(after).resolve();
        String output = runAndCapturePlainOutput(lifecycle);

        assertThat(output).contains("\u2514\u2500 PASSED after");
    }

    @Test
    @DisplayName("lifecycle with body and after but no before renders body flat with after last")
    void lifecycleWithBodyAfterNoBeforeRendersBodyFlatWithAfterLast() {
        Action<?> body = Step.of("testGet()", ctx -> {});
        Action<?> after = Step.of("tearDown()", ctx -> {});
        Lifecycle<?> lifecycle =
                Lifecycle.of("myLifecycle").child(body).after(after).resolve();
        String output = runAndCapturePlainOutput(lifecycle);

        assertThat(output).contains("\u251C\u2500 PASSED testGet()");
        assertThat(output).contains("\u2514\u2500 PASSED tearDown()");
    }

    @Test
    @DisplayName("lifecycle with setUp and tearDown names renders with standard connectors")
    void lifecycleWithSetUpTearDownNamesRendersStandardConnectors() {
        Action<?> before = Step.of("setUp()", ctx -> {});
        Action<?> after = Step.of("tearDown()", ctx -> {});
        Lifecycle<?> lifecycle =
                Lifecycle.of("myLifecycle").before(before).after(after).resolve();
        String output = runAndCapturePlainOutput(lifecycle);

        assertThat(output).contains("\u251C\u2500 PASSED setUp()");
        assertThat(output).contains("\u2514\u2500 PASSED tearDown()");
    }

    @Test
    @DisplayName("instance with lifecycle body child renders deeply nested tree")
    void instanceWithLifecycleBodyChildRendersDeeplyNestedTree() {
        Action<?> lifecycleBefore = Step.of("setUp()", ctx -> {});
        Action<?> lifecycleBody = Step.of("testGet()", ctx -> {});
        Action<?> lifecycleAfter = Step.of("tearDown()", ctx -> {});
        Lifecycle<?> lifecycle = Lifecycle.of("lifecycle")
                .before(lifecycleBefore)
                .child(lifecycleBody)
                .after(lifecycleAfter)
                .resolve();
        Instance<?> instance = Instance.<String>of("nginx:1.29.5", () -> "instance")
                .child(lifecycle)
                .resolve();
        Parallel<?> parallel =
                Parallel.of("root").parallelism(1).child(instance).resolve();
        String output = runAndCapturePlainOutput(parallel);

        assertThat(output).contains("\u251C\u2500 PASSED Instantiate (Instantiate)");
        assertThat(output).contains("\u2502  \u2514\u2500 PASSED lifecycle (Lifecycle)");
        assertThat(output).contains("\u2502     \u251C\u2500 PASSED setUp() (Step)");
        assertThat(output).contains("\u2502     \u2502  \u2514\u2500 PASSED testGet() (Step)");
        assertThat(output).contains("\u2502     \u2514\u2500 PASSED tearDown() (Step)");
        assertThat(output).contains("\u2514\u2500 PASSED Destroy (Destroy)");
    }

    @Test
    @DisplayName("static with before and after nests body children under before")
    void staticWithBeforeAndAfterNestsBodyUnderBefore() {
        Action<?> staticAction = Static.of("myStatic")
                .before("staticSetUp", () -> {})
                .child(Step.of("testOne", ctx -> {}))
                .child(Step.of("testTwo", ctx -> {}))
                .after("staticTearDown", () -> {})
                .resolve();
        String output = runAndCapturePlainOutput(staticAction);

        assertThat(output).contains("\u251C\u2500 PASSED staticSetUp");
        assertThat(output).contains("\u2502  \u251C\u2500 PASSED testOne");
        assertThat(output).contains("\u2502  \u2514\u2500 PASSED testTwo");
        assertThat(output).contains("\u2514\u2500 PASSED staticTearDown");
    }

    @Test
    @DisplayName("instance renders instantiate and destroy with nested body children")
    void instanceRendersWithNestedBodyChildren() {
        Instance<?> instance = Instance.<String>of("myInstance", () -> "instance")
                .child("setUp", s -> {})
                .child("test produce", s -> {})
                .child("test consume", s -> {})
                .child("tearDown", s -> {})
                .resolve();
        Parallel<?> parallel =
                Parallel.of("root").parallelism(1).child(instance).resolve();
        String output = runAndCapturePlainOutput(parallel);

        assertThat(output).contains("(Instance)");
        assertThat(output).contains("Instantiate (Instantiate)");
        assertThat(output).contains("setUp (Step)");
        assertThat(output).contains("test produce (Step)");
        assertThat(output).contains("test consume (Step)");
        assertThat(output).contains("tearDown (Step)");
        assertThat(output).contains("Destroy (Destroy)");
        assertThat(output).contains("\u251C\u2500 PASSED Instantiate (Instantiate)");
        assertThat(output).contains("\u2502  \u251C\u2500 PASSED setUp (Step)");
        assertThat(output).contains("\u2502  \u251C\u2500 PASSED test produce (Step)");
        assertThat(output).contains("\u2502  \u251C\u2500 PASSED test consume (Step)");
        assertThat(output).contains("\u2502  \u2514\u2500 PASSED tearDown (Step)");
        assertThat(output).contains("\u2514\u2500 PASSED Destroy (Destroy)");
    }

    @Test
    @DisplayName("renderer handles deep user-generated descriptor chains without recursion overflow")
    void rendererHandlesDeepUserGeneratedDescriptorChains() {
        final int depth = 2500;
        var root = createDeepLinearDescriptorTree(depth);
        var renderer = new TreeSummaryRenderer(false);

        String output = renderer.render(root);

        assertThat(output).contains("depth-0");
        assertThat(output).contains("depth-" + (depth - 1));
        assertThat(output.split(System.lineSeparator())).hasSize(depth);
    }

    @Test
    @DisplayName("onRunStarted prints starting banner")
    void onRunStartedPrintsStartingBanner() {
        Step<?> noop = Step.of("banner-test", obj -> {});
        String output = runAndCapturePlainOutput(noop);

        assertThat(output).contains("Paramixel v" + Version.version() + " starting...");
    }

    @Test
    @DisplayName("onRunCompleted prints footer with status, finished-at, and total time")
    void onRunCompletedPrintsFooter() {
        Step<?> noop = Step.of("footer-test", obj -> {});
        String output = runAndCapturePlainOutput(noop);

        assertThat(output).contains("Status      : PASSED");
        assertThat(output).contains("Finished at :");
        assertThat(output).contains("Total time  :");
    }

    @Test
    @DisplayName("footer uses ANSI-colored status when ANSI is enabled")
    void footerUsesAnsiStatusWhenEnabled() {
        Step<?> noop = Step.of("ansi-footer", obj -> {});
        String output = runAndCaptureOutput(noop);

        assertThat(output).contains(AnsiColor.BOLD_GREEN_TEXT.format("PASSED"));
    }

    @Test
    @DisplayName("footer uses plain status when ANSI is disabled")
    void footerUsesPlainStatusWhenDisabled() {
        Step<?> noop = Step.of("plain-footer", obj -> {});
        String output = runAndCapturePlainOutput(noop);

        assertThat(output).contains("Status      : PASSED");
        assertThat(output).doesNotContain("\u001B[");
    }

    @Test
    @DisplayName("onRunCompleted prints dashed separators around footer")
    void onRunCompletedPrintsDashedSeparators() {
        Step<?> noop = Step.of("separator-test", obj -> {});
        String output = runAndCapturePlainOutput(noop);

        assertThat(output).contains("----------");
    }

    @Test
    @DisplayName("each output line is prefixed with [PARAMIXEL]")
    void eachLinePrefixedWithParamixel() {
        Step<?> noop = Step.of("prefix-test", obj -> {});
        String output = runAndCapturePlainOutput(noop);

        for (var line : output.split(System.lineSeparator())) {
            if (!line.isEmpty() && !line.startsWith("ACTIVE THREAD COUNT ")) {
                assertThat(line).startsWith("[PARAMIXEL] ");
            }
        }
    }

    @Test
    void renderRejectsNullDescriptor() {
        var renderer = new TreeSummaryRenderer();
        String output = renderer.render(null);

        assertThat(output).contains("No Paramixel tests found");
    }

    private static ConcreteDescriptor createDeepLinearDescriptorTree(final int depth) {
        var root = new ConcreteDescriptor(Step.of("depth-0", ctx -> {}));
        var current = root;
        for (var index = 1; index < depth; index++) {
            var child = new ConcreteDescriptor(Step.of("depth-" + index, ctx -> {}));
            current.addChild(child);
            current = child;
        }
        return root;
    }
}

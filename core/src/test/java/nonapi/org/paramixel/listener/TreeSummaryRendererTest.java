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
import nonapi.org.paramixel.support.AnsiColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;
import org.paramixel.api.Version;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Static;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.SkipException;

@DisplayName("TreeSummaryRenderer")
@SuppressWarnings("removal")
class TreeSummaryRendererTest {

    private static final String ROOT_NAME = Constants.ROOT_NAME;

    private String runAndCaptureOutput(final Action action) {
        var listener = new SummaryListener();
        var configuration = new ConcreteConfiguration(Map.of(Configuration.ANSI, "true"));
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

        return output.toString(StandardCharsets.UTF_8);
    }

    private String runAndCapturePlainOutput(final Action action) {
        var listener = new SummaryListener();
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
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("synthetic root node is hidden")
    void syntheticRootNodeIsHidden() {
        var child = Step.of("visible-child", context -> {});
        var parallel = Parallel.builder(ROOT_NAME).parallelism(1).child(child).build();
        var output = runAndCapturePlainOutput(parallel);

        assertThat(output).contains("visible-child");
        assertThat(output).doesNotContain("PASSED " + ROOT_NAME);
    }

    @Test
    @DisplayName("synthetic root renders lifecycle slots as top-level siblings")
    void syntheticRootRendersLifecycleSlotsAsTopLevelSiblings() {
        var root = Scope.builder(ROOT_NAME)
                .before(Sequence.builder("BeforeAll hooks")
                        .child(Step.of("beforeOne", context -> {}))
                        .child(Step.of("beforeTwo", context -> {}))
                        .build())
                .body(Step.of("testOne()", context -> {}))
                .after(Sequence.builder("AfterAll hooks")
                        .child(Step.of("afterOne", context -> {}))
                        .child(Step.of("afterTwo", context -> {}))
                        .build())
                .build();
        var output = runAndCapturePlainOutput(root);

        assertThat(output).doesNotContain("PASSED " + ROOT_NAME);
        assertThat(output).contains("PASSED Run");
        assertThat(output).contains("\u251C\u2500 before[sequence]: PASSED BeforeAll hooks");
        assertThat(output).contains("\u2502  \u251C\u2500 step: PASSED beforeOne");
        assertThat(output).contains("\u2502  \u2514\u2500 step: PASSED beforeTwo");
        assertThat(output).contains("\u251C\u2500 body[step]: PASSED testOne()");
        assertThat(output).contains("\u2514\u2500 after[sequence]: PASSED AfterAll hooks");
        assertThat(output).contains("   \u251C\u2500 step: PASSED afterOne");
        assertThat(output).contains("   \u2514\u2500 step: PASSED afterTwo");
    }

    @Test
    @DisplayName("synthetic root labels parallel body action type")
    void syntheticRootLabelsParallelBodyActionType() {
        var root = Scope.builder(ROOT_NAME)
                .before(Step.of("before", context -> {}))
                .body(Parallel.builder("actions")
                        .parallelism(1)
                        .child(Step.of("FullLifecycleTest", context -> {}))
                        .child(Step.of("FakeTest", context -> {}))
                        .build())
                .after(Step.of("after", context -> {}))
                .build();
        var output = runAndCapturePlainOutput(root);

        assertThat(output).contains("PASSED Run");
        assertThat(output).contains("\u251C\u2500 before[step]: PASSED before");
        assertThat(output).contains("\u251C\u2500 body[parallel]: PASSED actions");
        assertThat(output).contains("\u2502  \u251C\u2500 step: PASSED FullLifecycleTest");
        assertThat(output).contains("\u2502  \u2514\u2500 step: PASSED FakeTest");
        assertThat(output).contains("\u2514\u2500 after[step]: PASSED after");
    }

    @Test
    @DisplayName("non-hidden root rendered")
    void nonHiddenRootRendered() {
        var noop = Step.of("my-root", context -> {});
        var output = runAndCaptureOutput(noop);

        assertThat(output).contains("my-root");
    }

    @Test
    @DisplayName("single action renders with PASSED status")
    void singleActionWithNoChildren() {
        var noop = Step.of("leaf", context -> {});
        var output = runAndCaptureOutput(noop);

        assertThat(output).contains("leaf");
        assertThat(output).contains("PASSED");
    }

    @Test
    @DisplayName("PASSED status rendered with green")
    void successfulStatusRenderedWithGreen() {
        var noop = Step.of("passing", context -> {});
        var output = runAndCaptureOutput(noop);

        assertThat(output).contains(AnsiColor.BOLD_GREEN_TEXT.format("PASSED"));
    }

    @Test
    @DisplayName("FAILED status rendered with red")
    void failStatusRenderedWithRed() {
        var failing = Step.of("failing", context -> {
            throw new RuntimeException("error");
        });
        var output = runAndCaptureOutput(failing);

        assertThat(output).contains(AnsiColor.BOLD_RED_TEXT.format("FAILED"));
    }

    @Test
    @DisplayName("SKIPPED status rendered with bold yellow")
    void skipStatusRenderedWithBoldYellow() {
        var skipping = Step.of("skipper", context -> {
            throw new SkipException("reason");
        });
        var output = runAndCaptureOutput(skipping);

        assertThat(output).contains(AnsiColor.BOLD_YELLOW_TEXT.format("SKIPPED"));
    }

    @Test
    @DisplayName("plain output disables ANSI formatting")
    void plainOutputDisablesAnsiFormatting() {
        var output = runAndCapturePlainOutput(Step.of("plain", context -> {}));

        assertThat(output).contains(Constants.PARAMIXEL_PLAIN);
        assertThat(output).contains("PASSED");
        assertThat(output).doesNotContain("\u001B[");
    }

    @Test
    @DisplayName("failure info shows exception class and message on same line")
    void failureInfoWithThrowable() {
        var failing = Step.of("fail-action", context -> {
            throw new IllegalStateException("bad state");
        });
        var output = runAndCaptureOutput(failing);

        assertThat(output).contains("java.lang.IllegalStateException");
        assertThat(output).contains("bad state");
    }

    @Test
    @DisplayName("failure info with multi-line message renders on single line")
    void failureInfoWithMultiLineMessageRendersOnSingleLine() {
        var failing = Step.of("multiline-fail", context -> {
            throw new RuntimeException("\nline2\nline3");
        });
        var output = runAndCaptureOutput(failing);

        assertThat(output).contains("java.lang.RuntimeException");
        assertThat(output).contains("line2 - line3");
    }

    @Test
    @DisplayName("renders core action name without kind")
    void rendersCoreActionNameWithoutKind() {
        var noop = Step.of("noop-action", context -> {});
        var output = runAndCaptureOutput(noop);

        assertThat(output).contains("noop-action");
    }

    @Test
    @DisplayName("timing shows milliseconds")
    void timingShowsMilliseconds() {
        var noop = Step.of("timed", context -> {});
        var output = runAndCaptureOutput(noop);

        assertThat(output).contains("ms");
    }

    @Test
    @DisplayName("full action names are preserved without truncation")
    void fullActionNamesPreserved() {
        var thirtyFiveCharName = "a".repeat(35);
        var noop = Step.of(thirtyFiveCharName, context -> {});
        var output = runAndCaptureOutput(noop);

        assertThat(output).contains(thirtyFiveCharName);
    }

    @Test
    @DisplayName("two children rendered with tree connectors")
    void twoChildrenRenderedWithTreeConnectors() {
        var child1 = Step.of("child-1", context -> {});
        var child2 = Step.of("child-2", context -> {});
        var seq = Sequence.builder("parent").child(child1).child(child2).build();
        var output = runAndCaptureOutput(seq);

        assertThat(output).contains("child-1");
        assertThat(output).contains("child-2");
        assertThat(output).contains("parent");
    }

    @Test
    @DisplayName("lifecycle with before and after renders with standard connectors")
    void lifecycleWithBeforeAndAfterRendersStandardConnectors() {
        var before = Step.of("before", context -> {});
        var body = Step.of("body", context -> {});
        var after = Step.of("after", context -> {});
        var scope = Scope.builder("myLifecycle")
                .before(before)
                .body(body)
                .after(after)
                .build();
        var output = runAndCapturePlainOutput(scope);

        assertThat(output).contains("\u251C\u2500 before[step]: PASSED before");
        assertThat(output).contains("\u251C\u2500 body[step]: PASSED body");
        assertThat(output).contains("\u2514\u2500 after[step]: PASSED after");
    }

    @Test
    @DisplayName("lifecycle with before, body, and after renders as siblings")
    void lifecycleWithBeforeBodyAfterRendersAsSiblings() {
        var before = Step.of("setUp()", context -> {});
        var body = Step.of("testGet()", context -> {});
        var after = Step.of("tearDown()", context -> {});
        var scope = Scope.builder("myLifecycle")
                .before(before)
                .body(body)
                .after(after)
                .build();
        var output = runAndCapturePlainOutput(scope);

        assertThat(output).contains("\u251C\u2500 before[step]: PASSED setUp()");
        assertThat(output).contains("\u251C\u2500 body[step]: PASSED testGet()");
        assertThat(output).contains("\u2514\u2500 after[step]: PASSED tearDown()");
    }

    @Test
    @DisplayName("lifecycle with before, multiple body, and after renders as siblings")
    void lifecycleWithBeforeMultipleBodyAfterRendersAsSiblings() {
        var before = Step.of("setUp()", context -> {});
        var body1 = Step.of("testA()", context -> {});
        var body2 = Step.of("testB()", context -> {});
        var after = Step.of("tearDown()", context -> {});
        var scope = Scope.builder("myLifecycle")
                .before(before)
                .body(Sequence.builder("body").child(body1).child(body2).build())
                .after(after)
                .build();
        var output = runAndCapturePlainOutput(scope);

        assertThat(output).contains("\u251C\u2500 before[step]: PASSED setUp()");
        assertThat(output).contains("\u251C\u2500 body[sequence]: PASSED body");
        assertThat(output).contains("\u2502  \u251C\u2500 step: PASSED testA()");
        assertThat(output).contains("\u2502  \u2514\u2500 step: PASSED testB()");
        assertThat(output).contains("\u2514\u2500 after[step]: PASSED tearDown()");
    }

    @Test
    @DisplayName("lifecycle with before and no after renders with standard connectors")
    void lifecycleWithBeforeNoAfterRendersStandardConnectors() {
        var before = Step.of("before", context -> {});
        var body = Step.of("body", context -> {});
        var scope = Scope.builder("myLifecycle").before(before).body(body).build();
        var output = runAndCapturePlainOutput(scope);

        assertThat(output).contains("\u251C\u2500 before[step]: PASSED before");
        assertThat(output).contains("\u2514\u2500 body[step]: PASSED body");
    }

    @Test
    @DisplayName("lifecycle with before, body, and no after renders as siblings")
    void lifecycleWithBeforeBodyNoAfterRendersAsSiblings() {
        var before = Step.of("setUp()", context -> {});
        var body = Step.of("testGet()", context -> {});
        var scope = Scope.builder("myLifecycle").before(before).body(body).build();
        var output = runAndCapturePlainOutput(scope);

        assertThat(output).contains("\u251C\u2500 before[step]: PASSED setUp()");
        assertThat(output).contains("\u2514\u2500 body[step]: PASSED testGet()");
    }

    @Test
    @DisplayName("lifecycle with after and no before renders with standard connectors")
    void lifecycleWithAfterNoBeforeRendersStandardConnectors() {
        var body = Step.of("body", context -> {});
        var after = Step.of("after", context -> {});
        var scope = Scope.builder("myLifecycle").body(body).after(after).build();
        var output = runAndCapturePlainOutput(scope);

        assertThat(output).contains("\u251C\u2500 body[step]: PASSED body");
        assertThat(output).contains("\u2514\u2500 after[step]: PASSED after");
    }

    @Test
    @DisplayName("lifecycle with body and after but no before renders body flat with after last")
    void lifecycleWithBodyAfterNoBeforeRendersBodyFlatWithAfterLast() {
        var body = Step.of("testGet()", context -> {});
        var after = Step.of("tearDown()", context -> {});
        var scope = Scope.builder("myLifecycle").body(body).after(after).build();
        var output = runAndCapturePlainOutput(scope);

        assertThat(output).contains("\u251C\u2500 body[step]: PASSED testGet()");
        assertThat(output).contains("\u2514\u2500 after[step]: PASSED tearDown()");
    }

    @Test
    @DisplayName("lifecycle with setUp and tearDown names renders with standard connectors")
    void lifecycleWithSetUpTearDownNamesRendersStandardConnectors() {
        var before = Step.of("setUp()", context -> {});
        var body = Step.of("body", context -> {});
        var after = Step.of("tearDown()", context -> {});
        var scope = Scope.builder("myLifecycle")
                .before(before)
                .body(body)
                .after(after)
                .build();
        var output = runAndCapturePlainOutput(scope);

        assertThat(output).contains("\u251C\u2500 before[step]: PASSED setUp()");
        assertThat(output).contains("\u251C\u2500 body[step]: PASSED body");
        assertThat(output).contains("\u2514\u2500 after[step]: PASSED tearDown()");
    }

    @Test
    @DisplayName("instance with lifecycle body child renders lifecycle slots as siblings")
    void instanceWithLifecycleBodyChildRendersLifecycleSlotsAsSiblings() {
        var lifecycleBefore = Step.of("setUp()", context -> {});
        var lifecycleBody = Step.of("testGet()", context -> {});
        var lifecycleAfter = Step.of("tearDown()", context -> {});
        var scope = Scope.builder("lifecycle")
                .before(lifecycleBefore)
                .body(lifecycleBody)
                .after(lifecycleAfter)
                .build();
        var instance =
                Instance.builder("nginx:1.29.5", () -> "instance").body(scope).build();
        var parallel = Parallel.builder("root").parallelism(1).child(instance).build();
        var output = runAndCapturePlainOutput(parallel);

        assertThat(output).contains("\u251C\u2500 before[step]: PASSED [instantiate]");
        assertThat(output).contains("\u251C\u2500 body[scope]: PASSED lifecycle");
        assertThat(output).contains("\u2502  \u251C\u2500 before[step]: PASSED setUp()");
        assertThat(output).contains("\u2502  \u251C\u2500 body[step]: PASSED testGet()");
        assertThat(output).contains("\u2502  \u2514\u2500 after[step]: PASSED tearDown()");
        assertThat(output).contains("\u2514\u2500 after[step]: PASSED [destroy]");
    }

    @Test
    @DisplayName("static with before and after renders body as sibling")
    void staticWithBeforeAndAfterRendersBodyAsSibling() {
        var staticAction = Static.builder("myStatic")
                .before(Step.of("staticSetUp", context -> {}))
                .body(Sequence.builder("body")
                        .child(Step.of("testOne", context -> {}))
                        .child(Step.of("testTwo", context -> {}))
                        .build())
                .after(Step.of("staticTearDown", context -> {}))
                .build();
        var output = runAndCapturePlainOutput(staticAction);

        assertThat(output).contains("\u251C\u2500 before[step]: PASSED staticSetUp");
        assertThat(output).contains("\u251C\u2500 body[sequence]: PASSED body");
        assertThat(output).contains("\u2502  \u251C\u2500 step: PASSED testOne");
        assertThat(output).contains("\u2502  \u2514\u2500 step: PASSED testTwo");
        assertThat(output).contains("\u2514\u2500 after[step]: PASSED staticTearDown");
    }

    @Test
    @DisplayName("instance renders instantiate, body, and destroy as siblings")
    void instanceRendersInstantiateBodyAndDestroyAsSiblings() {
        var instance = Instance.builder("myInstance", () -> "instance")
                .body(Sequence.builder("body")
                        .child(Step.of("setUp", s -> {}))
                        .child(Step.of("test produce", s -> {}))
                        .child(Step.of("test consume", s -> {}))
                        .child(Step.of("tearDown", s -> {}))
                        .build())
                .build();
        var parallel = Parallel.builder("root").parallelism(1).child(instance).build();
        var output = runAndCapturePlainOutput(parallel);

        assertThat(output).contains("myInstance");
        assertThat(output).contains("[instantiate]");
        assertThat(output).contains("setUp");
        assertThat(output).contains("test produce");
        assertThat(output).contains("test consume");
        assertThat(output).contains("tearDown");
        assertThat(output).contains("[destroy]");
        assertThat(output).contains("\u251C\u2500 before[step]: PASSED [instantiate]");
        assertThat(output).contains("\u251C\u2500 body[sequence]: PASSED body");
        assertThat(output).contains("\u2502  \u251C\u2500 step: PASSED setUp");
        assertThat(output).contains("\u2502  \u251C\u2500 step: PASSED test produce");
        assertThat(output).contains("\u2502  \u251C\u2500 step: PASSED test consume");
        assertThat(output).contains("\u2502  \u2514\u2500 step: PASSED tearDown");
        assertThat(output).contains("\u2514\u2500 after[step]: PASSED [destroy]");
    }

    @Test
    @DisplayName("renderer handles deep user-generated descriptor chains without recursion overflow")
    void rendererHandlesDeepUserGeneratedDescriptorChains() {
        final int depth = 2500;
        var root = createDeepLinearDescriptorTree(depth);
        var renderer = new TreeSummaryRenderer(false);

        var output = renderer.render(root);

        assertThat(output).contains("depth-0");
        assertThat(output).contains("depth-" + (depth - 1));
        assertThat(output.split(System.lineSeparator())).hasSize(depth);
    }

    @Test
    @DisplayName("onRunStarted prints starting banner")
    void onRunStartedPrintsStartingBanner() {
        var noop = Step.of("banner-test", context -> {});
        var output = runAndCapturePlainOutput(noop);

        assertThat(output).contains("Paramixel v" + Version.version() + " starting...");
    }

    @Test
    @DisplayName("onRunCompleted prints footer with status, finished-at, and total time")
    void onRunCompletedPrintsFooter() {
        var noop = Step.of("footer-test", context -> {});
        var output = runAndCapturePlainOutput(noop);

        assertThat(output).contains("Status      : PASSED");
        assertThat(output).contains("Finished at :");
        assertThat(output).contains("Total time  :");
    }

    @Test
    @DisplayName("footer uses ANSI-colored status when ANSI is enabled")
    void footerUsesAnsiStatusWhenEnabled() {
        var noop = Step.of("ansi-footer", context -> {});
        var output = runAndCaptureOutput(noop);

        assertThat(output).contains(AnsiColor.BOLD_GREEN_TEXT.format("PASSED"));
    }

    @Test
    @DisplayName("footer uses plain status when ANSI is disabled")
    void footerUsesPlainStatusWhenDisabled() {
        var noop = Step.of("plain-footer", context -> {});
        var output = runAndCapturePlainOutput(noop);

        assertThat(output).contains("Status      : PASSED");
        assertThat(output).doesNotContain("\u001B[");
    }

    @Test
    @DisplayName("onRunCompleted prints dashed separators around footer")
    void onRunCompletedPrintsDashedSeparators() {
        var noop = Step.of("separator-test", context -> {});
        var output = runAndCapturePlainOutput(noop);

        assertThat(output).contains("----------");
    }

    @Test
    @DisplayName("each output line is prefixed with [PARAMIXEL]")
    void eachLinePrefixedWithParamixel() {
        var noop = Step.of("prefix-test", context -> {});
        var output = runAndCapturePlainOutput(noop);

        for (var line : output.split(System.lineSeparator())) {
            if (!line.isEmpty() && !line.startsWith("ACTIVE THREAD COUNT ")) {
                assertThat(line).startsWith("[PARAMIXEL] ");
            }
        }
    }

    @Test
    @DisplayName("passed status lines have no trailing whitespace")
    void passedStatusLinesHaveNoTrailingWhitespace() {
        var noop = Step.of("trailing-space-test", context -> {});
        var output = runAndCapturePlainOutput(noop);

        for (var line : output.split(System.lineSeparator())) {
            if (line.contains("trailing-space-test")) {
                assertThat(line).doesNotEndWith(" ");
            }
        }
    }

    @Test
    void renderRejectsNullDescriptor() {
        var renderer = new TreeSummaryRenderer();
        var output = renderer.render(null);

        assertThat(output).contains("No Paramixel tests found");
    }

    private static ConcreteDescriptor createDeepLinearDescriptorTree(final int depth) {
        var root = new ConcreteDescriptor(Step.of("depth-0", context -> {}));
        var current = root;
        for (var index = 1; index < depth; index++) {
            var child = new ConcreteDescriptor(Step.of("depth-" + index, context -> {}));
            current.addChild(child);
            current = child;
        }
        return root;
    }
}

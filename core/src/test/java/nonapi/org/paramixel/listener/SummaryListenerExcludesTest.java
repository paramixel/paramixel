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
import nonapi.org.paramixel.ConcreteResult;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.paramixel.api.Configuration;
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;

@DisplayName("SummaryListener excludes")
class SummaryListenerExcludesTest {

    private static SummaryListener createListener(String excludeValue) {
        var listener = new SummaryListener();
        var config = new ConcreteConfiguration(
                excludeValue != null ? Map.of(Configuration.LISTENER_EXCLUDE, excludeValue) : Map.of());
        listener.initialize(config);
        return listener;
    }

    private static ConcreteDescriptor createPassedDescriptor(String name) {
        var descriptor = new ConcreteDescriptor(Step.of(name, v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.PASSED);
        return descriptor;
    }

    private static ConcreteResult createResult(ConcreteDescriptor root) {
        return new ConcreteResult(root, new ConcreteConfiguration(Map.of()));
    }

    private String captureStdout(Runnable runnable) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runnable.run();
        } finally {
            System.setOut(originalOut);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("constructor returns non-null instance")
    void constructorReturnsNonNullInstance() {
        assertThat(new SummaryListener()).isNotNull();
    }

    @Test
    @DisplayName("prints header, tree, and footer with no excludes")
    void normalOutputWhenNoExcludes() {
        var listener = createListener(null);
        var root = createPassedDescriptor("root-action");
        var result = createResult(root);

        String output = captureStdout(() -> {
            listener.onRunStarted();
            listener.onRunCompleted(result);
        });

        assertThat(output).contains("starting...");
        assertThat(output).contains("root-action");
        assertThat(output).contains("Status      ");
        assertThat(output).contains("Total time  ");
    }

    @Test
    @DisplayName("skips header when SUMMARY_HEADER excluded")
    void skipsHeaderWhenSummaryHeaderExcluded() {
        var listener = createListener("summary.header");
        var root = createPassedDescriptor("header-test");
        var result = createResult(root);

        String output = captureStdout(() -> {
            listener.onRunStarted();
            listener.onRunCompleted(result);
        });

        assertThat(output).doesNotContain("starting...");
        assertThat(output).contains("header-test");
        assertThat(output).contains("Status      ");
    }

    @Test
    @DisplayName("skips tree when SUMMARY_TREE excluded")
    void skipsTreeWhenSummaryTreeExcluded() {
        var listener = createListener("summary.tree");
        var root = createPassedDescriptor("tree-test");
        var result = createResult(root);

        String output = captureStdout(() -> {
            listener.onRunStarted();
            listener.onRunCompleted(result);
        });

        assertThat(output).contains("starting...");
        assertThat(output).doesNotContain("tree-test");
        assertThat(output).contains("Status      ");
        assertThat(output).contains("Total time  ");
    }

    @Test
    @DisplayName("skips footer when SUMMARY_FOOTER excluded")
    void skipsFooterWhenSummaryFooterExcluded() {
        var listener = createListener("summary.footer");
        var root = createPassedDescriptor("footer-test");
        var result = createResult(root);

        String output = captureStdout(() -> {
            listener.onRunStarted();
            listener.onRunCompleted(result);
        });

        assertThat(output).contains("starting...");
        assertThat(output).contains("footer-test");
        assertThat(output).doesNotContain("Status      ");
        assertThat(output).doesNotContain("Total time  ");
    }

    @Test
    @DisplayName("quiet shorthand excludes status and tree, preserves header and footer")
    void quietShorthandPreservesHeaderAndFooter() {
        var listener = createListener("quiet");
        var root = createPassedDescriptor("quiet-test");
        var result = createResult(root);

        String output = captureStdout(() -> {
            listener.onRunStarted();
            listener.onRunCompleted(result);
        });

        assertThat(output).contains("starting...");
        assertThat(output).doesNotContain("quiet-test");
        assertThat(output).contains("Status      ");
        assertThat(output).contains("Total time  ");
    }

    @Test
    @DisplayName("excludes all from onRunCompleted when both SUMMARY_TREE and SUMMARY_FOOTER excluded")
    void excludesAllOnRunCompletedWhenTreeAndFooterExcluded() {
        var listener = createListener("summary.tree,summary.footer");
        var root = createPassedDescriptor("empty-test");
        var result = createResult(root);

        String output = captureStdout(() -> {
            listener.onRunStarted();
            listener.onRunCompleted(result);
        });

        assertThat(output).contains("starting...");
        assertThat(output).doesNotContain("empty-test");
        assertThat(output).doesNotContain("Status      ");
        assertThat(output).doesNotContain("Total time  ");
    }

    @Test
    @DisplayName("all shorthand excludes everything")
    void allShorthandExcludesEverything() {
        var listener = createListener("all");
        var root = createPassedDescriptor("all-test");
        var result = createResult(root);

        String output = captureStdout(() -> {
            listener.onRunStarted();
            listener.onRunCompleted(result);
        });

        assertThat(output).doesNotContain("starting...");
        assertThat(output).doesNotContain("all-test");
        assertThat(output).doesNotContain("Status      ");
        assertThat(output).doesNotContain("Total time  ");
    }

    @Test
    @DisplayName("prints no-tests message even when excludes are set")
    void printsNoTestsMessageWhenExcludesSet() {
        var listener = createListener("all");
        var result = new ConcreteResult(new ConcreteConfiguration(Map.of()));

        String output = captureStdout(() -> listener.onRunCompleted(result));

        assertThat(output).contains("No Paramixel tests found");
    }

    @ParameterizedTest
    @MethodSource("allSummaryCombinations")
    @DisplayName("exhaustively guards with all 8 SUMMARY_HEADER/SUMMARY_TREE/SUMMARY_FOOTER combinations")
    void allSummaryCombinations(EnumSet<ExcludeTarget> excludeSet) {
        var configValue = new StringBuilder();
        for (var target : excludeSet) {
            if (configValue.length() > 0) {
                configValue.append(",");
            }
            configValue.append(target.name().toLowerCase().replace('_', '.'));
        }

        var listener = createListener(configValue.toString());
        var root = createPassedDescriptor("combo-action");
        var result = createResult(root);

        String output = captureStdout(() -> {
            listener.onRunStarted();
            listener.onRunCompleted(result);
        });

        boolean expectHeader = !excludeSet.contains(ExcludeTarget.SUMMARY_HEADER);
        boolean expectTree = !excludeSet.contains(ExcludeTarget.SUMMARY_TREE);
        boolean expectFooter = !excludeSet.contains(ExcludeTarget.SUMMARY_FOOTER);

        if (expectHeader) {
            assertThat(output).contains("starting...");
        } else {
            assertThat(output).doesNotContain("starting...");
        }

        if (expectTree) {
            assertThat(output).contains("combo-action");
        } else {
            assertThat(output).doesNotContain("combo-action");
        }

        if (expectFooter) {
            assertThat(output).contains("Total time  :");
        } else {
            assertThat(output).doesNotContain("Total time  :");
        }
    }

    private static Stream<Arguments> allSummaryCombinations() {
        return Stream.of(
                Arguments.of(EnumSet.noneOf(ExcludeTarget.class)),
                Arguments.of(EnumSet.of(ExcludeTarget.SUMMARY_HEADER)),
                Arguments.of(EnumSet.of(ExcludeTarget.SUMMARY_TREE)),
                Arguments.of(EnumSet.of(ExcludeTarget.SUMMARY_FOOTER)),
                Arguments.of(EnumSet.of(ExcludeTarget.SUMMARY_HEADER, ExcludeTarget.SUMMARY_TREE)),
                Arguments.of(EnumSet.of(ExcludeTarget.SUMMARY_HEADER, ExcludeTarget.SUMMARY_FOOTER)),
                Arguments.of(EnumSet.of(ExcludeTarget.SUMMARY_TREE, ExcludeTarget.SUMMARY_FOOTER)),
                Arguments.of(EnumSet.of(
                        ExcludeTarget.SUMMARY_HEADER, ExcludeTarget.SUMMARY_TREE, ExcludeTarget.SUMMARY_FOOTER)));
    }
}

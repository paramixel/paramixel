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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

@DisplayName("StatusListener path")
@SuppressWarnings("removal")
class StatusListenerPathTest {

    private static final String ROOT_NAME = Constants.ROOT_NAME;

    @Test
    @DisplayName("shows simple name for core action classes")
    void showsSimpleNameForCoreActionClasses() {
        var listener = new StatusListener();
        var noop = Step.of("simple", context -> {});
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
        assertThat(result).contains("simple");
    }

    @Test
    @DisplayName("shows FQCN for non-core package action classes")
    void showsFQCNForNonCorePackageActionClasses() {
        var listener = new StatusListener();
        var custom = Step.of("custom", context -> {});
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(custom);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("custom");
    }

    @Test
    @DisplayName("id path excludes root ancestors")
    void idPathExcludesRootAncestors() {
        var listener = new StatusListener();
        var child = Step.of("visible-child", context -> {});
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
        assertThat(result).contains("visible-child");
        var childLine = result.lines()
                .filter(line -> line.contains("visible-child"))
                .findFirst()
                .orElse("");
        assertThat(childLine).contains(child.displayName());
        assertThat(childLine).doesNotContain(ROOT_NAME);
    }

    @Test
    @DisplayName("action path excludes root ancestors")
    void actionPathExcludesRootAncestors() {
        var listener = new StatusListener();
        var child = Step.of("visible-child", context -> {});
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
        assertThat(result).contains("visible-child");
        assertThat(result).doesNotContain(ROOT_NAME);
    }

    @Test
    @DisplayName("nested action display path includes parent name")
    void nestedActionDisplayPathIncludesParentName() {
        var listener = new StatusListener();
        var child = Step.of("child", context -> {});
        var parent = Sequence.builder("parent").child(child).build();
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(parent);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("parent / child");
    }

    @Test
    @DisplayName("nested action path includes parent name")
    void nestedActionPathIncludesParentName() {
        var listener = new StatusListener();
        var child = Step.of("child", context -> {});
        var parent = Sequence.builder("parent").child(child).build();
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(parent);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("parent / child");
    }

    @Test
    @DisplayName("deep nesting produces full hierarchical display path")
    void deepNestingProducesFullHierarchicalPath() {
        var listener = new StatusListener();
        var leaf = Step.of("leaf", context -> {});
        var mid = Sequence.builder("mid").child(leaf).build();
        var top = Sequence.builder("top").child(mid).build();
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(top);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("top / mid / leaf");
    }

    @Test
    @DisplayName("timing shows milliseconds")
    void timingShowsMilliseconds() {
        var listener = new StatusListener();
        var noop = Step.of("timed-action", context -> {});
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
        assertThat(result).contains("ms");
    }

    @Test
    @DisplayName("display name with ANSI sequences is sanitized in status output")
    void displayNameWithAnsiSequencesIsSanitized() {
        var listener = new StatusListener();
        var action = Step.of("\u001B[31mred-name\u001B[0m", context -> {});
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
        assertThat(result).contains("red-name");
        assertThat(result).doesNotContain("\u001B");
    }

    @Test
    @DisplayName("display name with null bytes is sanitized in status output")
    void displayNameWithNullBytesIsSanitized() {
        var listener = new StatusListener();
        var action = Step.of("test\u0000name", context -> {});
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
        assertThat(result).contains("testname");
        assertThat(result).doesNotContain("\u0000");
    }

    @Test
    @DisplayName("nested display names with control characters are sanitized in path")
    void nestedDisplayNamesWithControlCharsAreSanitizedInPath() {
        var listener = new StatusListener();
        var child = Step.of("\u0007leaf\u001B[0m", context -> {});
        var parent = Sequence.builder("\u001B[1mtop\u0000").child(child).build();
        var runner = Runner.builder().listener(listener).build();

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runner.run(parent);
        } finally {
            System.setOut(originalOut);
        }

        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("top / leaf");
        assertThat(result).doesNotContain("\u001B");
        assertThat(result).doesNotContain("\u0000");
        assertThat(result).doesNotContain("\u0007");
    }
}

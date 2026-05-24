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

package org.paramixel.api.internal.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.internal.listener.support.Constants;

@DisplayName("StatusListener path")
class StatusListenerPathTest {

    private static final String ROOT_NAME = Constants.ROOT_NAME;

    @Test
    @DisplayName("shows simple name for core action classes")
    void showsSimpleNameForCoreActionClasses() {
        StatusListener listener = new StatusListener();
        Step<?> noop = Step.of("simple", obj -> {});
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
        assertThat(result).contains("(Step)");
    }

    @Test
    @DisplayName("shows FQCN for non-core package action classes")
    void showsFQCNForNonCorePackageActionClasses() {
        StatusListener listener = new StatusListener();
        Action<?> custom = Step.of("custom", obj -> {});
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
        assertThat(result).contains("Step");
    }

    @Test
    @DisplayName("id path excludes root ancestors")
    void idPathExcludesRootAncestors() {
        StatusListener listener = new StatusListener();
        Action<?> child = Step.of("visible-child", obj -> {});
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
        assertThat(result).contains("visible-child");
        String childLine = result.lines()
                .filter(line -> line.contains("visible-child"))
                .findFirst()
                .orElse("");
        assertThat(childLine).contains(child.name());
        assertThat(childLine).doesNotContain(ROOT_NAME);
    }

    @Test
    @DisplayName("action path excludes root ancestors")
    void actionPathExcludesRootAncestors() {
        StatusListener listener = new StatusListener();
        Action<?> child = Step.of("visible-child", obj -> {});
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
        assertThat(result).contains("visible-child");
        assertThat(result).doesNotContain(ROOT_NAME);
    }

    @Test
    @DisplayName("nested action display path includes parent name")
    void nestedActionDisplayPathIncludesParentName() {
        StatusListener listener = new StatusListener();
        Action<?> child = Step.of("child", obj -> {});
        Sequential<?> parent = Sequential.of("parent").child(child).resolve();
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
        assertThat(result).contains("parent / child (Step)");
    }

    @Test
    @DisplayName("nested action path includes parent name")
    void nestedActionPathIncludesParentName() {
        StatusListener listener = new StatusListener();
        Action<?> child = Step.of("child", obj -> {});
        Sequential<?> parent = Sequential.of("parent").child(child).resolve();
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
        assertThat(result).contains("parent / child (Step)");
    }

    @Test
    @DisplayName("deep nesting produces full hierarchical display path")
    void deepNestingProducesFullHierarchicalPath() {
        StatusListener listener = new StatusListener();
        Action<?> leaf = Step.of("leaf", obj -> {});
        Action<?> mid = Sequential.of("mid").child(leaf).resolve();
        Action<?> top = Sequential.of("top").child(mid).resolve();
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
        assertThat(result).contains("top / mid / leaf (Step)");
    }

    @Test
    @DisplayName("timing shows milliseconds")
    void timingShowsMilliseconds() {
        StatusListener listener = new StatusListener();
        Step<?> noop = Step.of("timed-action", obj -> {});
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

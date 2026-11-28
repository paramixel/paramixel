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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.CompilationException;

/**
 * The authoring grammar: one before slot, one after slot, three argument kinds, held builders, and the binding and phase
 * scope rules.
 */
class TaskAuthoringTest {

    private static final String NAME = "name";
    private static final String VALUE = "value";

    @BeforeEach
    void clearEvents() {
        Events.RECORDED.clear();
    }

    private static Result run(final Task definition) {
        return Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("p"), 2)
                .build()
                .run(definition);
    }

    private static Result run(final TaskBuilder<?> definition) {
        return Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("p"), 2)
                .build()
                .run(definition);
    }

    @Test
    @DisplayName("AUTH-01: a second before throws at the call site with the composition guidance")
    void secondBeforeThrowsImmediately() {
        final var builder = Sequential.builder("root").before("before");

        assertThatThrownBy(() -> builder.before("after"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Task 'root' already has a before slot")
                .hasMessageContaining("Sequential.builder(...)")
                .hasMessageContaining("Parallel.builder(...)");
    }

    @Test
    @DisplayName("AUTH-02: a second after throws at the call site with the composition guidance")
    void secondAfterThrowsImmediately() {
        final var builder = Sequential.builder("root").after("after");

        assertThatThrownBy(() -> builder.after("other"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Task 'root' already has an after slot")
                .hasMessageContaining("Sequential.builder(...)")
                .hasMessageContaining("Parallel.builder(...)");
    }

    @Test
    @DisplayName("AUTH-03: before, step, and after accept an id, a Task, and a builder with the same execution")
    void allThreeArgumentKindsExecuteTheSameWay() {
        final var byId = Sequential.builder(Hooks.class, "root")
                .before("before")
                .step("check")
                .after("after")
                .build();
        assertThat(run(byId).status().isPassed()).isTrue();
        final var fromIds = List.copyOf(Events.RECORDED);

        Events.RECORDED.clear();
        final var byTask = Sequential.builder("root")
                .before(Sequential.builder("setup").step("hook", () -> Events.RECORDED.add("before")))
                .step(Sequential.builder("check").step("hook", () -> Events.RECORDED.add("check")))
                .after(Sequential.builder("cleanup").step("hook", () -> Events.RECORDED.add("after")))
                .build();
        assertThat(run(byTask).status().isPassed()).isTrue();
        final var fromTasks = List.copyOf(Events.RECORDED);

        Events.RECORDED.clear();
        final var byBuilder = Sequential.builder("root")
                .before(Sequential.builder("setup").step("hook", () -> Events.RECORDED.add("before")))
                .step(Sequential.builder("check").step("hook", () -> Events.RECORDED.add("check")))
                .after(Sequential.builder("cleanup").step("hook", () -> Events.RECORDED.add("after")))
                .build();
        assertThat(run(byBuilder).status().isPassed()).isTrue();

        assertThat(fromIds).containsExactly("before", "check", "after");
        assertThat(fromTasks).isEqualTo(fromIds);
        assertThat(Events.RECORDED).isEqualTo(fromIds);
    }

    @Test
    @DisplayName("AUTH-04: a builder changed after being passed to step is built with the change")
    void stepBuilderIsBuiltWithLateChanges() {
        final var child = Sequential.builder("child").step("one", () -> Events.RECORDED.add("one"));
        final var root = Sequential.builder("root").step(child);
        child.step("two", () -> Events.RECORDED.add("two"));

        assertThat(run(root.build()).status().isPassed()).isTrue();
        assertThat(Events.RECORDED).containsExactly("one", "two");
    }

    @Test
    @DisplayName("AUTH-05: passing one builder twice in a tree fails at build time")
    void oneBuilderAttachedTwiceFailsAtBuildTime() {
        final var shared = Sequential.builder("shared").step("work", () -> {});
        final var root = Sequential.builder("root").step(shared).step(shared);

        assertThatThrownBy(root::build)
                .isInstanceOf(CompilationException.class)
                .satisfies(thrown -> assertThat(((CompilationException) thrown).diagnostics())
                        .anySatisfy(diagnostic -> assertThat(diagnostic)
                                .contains("Builder 'shared' is attached more than once in one tree")
                                .contains("task 'root' a step")));
    }

    @Test
    void builderCycleReportsItsPathAndOtherAuthoringErrors() {
        final var root = Sequential.builder("root");
        root.before(Sequential.builder("setup").after(root)).step("missing");

        assertThatThrownBy(root::build)
                .isInstanceOfSatisfying(
                        CompilationException.class,
                        exception -> assertThat(exception.diagnostics())
                                .anySatisfy(diagnostic -> assertThat(diagnostic)
                                        .contains("Builder cycle: root -> setup -> root", "the after slot"))
                                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("missing", "no anchor")));
    }

    @Test
    @DisplayName("AUTH-05: the same built task may still be placed twice")
    void oneTaskPlacedTwiceCompiles() {
        final var shared = Sequential.builder("shared")
                .step("work", () -> Events.RECORDED.add("work"))
                .build();
        final var result =
                run(Sequential.builder("root").step(shared).step(shared).build());

        assertThat(result.status().isPassed()).isTrue();
        assertThat(Events.RECORDED).containsExactly("work", "work");
        assertThat(result.root().orElseThrow().children())
                .extracting(TaskResult::occurrenceId)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName(
            "AUTH-06: bound values reach before, steps, after, and descendants, and a child rebinding wins only there")
    void boundValuesAndChildRebinding() {
        final var result = run(Sequential.builder(Hooks.class, "root")
                .bind(NAME, "root-value")
                .before("assertName")
                .step(Sequential.builder("child")
                        .bind(NAME, "child-value")
                        .step("assertName")
                        .after("assertName"))
                .after("assertName")
                .build());

        assertThat(result.status().isPassed()).isTrue();
        assertThat(Events.RECORDED).containsExactly("root-value", "child-value", "child-value", "root-value");
    }

    @Test
    @DisplayName("AUTH-07: a duplicate bind name on one node throws")
    void duplicateBindNameThrows() {
        final var builder = Sequential.builder("root").bind(NAME, "one");

        assertThatThrownBy(() -> builder.bind(NAME, "two"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate bind name on task 'root'")
                .hasMessageContaining(NAME);
    }

    @Test
    @DisplayName("AUTH-08: a value put inside a sequential before-subtree reaches the owning steps")
    void sequentialBeforeSubtreeStateReachesSteps() {
        final var result = run(Sequential.builder(Hooks.class, "root")
                .before(Sequential.builder("setup").step("putValue"))
                .step("requireValue")
                .build());

        assertThat(result.status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("AUTH-09: a value put inside a parallel branch of a before-subtree never leaves that branch")
    void parallelBranchOfBeforeSubtreeIsIsolated() {
        final var result = run(Sequential.builder(Hooks.class, "root")
                .before(Parallel.builder("setup", Pool.named("p"))
                        .step(Sequential.builder("branch").step("putValue").step("requireValue")))
                .step("requireNoValue")
                .build());

        assertThat(result.status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("AUTH-10: a failing before-subtree skips the steps and still runs after")
    void failingBeforeSubtreeSkipsStepsAndRunsAfter() {
        final var result = run(Sequential.builder("root")
                .before(Sequential.builder("setup").step("boom", () -> {
                    throw new IllegalStateException("setup failed");
                }))
                .step("unreachable", () -> Events.RECORDED.add("step"))
                .after(Sequential.builder("cleanup").step("closed", () -> Events.RECORDED.add("after")))
                .build());

        assertThat(result.status().isFailed()).isTrue();
        assertThat(Events.RECORDED).containsExactly("after");
        final var root = result.root().orElseThrow();
        final var step = root.children().stream()
                .filter(child -> child.name().equals("unreachable"))
                .findFirst()
                .orElseThrow();
        assertThat(step.status().isSkipped()).isTrue();
    }

    @Test
    @DisplayName("AUTH-11: a dependent after-subtree stops at the first failing cleanup step")
    void dependentAfterSubtreeStopsAtFirstFailure() {
        final var result = run(Sequential.builder("root")
                .step("work", () -> {})
                .after(Sequential.builder("cleanup")
                        .step("first", () -> Events.RECORDED.add("first"))
                        .step("boom", () -> {
                            throw new IllegalStateException("cleanup failed");
                        })
                        .step("last", () -> Events.RECORDED.add("last")))
                .build());

        assertThat(result.status().isFailed()).isTrue();
        assertThat(Events.RECORDED).containsExactly("first");
    }

    @Test
    @DisplayName("AUTH-12: an independent after-subtree attempts every cleanup step")
    void independentAfterSubtreeRunsEveryStep() {
        final var result = run(Sequential.builder("root")
                .step("work", () -> {})
                .after(Sequential.builder("cleanup")
                        .independent()
                        .step("first", () -> Events.RECORDED.add("first"))
                        .step("boom", () -> {
                            throw new IllegalStateException("cleanup failed");
                        })
                        .step("last", () -> Events.RECORDED.add("last")))
                .build());

        assertThat(result.status().isFailed()).isTrue();
        assertThat(Events.RECORDED).containsExactly("first", "last");
    }

    @Test
    @DisplayName("AUTH-13: the before and after subtrees are reported under their owner, labelled by phase")
    void phaseSubtreesAppearInTheResultTree() {
        final var result = run(Sequential.builder("root")
                .before(Sequential.builder("setup").step("prepare", () -> {}))
                .step("work", () -> {})
                .after(Sequential.builder("cleanup").step("release", () -> {}))
                .build());

        final var root = result.root().orElseThrow();
        assertThat(root.children()).extracting(TaskResult::name).containsExactly("setup", "work", "cleanup");
        assertThat(root.children())
                .extracting(TaskResult::slot)
                .containsExactly(TaskResult.Slot.BEFORE, TaskResult.Slot.CHILD, TaskResult.Slot.AFTER);
        final var setup = root.children().get(0);
        assertThat(setup.children()).extracting(TaskResult::name).containsExactly("prepare");
        assertThat(setup.path()).isEqualTo("root > setup");
    }

    @Test
    @DisplayName("AUTH-14: Runner.run(builder) is equivalent to Runner.run(builder.build())")
    void runnerAcceptsABuilderDirectly() {
        final var builder = Sequential.builder("root").step("work", () -> {});

        final var fromBuilder = run(builder);
        final var fromTask = run(builder.build());

        assertThat(fromBuilder.status().kind()).isEqualTo(fromTask.status().kind());
        assertThat(fromBuilder.root().orElseThrow().name()).isEqualTo("root");
        assertThat(fromBuilder.occurrenceCount()).isEqualTo(fromTask.occurrenceCount());
        assertThat(fromBuilder.status().isPassed()).isTrue();
    }

    /**
     * Records hook invocations; each test starts from an empty list.
     */
    static final class Events {

        static final List<String> RECORDED = new ArrayList<>();

        private Events() {
            // Intentionally empty
        }
    }

    /**
     * Anchor class for the identifier-bound cases.
     */
    public static final class Hooks {

        private Hooks() {
            // Intentionally empty
        }

        @Paramixel.Id("before")
        public static void before(final Context context) {
            Events.RECORDED.add("before");
        }

        @Paramixel.Id("check")
        public static void check(final Context context) {
            Events.RECORDED.add("check");
        }

        @Paramixel.Id("after")
        public static void after(final Context context) {
            Events.RECORDED.add("after");
        }

        @Paramixel.Id("assertName")
        public static void assertName(final Context context) {
            Events.RECORDED.add(context.require(NAME, String.class));
        }

        @Paramixel.Id("putValue")
        public static void putValue(final Context context) {
            context.put(VALUE, "set");
        }

        @Paramixel.Id("requireValue")
        public static void requireValue(final Context context) {
            Events.RECORDED.add(context.require(VALUE, String.class));
        }

        @Paramixel.Id("requireNoValue")
        public static void requireNoValue(final Context context) {
            if (context.find(VALUE, String.class).isPresent()) {
                throw new IllegalStateException("state crossed the parallel boundary of the before subtree");
            }
        }
    }
}

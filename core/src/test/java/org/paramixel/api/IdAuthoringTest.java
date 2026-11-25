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
import static org.paramixel.api.Tasks.sequential;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.exception.CompilationException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

/**
 * Name-bound authoring: resolution of {@link Paramixel.Id} hooks against an anchor class.
 */
class IdAuthoringTest {

    private static Result run(final Task definition) {
        return Runner.builder().listener(Listener.NOOP).build().run(definition);
    }

    private static java.util.List<Failure> allFailures(final TaskResult node) {
        final var failures = new java.util.ArrayList<Failure>(node.failures());
        for (final var child : node.children()) {
            failures.addAll(allFailures(child));
        }
        return failures;
    }

    private static List<String> problems(final Runnable action) {
        try {
            action.run();
        } catch (final CompilationException e) {
            return e.diagnostics();
        }
        throw new AssertionError("Expected a CompilationException");
    }

    @Test
    @DisplayName("ID-01: hooks resolve against the anchor class and run in lifecycle order")
    void hooksRunInOrder() {
        final var recorder = Records.EVENTS;
        recorder.clear();

        final var result = run(sequential(Hooks.class, "root")
                .before("first")
                .step("work")
                .after("last")
                .build());

        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(recorder).containsExactly("first", "work", "last");
    }

    @Test
    @DisplayName("ID-02: an attached builder inherits the root's anchor")
    void anchorIsInherited() {
        Records.EVENTS.clear();

        final var result = run(sequential(Hooks.class, "root")
                .child(sequential("group").before("first").step("work"))
                .build());

        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(Records.EVENTS).containsExactly("first", "work");
    }

    @Test
    @DisplayName("ID-03: a hook may declare checked exceptions")
    void checkedExceptionsAreAllowed() {
        final var result =
                run(sequential(Hooks.class, "root").step("throwsChecked").build());

        assertThat(result.status().isFailed()).isTrue();
        assertThat(allFailures(result.root().orElseThrow()))
                .anySatisfy(failure -> assertThat(failure.message()).contains("boom"));
    }

    @Test
    @DisplayName("ID-04: an unknown id is rejected before anything runs, with candidates")
    void unknownIdIsRejected() {
        Records.EVENTS.clear();

        assertThat(problems(() -> sequential(Hooks.class, "root").step("wor").build()))
                .anySatisfy(diagnostic ->
                        assertThat(diagnostic).contains("Unknown id").contains("candidates"));

        assertThat(Records.EVENTS).isEmpty();
    }

    @Test
    @DisplayName("ID-05: an id used without an anchor is rejected")
    void missingAnchorIsRejected() {
        assertThat(problems(() -> sequential("root").step("work").build()))
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("no anchor"));
    }

    @Test
    @DisplayName("ID-06: a duplicated id is rejected")
    void duplicateIdIsRejected() {
        assertThat(problems(
                        () -> sequential(Duplicates.class, "root").step("dup").build()))
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("declared 2 times"));
    }

    @Test
    @DisplayName("ID-07: a hook with the wrong shape is rejected per role")
    void wrongShapeIsRejected() {
        assertThat(problems(() ->
                        sequential(Malformed.class, "root").before("notStatic").build()))
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("must be public static"));

        assertThat(problems(() -> sequential(Malformed.class, "root")
                        .body("returnsSomething")
                        .build()))
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("expected void m(Context)"));

        assertThat(problems(() -> sequential(Malformed.class, "root")
                        .body("twoParameters")
                        .build()))
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("expected void m(Context)"));
    }

    @Test
    @DisplayName("ID-08: all problems are reported together")
    void problemsAreAggregated() {
        assertThat(problems(() -> sequential(Malformed.class, "root")
                        .step("missing")
                        .step("twoParameters")
                        .build()))
                .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("ID-09: FailException, SkipException and AbortException keep their classification")
    void hookExceptionClassification() {
        assertThat(run(sequential(Hooks.class, "root").step("fails").build())
                        .status()
                        .isFailed())
                .isTrue();

        final var skipped = run(sequential(Hooks.class, "root").step("skips").build());
        assertThat(skipped.status().isSkipped()).isTrue();
        assertThat(allFailures(skipped.root().orElseThrow()))
                .anySatisfy(failure -> assertThat(failure.message()).contains("not now"));

        final var aborted = run(sequential(Hooks.class, "root").step("aborts").build());
        assertThat(aborted.status().isTerminal()).isTrue();
    }

    @Test
    @DisplayName("ID-10: a repeat predicate is a two-parameter hook")
    void repeatPredicate() {
        Records.EVENTS.clear();

        final var result = run(Tasks.repeat(Hooks.class, "again")
                .maxIterations(3)
                .until("converged")
                .step("count")
                .build());

        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(Records.EVENTS).containsExactly("count", "count");
    }

    @Test
    @DisplayName("ID-12: a resolved hook is labelled by its id, not by its position")
    void hookLabelsUseIds() {
        final var result = run(sequential(Hooks.class, "root")
                .before("first")
                .step("work")
                .after("last")
                .build());

        final var root = result.root().orElseThrow();
        assertThat(root.phases())
                .anySatisfy(phase -> assertThat(phase.name()).isEqualTo("before[first]"))
                .anySatisfy(phase -> assertThat(phase.name()).isEqualTo("after[last]"));

        final var step = root.children().stream()
                .filter(child -> child.name().equals("work"))
                .findFirst()
                .orElseThrow();
        assertThat(step.phases()).anySatisfy(phase -> assertThat(phase.name()).isEqualTo("body[work]"));
    }

    @Test
    @DisplayName("ID-13: a subtree built before it is attached inherits the anchor when the definition is built")
    void helperBuiltSubtreeInheritsAnchor() {
        Records.EVENTS.clear();

        // Built bottom-up: no anchor anywhere except the root, and the ids still resolve.
        final var definition = sequential(Hooks.class, "root")
                .child(sequential("suite").child(sequential("inner").step("work")))
                .build();

        assertThat(definition.children().get(0).children().get(0).name()).isEqualTo("inner");
        assertThat(run(definition).status().isPassed()).isTrue();
        assertThat(Records.EVENTS).containsExactly("work");

        // The same shape with no anchor at all is still an authoring error.
        assertThat(problems(() -> sequential("root")
                        .child(sequential("suite").child(sequential("inner").step("work")))
                        .build()))
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("no anchor"));
    }

    @Test
    @DisplayName("ID-11: an unanchored definition that uses no id still builds and runs")
    void unanchoredWithoutIdsStillRuns() {
        final var result = run(sequential("plain").step("unnamed", () -> {}).build());
        assertThat(result.status()).isEqualTo(Status.PASSED);
    }

    /**
     * Records hook invocations; cleared by each test that asserts on it.
     */
    static final class Records {

        static final List<String> EVENTS = new ArrayList<>();

        private Records() {
            // Intentionally empty
        }
    }

    /**
     * Anchor class for the passing cases.
     */
    public static final class Hooks {

        private Hooks() {
            // Intentionally empty
        }

        @Paramixel.Id("first")
        public static void first(final Context context) {
            Records.EVENTS.add("first");
        }

        @Paramixel.Id("work")
        public static void work(final Context context) {
            Records.EVENTS.add("work");
        }

        @Paramixel.Id("last")
        public static void last(final Context context) {
            Records.EVENTS.add("last");
        }

        @Paramixel.Id("throwsChecked")
        public static void throwsChecked(final Context context) throws IOException {
            throw new IOException("boom");
        }

        @Paramixel.Id("fails")
        public static void fails(final Context context) {
            throw new FailException("hook failure");
        }

        @Paramixel.Id("skips")
        public static void skips(final Context context) {
            throw new SkipException("not now");
        }

        @Paramixel.Id("aborts")
        public static void aborts(final Context context) {
            throw new AbortException("stop");
        }

        @Paramixel.Id("count")
        public static void count(final Context context) {
            Records.EVENTS.add("count");
        }

        @Paramixel.Id("converged")
        public static boolean converged(final Context context, final Status status) {
            return Records.EVENTS.size() >= 2;
        }
    }

    /**
     * Anchor class with one identifier declared twice.
     */
    public static final class Duplicates {

        private Duplicates() {
            // Intentionally empty
        }

        @Paramixel.Id("dup")
        public static void first(final Context context) {
            // Intentionally empty
        }

        @Paramixel.Id("dup")
        public static void second(final Context context) {
            // Intentionally empty
        }
    }

    /**
     * Anchor class with mis-shaped hooks.
     */
    public static final class Malformed {

        private Malformed() {
            // Intentionally empty
        }

        @Paramixel.Id("notStatic")
        public void notStatic(final Context context) {
            // Intentionally empty
        }

        @Paramixel.Id("returnsSomething")
        public static Context returnsSomething(final Context context) {
            return context;
        }

        @Paramixel.Id("twoParameters")
        public static void twoParameters(final Context context, final String extra) {
            // Intentionally empty
        }
    }
}

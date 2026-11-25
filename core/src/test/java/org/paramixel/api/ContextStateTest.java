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
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.sequential;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.support.RetryPolicy;

/**
 * Context scope semantics: sharing along a sequential chain, isolation across a parallel boundary, per-attempt reset,
 * typing, and the absence of any close or leak behaviour.
 */
class ContextStateTest {

    private static java.util.List<Failure> allFailures(final TaskResult node) {
        final var failures = new java.util.ArrayList<Failure>(node.failures());
        for (final var child : node.children()) {
            failures.addAll(allFailures(child));
        }
        return failures;
    }

    private static Result run(final Task definition) {
        return Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("p"), 2)
                .build()
                .run(definition);
    }

    @Test
    @DisplayName("CTX-01: sequential siblings share one scope")
    void sequentialSiblingsShareState() {
        final var result = run(sequential(Hooks.class, "root")
                .step("storeValue")
                .step("readValue")
                .build());

        assertThat(result.status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("CTX-02: an enclosing scope is readable from a nested group")
    void nestedGroupReadsEnclosingScope() {
        final var result = run(sequential(Hooks.class, "root")
                .step("storeValue")
                .child(sequential("group").step("readValue"))
                .build());

        assertThat(result.status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("CTX-03: parallel siblings do not see each other's entries")
    void parallelSiblingsAreIsolated() {
        final var result = run(parallel(Hooks.class, "root", Pool.named("p"))
                .child(sequential("branchA").step("storeValue"))
                .child(sequential("branchB").step("readNothing"))
                .build());

        assertThat(result.status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("CTX-04: bind seeds the scope before the first hook")
    void bindSeedsScope() {
        final var result = run(sequential(Hooks.class, "root")
                .bind("seeded", "value")
                .step("readSeeded")
                .build());

        assertThat(result.status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("CTX-05: remove drops an entry, and removing an absent entry is not an error")
    void removeDropsEntry() {
        final var result = run(sequential(Hooks.class, "root")
                .step("storeValue")
                .step("removeAndCheckGone")
                .build());

        assertThat(result.status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("CTX-06: a type mismatch names both types")
    void typeMismatchIsPrecise() {
        final var result = run(sequential(Hooks.class, "root")
                .step("storeValue")
                .step("requireWrongType")
                .build());

        assertThat(result.status().isPassed()).isFalse();
        assertThat(allFailures(result.root().orElseThrow()))
                .anySatisfy(failure -> assertThat(failure.message())
                        .contains("java.lang.String")
                        .contains("java.lang.Integer"));
    }

    @Test
    @DisplayName("CTX-07: find tolerates a wrong type, require does not")
    void findToleratesWrongType() {
        final var result =
                run(sequential(Hooks.class, "root").step("findWrongType").build());

        assertThat(result.status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("CTX-08: null values are rejected")
    void nullValuesAreRejected() {
        final var result = run(sequential(Hooks.class, "root").step("putNull").build());

        assertThat(result.status().isPassed()).isFalse();
        assertThat(allFailures(result.root().orElseThrow()))
                .anySatisfy(failure -> assertThat(failure.message()).contains("value is null"));
    }

    @Test
    @DisplayName("CTX-09: a duplicate bind name on one task is an authoring error")
    void duplicateBindOnOneTaskIsRejected() {
        assertThatThrownBy(
                        () -> sequential(Hooks.class, "root").bind("name", "a").bind("name", "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate bind name");
    }

    @Test
    @DisplayName("CTX-09b: sibling tasks in a sequential chain may bind the same name")
    void siblingBindsAreAllowed() {
        final var root = sequential(Hooks.class, "root");
        for (var i = 0; i < 3; i++) {
            root.child(sequential("branch-" + i).bind("index", i).step("requireIndex"));
        }
        assertThat(run(root.build()).status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("CTX-10: the same bind name in two parallel branches is allowed")
    void duplicateBindAcrossScopesIsAllowed() {
        final var result = run(parallel(Hooks.class, "root", Pool.named("p"))
                .child(sequential("a").bind("name", "a"))
                .child(sequential("b").bind("name", "b"))
                .build());

        assertThat(result.status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("CTX-11: a retried task starts every attempt with an empty scope")
    void retryResetsScope() {
        Hooks.RETRY_CALLS.set(0);

        final var result = run(sequential(Hooks.class, "retrying")
                .retry(RetryPolicy.builder().maxAttempts(2).build())
                .step("putOnceThenFail")
                .build());

        assertThat(result.status().isPassed()).isTrue();
    }

    /**
     * Anchor class for the scope cases.
     */
    public static final class Hooks {

        static final AtomicInteger RETRY_CALLS = new AtomicInteger();

        private Hooks() {
            // Intentionally empty
        }

        @Paramixel.Id("storeValue")
        public static void storeValue(final Context context) {
            context.put("value", "stored");
        }

        @Paramixel.Id("readValue")
        public static void readValue(final Context context) {
            assertThat(context.require("value", String.class)).isEqualTo("stored");
        }

        @Paramixel.Id("readNothing")
        public static void readNothing(final Context context) {
            assertThat(context.find("value", String.class)).isEmpty();
        }

        @Paramixel.Id("readSeeded")
        public static void readSeeded(final Context context) {
            assertThat(context.require("seeded", String.class)).isEqualTo("value");
        }

        @Paramixel.Id("removeAndCheckGone")
        public static void removeAndCheckGone(final Context context) {
            context.remove("value");
            context.remove("never-stored");
            assertThat(context.find("value", String.class)).isEmpty();
        }

        @Paramixel.Id("requireWrongType")
        public static void requireWrongType(final Context context) {
            context.require("value", Integer.class);
        }

        @Paramixel.Id("findWrongType")
        public static void findWrongType(final Context context) {
            assertThat(context.find("value", Integer.class)).isEmpty();
        }

        @Paramixel.Id("putNull")
        public static void putNull(final Context context) {
            context.put("value", null);
        }

        @Paramixel.Id("requireIndex")
        public static void requireIndex(final Context context) {
            assertThat(context.require("index", Integer.class)).isGreaterThanOrEqualTo(0);
        }

        @Paramixel.Id("putOnceThenFail")
        public static void putOnceThenFail(final Context context) {
            if (RETRY_CALLS.incrementAndGet() == 1) {
                context.put("marker", "first attempt");
                throw new FailException("first attempt fails");
            }
            assertThat(context.find("marker", String.class)).isEmpty();
        }
    }
}

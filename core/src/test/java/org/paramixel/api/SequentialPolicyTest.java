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
import static org.paramixel.api.Tasks.task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.exception.SkipException;

/**
 * Failure continuation and status aggregation for sequential groups.
 */
class SequentialPolicyTest {

    private static Result run(final Task definition) {
        return Runner.builder().listener(Listener.NOOP).build().run(definition);
    }

    @Test
    @DisplayName("FAIL-01: dependent sequence stops after a failure with an explicit reason")
    void dependentStopsAfterFailure() {
        final AtomicInteger later = new AtomicInteger();
        final Task definition = sequential("root")
                .step("a", () -> {
                    throw new IllegalStateException("boom");
                })
                .step("b", later::incrementAndGet)
                .step("c", later::incrementAndGet)
                .build();

        final var result = run(definition);
        final var children = result.root().orElseThrow().children();

        assertThat(later).hasValue(0);
        assertThat(children.get(0).status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(children.get(1).status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(children.get(1).reason().orElseThrow()).contains("dependency");
        assertThat(children.get(2).status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
    }

    @Test
    @DisplayName("FAIL-02: independent sequence attempts every child and still aggregates failure")
    void independentContinuesAfterFailure() {
        final List<String> calls = new ArrayList<>();
        final Task definition = sequential("root")
                .independent()
                .step("a", () -> {
                    calls.add("a");
                    throw new IllegalStateException("boom");
                })
                .step("b", () -> calls.add("b"))
                .build();

        final var result = run(definition);

        assertThat(calls).containsExactly("a", "b");
        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.root().orElseThrow().children().get(1).status().kind())
                .isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("FAIL-03: a dependent sequence stops after an assumption abort")
    void dependentStopsAfterAbort() {
        final AtomicInteger later = new AtomicInteger();
        final Task definition = sequential("root")
                .step("a", () -> {
                    throw AbortException.abort("assumption failed");
                })
                .step("b", later::incrementAndGet)
                .build();

        final var result = run(definition);
        final var children = result.root().orElseThrow().children();

        assertThat(later).hasValue(0);
        assertThat(children.get(0).status().kind()).isEqualTo(Status.Kind.ABORTED);
        assertThat(children.get(1).status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.ABORTED);
    }

    @Test
    @DisplayName("FAIL-04: independent sequences continue after an abort or a skip")
    void independentContinuesAfterAbortAndSkip() {
        final List<String> calls = new ArrayList<>();
        final Task definition = sequential("root")
                .independent()
                .step("abort", () -> {
                    calls.add("abort");
                    throw AbortException.abort("assumption failed");
                })
                .step("skip", () -> {
                    calls.add("skip");
                    throw SkipException.skip("not applicable");
                })
                .step("pass", () -> calls.add("pass"))
                .build();

        final var result = run(definition);

        assertThat(calls).containsExactly("abort", "skip", "pass");
        assertThat(result.status().kind()).isEqualTo(Status.Kind.ABORTED);
    }

    @Test
    @DisplayName("FAIL-06: a passed body with a failed child reports the task as failed")
    void bodyPassesButChildFails() {
        final Task definition = task("task")
                .step(
                        "body",
                        () -> {},
                        children -> children.step("child", () -> {
                            throw new IllegalStateException("child failed");
                        }))
                .build();

        final var result = run(definition);
        final var node = result.root().orElseThrow();
        final var body = node.children().get(0);

        assertThat(node.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(body.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(body.bodyStatus().orElseThrow().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.exitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("FAIL-07: an independent inner group does not make the outer sequence independent")
    void innerIndependentDoesNotAffectOuter() {
        final AtomicInteger outer = new AtomicInteger();
        final Task definition = sequential("outer")
                .sequential(
                        "inner",
                        inner -> inner.independent()
                                .step("a", () -> {
                                    throw new IllegalStateException("boom");
                                })
                                .step("b", () -> {}))
                .step("outer-next", outer::incrementAndGet)
                .build();

        final var result = run(definition);

        assertThat(outer).hasValue(0);
        assertThat(result.root().orElseThrow().children().get(1).status().kind())
                .isEqualTo(Status.Kind.SKIPPED);
    }

    @Test
    @DisplayName("FAIL-08: mixed passed and skipped children aggregate to skipped")
    void skippedIsNotPassing() {
        final Task definition = task("root")
                .independent()
                .step("pass", () -> {})
                .step("skip", () -> {
                    throw SkipException.skip("not applicable");
                })
                .build();

        final var result = run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(result.exitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("skip and abort map to the configured exit code without rewriting the status")
    void exitPolicyIsSeparateFromStatus() {
        final Task skipped = task("root")
                .step("skip", () -> {
                    throw SkipException.skip("not applicable");
                })
                .build();

        final var defaultRun = run(skipped);
        assertThat(defaultRun.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(defaultRun.exitCode()).isEqualTo(1);

        final var permissive = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(java.util.Map.of(
                        Configuration.FAILURE_ON_SKIP, "false",
                        Configuration.FAILURE_ON_ABORT, "false")))
                .build()
                .run(skipped);
        assertThat(permissive.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(permissive.exitCode()).isZero();
    }

    @Test
    @DisplayName("an empty named task passes and is reported as a no-op")
    void emptyTaskPasses() {
        final var result = run(task("empty").build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("EMPTY_TASK"));
    }
}

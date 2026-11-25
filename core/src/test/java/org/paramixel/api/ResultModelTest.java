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
import static org.paramixel.api.Tasks.task;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.exception.SkipException;
import org.paramixel.api.support.RetryPolicy;

/**
 * The immutable result model: identity, counters, aggregation, and exit policy.
 */
class ResultModelTest {

    @Test
    void deliberateSkipExposesItsExplicitReason() {
        final var reason = "fixture unavailable";
        final var definition = task("root")
                .step("skipped", () -> {
                    throw SkipException.skip(reason);
                })
                .build();
        final var result = run(definition);

        assertThat(result.root().orElseThrow().children()).singleElement().satisfies(skipped -> {
            assertThat(skipped.status().kind()).isEqualTo(Status.Kind.SKIPPED);
            assertThat(skipped.reason()).hasValue(reason);
        });
    }

    private static Result run(final Task definition) {
        return Runner.builder().listener(Listener.NOOP).build().run(definition);
    }

    @Test
    @DisplayName("execution and finalization durations end at the occurrence's completion, not at projection")
    void durationsEndAtCompletion() {
        final var definition = task("root")
                .child(task("quick").step("work", () -> {}).after(() -> {}))
                .child(task("slow").step("work", () -> Thread.sleep(600L)))
                .build();

        final var quick = run(definition).root().orElseThrow().children().get(0);

        assertThat(quick.executionDuration())
                .hasValueSatisfying(duration -> assertThat(duration).isLessThan(Duration.ofMillis(300)));
        assertThat(quick.finalizationDuration())
                .hasValueSatisfying(duration -> assertThat(duration).isLessThan(Duration.ofMillis(300)));
    }

    @Test
    @DisplayName("result trees are immutable snapshots with stable identity")
    void resultIsImmutableSnapshot() {
        final var definition = task("root").step("one", () -> {}).build();
        final var first = run(definition);
        final var second = run(definition);

        assertThat(first.runId()).isNotEqualTo(second.runId());
        assertThat(first.root().orElseThrow().occurrenceId()).isEqualTo("0");
        assertThatThrownBy(() -> first.root().orElseThrow().children().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.diagnostics().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(first.root().orElseThrow().path()).isEqualTo("root");
        assertThat(first.root().orElseThrow().children().get(0).path()).isEqualTo("root > one");
    }

    @Test
    @DisplayName("display paths separate levels with ' > ' so slashes inside names stay within one level")
    void displayPathSeparatesLevelsWithGreaterThan() {
        final var definition = task("root")
                .sequential("apache/kafka:3.7.2", image -> image.step("produce()", () -> {}))
                .build();
        final var image = run(definition).root().orElseThrow().children().get(0);

        assertThat(image.path()).isEqualTo("root > apache/kafka:3.7.2");
        assertThat(image.children().get(0).path()).isEqualTo("root > apache/kafka:3.7.2 > produce()");
    }

    @Test
    @DisplayName("counters count occurrences once regardless of phases")
    void countersCountOccurrencesOnce() {
        final var definition = parallel("root", Pool.named("p"))
                .step("pass", () -> {})
                .step("skip", () -> {
                    throw SkipException.skip("no");
                })
                .step("abort", () -> {
                    throw AbortException.abort("assumption");
                })
                .step("fail", () -> {
                    throw new IllegalStateException("boom");
                })
                .build();

        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("p"), 4)
                .build()
                .run(definition);

        assertThat(result.occurrenceCount()).isEqualTo(5);
        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.abortedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(2);
        assertThat(result.attemptCount()).isZero();
        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.passedCount()
                        + result.failedCount()
                        + result.skippedCount()
                        + result.abortedCount()
                        + result.canceledCount())
                .isEqualTo(result.occurrenceCount());
    }

    @Test
    @DisplayName("severity aggregation follows FAILED > CANCELED > ABORTED > SKIPPED > PASSED")
    void severityOrdering() {
        assertThat(Status.mostSevere(List.of(Status.PASSED, Status.SKIPPED)).kind())
                .isEqualTo(Status.Kind.SKIPPED);
        assertThat(Status.mostSevere(List.of(Status.SKIPPED, Status.aborted("a")))
                        .kind())
                .isEqualTo(Status.Kind.ABORTED);
        assertThat(Status.mostSevere(List.of(Status.aborted("a"), Status.canceled("c")))
                        .kind())
                .isEqualTo(Status.Kind.CANCELED);
        assertThat(Status.mostSevere(List.of(Status.canceled("c"), Status.failed("f")))
                        .kind())
                .isEqualTo(Status.Kind.FAILED);
        assertThat(Status.mostSevere(List.of()).kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("a retried-and-passed run is a passing run")
    void retriedRunPasses() {
        final var definition = task("root")
                .child(task("retried")
                        .retry(RetryPolicy.attempts(2))
                        .step("probe", new AtomicInteger()::incrementAndGet))
                .build();

        final var result = run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.exitCode()).isZero();
        assertThat(result.failedCount()).isZero();
    }

    @Test
    @DisplayName("a successful retry does not count historical attempt failures as occurrences")
    void retriedAttemptsAreNotCountedAsFailures() {
        final var attempts = new AtomicInteger();
        final var definition = task("root")
                .child(Tasks.sequential("retried")
                        .retry(RetryPolicy.attempts(3))
                        .step("probe", () -> {
                            if (attempts.incrementAndGet() < 3) {
                                throw new IllegalStateException("not yet");
                            }
                        }))
                .build();

        final var result = run(definition);

        assertThat(attempts).hasValue(3);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.occurrenceCount()).isEqualTo(3);
        assertThat(result.failedCount()).isZero();
        assertThat(result.passedCount()).isEqualTo(3);
        assertThat(result.attemptCount()).isEqualTo(3);
        // Historical failures stay visible in the tree even though they are not counted as occurrences.
        final var retried = result.root().orElseThrow().children().get(0);
        assertThat(retried.children()).hasSize(3);
        assertThat(retried.children().get(0).status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(retried.children().get(2).status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("exit code honors failureOnAbort and failureOnSkip configuration")
    void exitCodeConfiguration() {
        final Task aborted = task("root")
                .step("abort", () -> {
                    throw AbortException.abort("assumption");
                })
                .build();
        assertThat(run(aborted).exitCode()).isEqualTo(1);

        final var permissive = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.FAILURE_ON_ABORT, "false")))
                .build()
                .run(aborted);
        assertThat(permissive.status().kind()).isEqualTo(Status.Kind.ABORTED);
        assertThat(permissive.exitCode()).isZero();
    }

    @Test
    @DisplayName("diagnostics and configuration summary expose redacted run metadata")
    void diagnosticsAndSummary() {
        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(
                        "paramixel.ansi", "false",
                        "paramixel.token", "super-secret",
                        "unrelated.key", "ignored")))
                .build()
                .run(task("root").step("work", () -> {}).build());

        assertThat(result.configurationSummary()).containsEntry("paramixel.ansi", "false");
        assertThat(result.configurationSummary()).containsEntry("paramixel.token", "***");
        assertThat(result.configurationSummary()).doesNotContainKey("unrelated.key");
    }

    @Test
    @DisplayName("result counters and status are stable after a later run")
    void laterRunDoesNotMutateEarlierResult() {
        final var definition = task("root").step("work", () -> {}).build();
        final var first = run(definition);
        final var firstSnapshot = first.root().orElseThrow();
        run(definition);

        assertThat(firstSnapshot.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(first.root().orElseThrow()).isSameAs(firstSnapshot);
    }

    @Test
    @DisplayName("failure precedence puts the operation failure first")
    void failurePrecedence() {
        final var definition = task("root")
                .after(() -> {
                    throw new IllegalStateException("cleanup failed");
                })
                .step("body", () -> {
                    throw new IllegalStateException("operation failed");
                })
                .build();

        final var result = run(definition);
        final var child = result.root().orElseThrow().children().get(0);

        assertThat(child.failures().get(0).message()).contains("operation failed");
        assertThat(child.failures().get(0).isPrimary()).isTrue();
        assertThat(result.root().orElseThrow().failures().get(0).message()).contains("cleanup failed");
    }

    @Test
    @DisplayName("counters are set by name and round-trip through accessors and diagnostic merges")
    void namedCountersRoundTrip() {
        final var result = Result.builder()
                .runId("named-counters")
                .occurrenceCount(10)
                .attemptCount(4)
                .passedCount(5)
                .failedCount(2)
                .skippedCount(1)
                .abortedCount(1)
                .canceledCount(1)
                .timedOutCount(2)
                .build();
        final var merged = result.withAdditionalDiagnostics(List.of("late diagnostic"));

        for (Result value : List.of(result, merged)) {
            assertThat(value.occurrenceCount()).isEqualTo(10);
            assertThat(value.attemptCount()).isEqualTo(4);
            assertThat(value.passedCount()).isEqualTo(5);
            assertThat(value.failedCount()).isEqualTo(2);
            assertThat(value.skippedCount()).isEqualTo(1);
            assertThat(value.abortedCount()).isEqualTo(1);
            assertThat(value.canceledCount()).isEqualTo(1);
            assertThat(value.timedOutCount()).isEqualTo(2);
        }
        assertThat(merged.diagnostics()).containsExactly("late diagnostic");
    }

    @Test
    @DisplayName("inconsistent or negative counters are rejected")
    void inconsistentCountersAreRejected() {
        assertThatThrownBy(() -> Result.builder()
                        .runId("too-many-passed")
                        .occurrenceCount(10)
                        .passedCount(99)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("occurrenceCount");
        assertThatThrownBy(() -> Result.builder()
                        .runId("status-sum")
                        .occurrenceCount(3)
                        .passedCount(2)
                        .failedCount(2)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("occurrenceCount");
        assertThatThrownBy(() -> Result.builder()
                        .runId("timeouts")
                        .occurrenceCount(1)
                        .timedOutCount(2)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timedOutCount");
        assertThatThrownBy(() -> Result.builder().failedCount(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failedCount");
    }

    @Test
    @DisplayName("engine counters satisfy the builder's consistency rules for a mixed run")
    void engineCountersAreConsistent() {
        final var definition = task("root")
                .independent()
                .step("passes", () -> {})
                .step("fails", () -> {
                    throw new IllegalStateException("boom");
                })
                .step("skips", () -> {
                    throw new SkipException("not applicable");
                })
                .build();

        final var result = run(definition);

        assertThat(result.passedCount()
                        + result.failedCount()
                        + result.skippedCount()
                        + result.abortedCount()
                        + result.canceledCount())
                .isEqualTo(result.occurrenceCount());
        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isEqualTo(1);
    }
}

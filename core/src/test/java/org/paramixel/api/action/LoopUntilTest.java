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

package org.paramixel.api.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.exception.FailException.fail;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.exception.AbortException;

@DisplayName("Loop termination")
class LoopUntilTest {

    private static Descriptor run(final Action action) {
        return Runner.builder()
                .configuration(Configuration.of(Map.of()))
                .build()
                .run(action)
                .descriptor()
                .orElseThrow();
    }

    @Test
    @DisplayName("untilPassed stops after the first passing iteration")
    void untilPassedStopsOnPass() {
        var iterations = new AtomicInteger();
        var root = run(Loop.loop("loop")
                .maxIterations(10)
                .untilPassed()
                .body(Step.step("body", context -> iterations.incrementAndGet()))
                .build());

        assertThat(root.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(iterations.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("untilPassed retries until an iteration passes")
    void untilPassedRetriesUntilPass() {
        var iterations = new AtomicInteger();
        var root = run(Loop.loop("loop")
                .maxIterations(10)
                .untilPassed()
                .body(Step.step("body", context -> {
                    if (iterations.incrementAndGet() < 3) {
                        throw fail("not yet");
                    }
                }))
                .build());

        assertThat(root.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(iterations.get()).isEqualTo(3);
        // A loop that retried and then passed has FAILED children under a PASSED root.
        assertThat(root.children().get(0).status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(root.children().get(2).status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("unreached iterations are reported as skipped because the loop was satisfied")
    void unreachedIterationsCarryTheSatisfiedReason() {
        var root = run(Loop.loop("loop")
                .maxIterations(5)
                .untilPassed()
                .body(Step.step("body", context -> {}))
                .build());

        var children = root.children();
        assertThat(children).hasSize(5);
        assertThat(children.get(0).status().kind()).isEqualTo(Status.Kind.PASSED);
        for (var i = 1; i < children.size(); i++) {
            assertThat(children.get(i).status().kind()).isEqualTo(Status.Kind.SKIPPED);
            assertThat(children.get(i).status().message()).hasValue("loop satisfied");
        }
    }

    @Test
    @DisplayName("the BiPredicate form receives each iteration status")
    void biPredicateReceivesStatus() {
        var iterations = new AtomicInteger();
        var seen = new java.util.concurrent.CopyOnWriteArrayList<Status>();
        var root = run(Loop.loop("loop")
                .maxIterations(4)
                .until((context, status) -> {
                    seen.add(status);
                    return status.isPassed();
                })
                .body(Step.step("body", context -> {
                    if (iterations.incrementAndGet() < 3) {
                        throw fail("attempt " + iterations.get() + " fails");
                    }
                }))
                .build());

        assertThat(seen).hasSize(3);
        assertThat(seen.get(0).kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(seen.get(1).kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(seen.get(2).kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(root.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("a loop that never satisfies its predicate fails")
    void neverSatisfiedFails() {
        var root = run(Loop.loop("loop")
                .maxIterations(3)
                .until((context, status) -> false)
                .body(Step.step("body", context -> {}))
                .build());

        assertThat(root.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(root.children()).hasSize(3);
    }

    @Test
    @DisplayName("a delay schedule is applied between iterations only")
    void delayBetweenIterations() {
        var iterations = new AtomicInteger();
        var start = System.nanoTime();
        var root = run(Loop.loop("loop")
                .maxIterations(3)
                .delay(org.paramixel.api.support.Backoff.fixed(java.time.Duration.ofMillis(20)))
                .body(Step.step("body", context -> iterations.incrementAndGet()))
                .build());
        var elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertThat(root.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(iterations.get()).isEqualTo(3);
        // Two gaps between three iterations; no delay after the last one.
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(40L);
    }

    @Test
    @DisplayName("an aborted iteration stops the loop and skips the rest")
    void abortedIterationStopsTheLoop() {
        var iterations = new AtomicInteger();
        var root = run(Loop.loop("loop")
                .maxIterations(4)
                .body(Step.step("body", context -> {
                    if (iterations.incrementAndGet() == 1) {
                        throw AbortException.abort("stop");
                    }
                }))
                .build());

        assertThat(root.status().kind()).isEqualTo(Status.Kind.ABORTED);
        assertThat(iterations.get()).isEqualTo(1);
        var children = root.children();
        assertThat(children.get(0).status().kind()).isEqualTo(Status.Kind.ABORTED);
        assertThat(children.subList(1, children.size()))
                .allMatch(child -> child.status().kind() == Status.Kind.SKIPPED);
    }

    @Test
    @DisplayName("a loop without a predicate reports the aggregate of its iterations")
    void withoutPredicateAggregates() {
        var root = run(Loop.loop("loop")
                .maxIterations(2)
                .body(Step.step("body", context -> {}))
                .build());

        assertThat(root.status().kind()).isEqualTo(Status.Kind.PASSED);
        List<Descriptor> children = root.children();
        assertThat(children).hasSize(2);
        assertThat(children).allMatch(child -> child.status().kind() == Status.Kind.PASSED);
    }
}

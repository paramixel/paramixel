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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;

@DisplayName("Loop action")
class LoopTest {

    private static final Action STEP = Step.of("step", s -> {});

    @Test
    @DisplayName("all iterations fail returns failed (no predicate)")
    void allIterationsFailReturnsFailedNoPredicate() {
        var counter = new AtomicInteger();
        var action = Loop.builder("all-fail")
                .body(Step.of("step", context -> {
                    counter.incrementAndGet();
                    FailException.fail("fail");
                }))
                .maxIterations(3)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("all iterations run even when body passes (no predicate)")
    void allIterationsRunEvenWhenBodyPassesNoPredicate() {
        var counter = new AtomicInteger();
        var action = Loop.builder("all-run")
                .body(Step.of("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count < 2) {
                        FailException.fail("not yet");
                    }
                }))
                .maxIterations(5)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(counter.get()).isEqualTo(5);
    }

    @Test
    @DisplayName("single iteration passes (no predicate)")
    void singleIterationPassesNoPredicate() {
        var action = Loop.builder("single")
                .body(Step.of("step", context -> {}))
                .maxIterations(1)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
    }

    @Test
    @DisplayName("single iteration fails (no predicate)")
    void singleIterationFailsNoPredicate() {
        var action = Loop.builder("single")
                .body(Step.of("step", context -> FailException.fail("fail")))
                .maxIterations(1)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
    }

    @Test
    @DisplayName("predicate true on first iteration")
    void predicateTrueOnFirstIteration() {
        var counter = new AtomicInteger();
        var action = Loop.builder("pred-true-first")
                .body(Step.of("step", context -> counter.incrementAndGet()))
                .until(context -> true)
                .maxIterations(3)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(counter.get()).isEqualTo(1);
        assertThat(root.children().get(0).isPassed()).isTrue();
        assertThat(root.children().get(1).isSkipped()).isTrue();
        assertThat(root.children().get(2).isSkipped()).isTrue();
    }

    @Test
    @DisplayName("predicate true on third iteration")
    void predicateTrueOnThirdIteration() {
        var counter = new AtomicInteger();
        var action = Loop.builder("pred-true-third")
                .body(Step.of("step", context -> counter.incrementAndGet()))
                .until(context -> counter.get() >= 3)
                .maxIterations(5)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
        assertThat(root.children().get(0).isPassed()).isTrue();
        assertThat(root.children().get(1).isPassed()).isTrue();
        assertThat(root.children().get(2).isPassed()).isTrue();
        assertThat(root.children().get(3).isSkipped()).isTrue();
        assertThat(root.children().get(4).isSkipped()).isTrue();
    }

    @Test
    @DisplayName("predicate never true returns failed")
    void predicateNeverTrueReturnsFailed() {
        var counter = new AtomicInteger();
        var action = Loop.builder("pred-never-true")
                .body(Step.of("step", context -> counter.incrementAndGet()))
                .until(context -> false)
                .maxIterations(3)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("predicate InternalError propagates")
    void predicateInternalErrorPropagates() {
        var error = new InternalError("fatal predicate");
        var action = Loop.builder("pred-internal-error")
                .body(Step.of("step", context -> {}))
                .until(context -> {
                    throw error;
                })
                .maxIterations(1)
                .build();

        assertThatThrownBy(() -> Runner.builder().build().run(action)).isSameAs(error);
    }

    @Test
    @DisplayName("predicate recoverable exception returns false")
    void predicateRecoverableExceptionReturnsFalse() {
        var counter = new AtomicInteger();
        var action = Loop.builder("pred-recoverable")
                .body(Step.of("step", context -> counter.incrementAndGet()))
                .until(context -> {
                    throw new RuntimeException("recoverable predicate");
                })
                .maxIterations(2)
                .build();

        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("abort stops loop")
    void abortStopsLoop() {
        var counter = new AtomicInteger();
        var action = Loop.builder("abort")
                .body(Step.of("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count == 2) {
                        AbortedException.abort("abort");
                    }
                    FailException.fail("fail");
                }))
                .maxIterations(5)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isAborted()).isTrue();
        assertThat(counter.get()).isEqualTo(2);
        assertThat(root.children().get(0).isFailed()).isTrue();
        assertThat(root.children().get(1).isAborted()).isTrue();
        assertThat(root.children().get(2).isSkipped()).isTrue();
        assertThat(root.children().get(3).isSkipped()).isTrue();
        assertThat(root.children().get(4).isSkipped()).isTrue();
    }

    @Test
    @DisplayName("displayName returns configured name")
    void displayNameReturnsConfiguredName() {
        var action = Loop.builder("my-loop").body(STEP).maxIterations(1).build();
        assertThat(action.displayName()).isEqualTo("my-loop");
    }

    @Test
    @DisplayName("accessors return expected values")
    void accessorsReturnExpectedValues() {
        var action = Loop.builder("loop")
                .body(STEP)
                .until((org.paramixel.api.Context ctx) -> true)
                .maxIterations(5)
                .build();

        assertThat(action.body()).isSameAs(STEP);
        assertThat(action.maxIterations()).isEqualTo(5);
        assertThat(action.until()).isPresent();
    }

    @Test
    @DisplayName("until absent when not configured")
    void untilAbsentWhenNotConfigured() {
        var action = Loop.builder("loop").body(STEP).maxIterations(1).build();
        assertThat(action.until()).isEmpty();
    }

    @Test
    @DisplayName("descriptor tree has maxIterations children")
    void descriptorTreeHasMaxIterationsChildren() {
        var action = Loop.builder("loop-tree")
                .body(Step.of("step", context -> {}))
                .maxIterations(4)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.children()).hasSize(4);
    }

    @Test
    @DisplayName("delay linear provides constant delay between iterations")
    void delayLinearConstantDelayBetweenIterations() {
        var counter = new AtomicInteger();
        var action = Loop.builder("delay-linear")
                .body(Step.of("step", context -> {
                    counter.incrementAndGet();
                }))
                .maxIterations(3)
                .delay(Duration.ofMillis(50))
                .build();
        var start = System.currentTimeMillis();
        var result = Runner.builder().build().run(action);
        var elapsed = System.currentTimeMillis() - start;
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
        // 2 delays (after iter 1 and 2) x 50ms = at least 100ms
        assertThat(elapsed).isGreaterThanOrEqualTo(100L);
    }

    @Test
    @DisplayName("delay exponential doubles between iterations")
    void delayExponentialDoublesBetweenIterations() {
        var counter = new AtomicInteger();
        var action = Loop.builder("delay-exp")
                .body(Step.of("step", context -> {
                    counter.incrementAndGet();
                }))
                .maxIterations(4)
                .delay(new Loop.DelayPolicy.Exponential(Duration.ofMillis(10)))
                .build();
        var start = System.currentTimeMillis();
        var result = Runner.builder().build().run(action);
        var elapsed = System.currentTimeMillis() - start;
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(counter.get()).isEqualTo(4);
        // 3 delays: 10 + 20 + 40 = at least 70ms
        assertThat(elapsed).isGreaterThanOrEqualTo(70L);
    }

    @Test
    @DisplayName("delay not applied before first iteration")
    void delayNotAppliedBeforeFirstIteration() {
        var counter = new AtomicInteger();
        var action = Loop.builder("delay-no-first")
                .body(Step.of("step", context -> {
                    counter.incrementAndGet();
                }))
                .maxIterations(1)
                .delay(Duration.ofMillis(500))
                .build();
        var start = System.currentTimeMillis();
        var result = Runner.builder().build().run(action);
        var elapsed = System.currentTimeMillis() - start;
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(counter.get()).isEqualTo(1);
        // No delay after last iteration, so total should be < 500ms
        assertThat(elapsed).isLessThan(500L);
    }

    @Test
    @DisplayName("delay not applied after last iteration")
    void delayNotAppliedAfterLastIteration() {
        var counter = new AtomicInteger();
        var action = Loop.builder("delay-no-last")
                .body(Step.of("step", context -> {
                    counter.incrementAndGet();
                }))
                .maxIterations(2)
                .delay(Duration.ofMillis(50))
                .build();
        var start = System.currentTimeMillis();
        var result = Runner.builder().build().run(action);
        var elapsed = System.currentTimeMillis() - start;
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(counter.get()).isEqualTo(2);
        // Only 1 delay (after iter 1), so at least 50ms
        assertThat(elapsed).isGreaterThanOrEqualTo(50L);
    }

    @Test
    @DisplayName("delay not applied after abort")
    void delayNotAppliedAfterAbort() {
        var counter = new AtomicInteger();
        var action = Loop.builder("delay-abort")
                .body(Step.of("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count == 1) {
                        AbortedException.abort("abort");
                    }
                }))
                .maxIterations(5)
                .delay(Duration.ofMillis(500))
                .build();
        var start = System.currentTimeMillis();
        var result = Runner.builder().build().run(action);
        var elapsed = System.currentTimeMillis() - start;
        var root = result.descriptor().orElseThrow();

        assertThat(root.isAborted()).isTrue();
        assertThat(counter.get()).isEqualTo(1);
        // Aborted on first iteration, no delay fires
        assertThat(elapsed).isLessThan(500L);
    }

    @Test
    @DisplayName("delay not applied after satisfied predicate")
    void delayNotAppliedAfterSatisfiedPredicate() {
        var counter = new AtomicInteger();
        var action = Loop.builder("delay-satisfied")
                .body(Step.of("step", context -> {
                    counter.incrementAndGet();
                }))
                .until(context -> counter.get() >= 2)
                .maxIterations(5)
                .delay(Duration.ofMillis(500))
                .build();
        var start = System.currentTimeMillis();
        var result = Runner.builder().build().run(action);
        var elapsed = System.currentTimeMillis() - start;
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(counter.get()).isEqualTo(2);
        // Only 1 delay (after iter 1), so at least 500ms
        assertThat(elapsed).isGreaterThanOrEqualTo(500L);
    }

    @Test
    @DisplayName("delay accessor returns configured policy")
    void delayAccessorReturnsConfiguredPolicy() {
        var policy = new Loop.DelayPolicy.Linear(Duration.ofMillis(100));
        var action = Loop.builder("delay-accessor")
                .body(STEP)
                .maxIterations(1)
                .delay(policy)
                .build();
        assertThat(action.delay()).isPresent();
        assertThat(action.delay().get()).isSameAs(policy);
    }

    @Test
    @DisplayName("delay accessor empty when not configured")
    void delayAccessorEmptyWhenNotConfigured() {
        var action = Loop.builder("no-delay").body(STEP).maxIterations(1).build();
        assertThat(action.delay()).isEmpty();
    }

    @Nested
    @DisplayName("Builder")
    class Builder {

        @Test
        @DisplayName("build throws when body not configured")
        void buildThrowsWhenBodyNotConfigured() {
            assertThatThrownBy(() -> Loop.builder("bad-loop").maxIterations(1).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("body action must be configured");
        }

        @Test
        @DisplayName("build throws when maxIterations not configured")
        void buildThrowsWhenMaxIterationsNotConfigured() {
            assertThatThrownBy(() -> Loop.builder("bad-loop").body(STEP).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("maxIterations must be configured");
        }

        @Test
        @DisplayName("body accepts another Builder")
        void bodyAcceptsAnotherBuilder() {
            var child = Step.of("inner-step", context -> {});
            var loop = Loop.builder("from-builder")
                    .body(Instance.builder("inner", child::getClass).body(child))
                    .maxIterations(2)
                    .build();

            assertThat(loop.displayName()).isEqualTo("from-builder");
            assertThat(loop.maxIterations()).isEqualTo(2);
            assertThat(loop.body()).isNotNull();
        }

        @Test
        @DisplayName("builder can build multiple immutable snapshots")
        void builderCanBuildMultipleImmutableSnapshots() {
            var builder = Loop.builder("loop").body(STEP);
            var first = builder.maxIterations(1).build();
            builder.maxIterations(3);
            var second = builder.build();

            assertThat(first.body()).isSameAs(STEP);
            assertThat(first.maxIterations()).isEqualTo(1);
            assertThat(second.body()).isSameAs(STEP);
            assertThat(second.maxIterations()).isEqualTo(3);
        }
    }
}

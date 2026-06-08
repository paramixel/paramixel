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

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;

@DisplayName("Until action")
class UntilTest {

    private static final Action STEP = Step.of("step", s -> {});

    @Test
    @DisplayName("all iterations fail returns failed")
    void allIterationsFailReturnsFailed() {
        var counter = new AtomicInteger();
        var action = Until.builder("all-fail")
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
    @DisplayName("body passes on second iteration returns passed")
    void bodyPassesOnSecondIterationReturnsPassed() {
        var counter = new AtomicInteger();
        var action = Until.builder("pass-second")
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

        assertThat(root.isPassed()).isTrue();
        assertThat(counter.get()).isEqualTo(2);
        assertThat(root.children().get(0).isFailed()).isTrue();
        assertThat(root.children().get(1).isPassed()).isTrue();
        assertThat(root.children().get(2).isSkipped()).isTrue();
        assertThat(root.children().get(3).isSkipped()).isTrue();
        assertThat(root.children().get(4).isSkipped()).isTrue();
    }

    @Test
    @DisplayName("single iteration passes")
    void singleIterationPasses() {
        var action = Until.builder("single")
                .body(Step.of("step", context -> {}))
                .maxIterations(1)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
    }

    @Test
    @DisplayName("single iteration fails")
    void singleIterationFails() {
        var action = Until.builder("single")
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
        var action = Until.builder("pred-true-first")
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
        var action = Until.builder("pred-true-third")
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
        var action = Until.builder("pred-never-true")
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
    @DisplayName("abort stops loop")
    void abortStopsLoop() {
        var counter = new AtomicInteger();
        var action = Until.builder("abort")
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
        var action = Until.builder("my-until").body(STEP).maxIterations(1).build();
        assertThat(action.displayName()).isEqualTo("my-until");
    }

    @Test
    @DisplayName("accessors return expected values")
    void accessorsReturnExpectedValues() {
        var action = Until.builder("until")
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
        var action = Until.builder("until").body(STEP).maxIterations(1).build();
        assertThat(action.until()).isEmpty();
    }

    @Test
    @DisplayName("descriptor tree has maxIterations children")
    void descriptorTreeHasMaxIterationsChildren() {
        var action = Until.builder("until-tree")
                .body(Step.of("step", context -> {}))
                .maxIterations(4)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.children()).hasSize(4);
    }

    @Nested
    @DisplayName("Builder")
    class Builder {

        @Test
        @DisplayName("build throws when body not configured")
        void buildThrowsWhenBodyNotConfigured() {
            assertThatThrownBy(() -> Until.builder("bad-until").maxIterations(1).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("body action must be configured");
        }

        @Test
        @DisplayName("build throws when maxIterations not configured")
        void buildThrowsWhenMaxIterationsNotConfigured() {
            assertThatThrownBy(() -> Until.builder("bad-until").body(STEP).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("maxIterations must be configured");
        }

        @Test
        @DisplayName("body accepts another Builder")
        void bodyAcceptsAnotherBuilder() {
            var child = Step.of("inner-step", context -> {});
            var until = Until.builder("from-builder")
                    .body(Instance.builder("inner", child::getClass).body(child))
                    .maxIterations(2)
                    .build();

            assertThat(until.displayName()).isEqualTo("from-builder");
            assertThat(until.maxIterations()).isEqualTo(2);
            assertThat(until.body()).isNotNull();
        }

        @Test
        @DisplayName("builder can build multiple immutable snapshots")
        void builderCanBuildMultipleImmutableSnapshots() {
            var builder = Until.builder("until").body(STEP);
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

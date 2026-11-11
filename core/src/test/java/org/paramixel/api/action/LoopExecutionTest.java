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

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Loop execution")
class LoopExecutionTest {

    @Test
    @DisplayName("skipException in body continues loop (no predicate)")
    void skipExceptionInBodyContinuesLoopNoPredicate() {
        var counter = new AtomicInteger();

        var action = Loop.loop("loop")
                .body(Step.step("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count == 1) {
                        throw SkipException.skip("skip");
                    }
                }))
                .maxIterations(3)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isSkipped()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("runtimeException in body continues loop (no predicate)")
    void runtimeExceptionInBodyContinuesLoopNoPredicate() {
        var counter = new AtomicInteger();

        var action = Loop.loop("loop")
                .body(Step.step("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count == 1) {
                        throw new RuntimeException("error");
                    }
                }))
                .maxIterations(3)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("predicate throws fails loop immediately")
    void predicateThrowsFailsLoopImmediately() {
        var counter = new AtomicInteger();

        var action = Loop.loop("loop")
                .body(Step.step("step", context -> counter.incrementAndGet()))
                .until(context -> {
                    throw new RuntimeException("error");
                })
                .maxIterations(3)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.status().message()).isPresent();
        assertThat(root.status().message().get()).contains("until predicate failed");
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("abort on first iteration")
    void abortOnFirstIteration() {
        var counter = new AtomicInteger();

        var action = Loop.loop("loop")
                .body(Step.step("step", context -> {
                    counter.incrementAndGet();
                    throw AbortException.abort("abort");
                }))
                .maxIterations(5)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isAborted()).isTrue();
        assertThat(counter.get()).isEqualTo(1);
        assertThat(root.children().get(0).status().isAborted()).isTrue();
        assertThat(root.children().get(1).status().isSkipped()).isTrue();
        assertThat(root.children().get(2).status().isSkipped()).isTrue();
        assertThat(root.children().get(3).status().isSkipped()).isTrue();
        assertThat(root.children().get(4).status().isSkipped()).isTrue();
    }

    @Test
    @DisplayName("abort on last iteration")
    void abortOnLastIteration() {
        var counter = new AtomicInteger();

        var action = Loop.loop("loop")
                .body(Step.step("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count == 5) {
                        throw AbortException.abort("abort");
                    }
                    throw FailException.fail("fail");
                }))
                .maxIterations(5)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isAborted()).isTrue();
        assertThat(counter.get()).isEqualTo(5);
    }

    @Test
    @DisplayName("execution count matches expected")
    void executionCountMatchesExpected() {
        var counter = new AtomicInteger();

        var action = Loop.loop("loop")
                .body(Step.step("step", context -> counter.incrementAndGet()))
                .until(context -> false)
                .maxIterations(7)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(counter.get()).isEqualTo(7);
    }
}

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
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Loop execution")
class LoopExecutionTest {

    @Test
    @DisplayName("skipException in body continues loop (no predicate)")
    void skipExceptionInBodyContinuesLoopNoPredicate() {
        var counter = new AtomicInteger();

        var action = Loop.builder("loop")
                .body(Step.of("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count == 1) {
                        SkipException.skip("skip");
                    }
                }))
                .maxIterations(3)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isSkipped()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("runtimeException in body continues loop (no predicate)")
    void runtimeExceptionInBodyContinuesLoopNoPredicate() {
        var counter = new AtomicInteger();

        var action = Loop.builder("loop")
                .body(Step.of("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count == 1) {
                        throw new RuntimeException("error");
                    }
                }))
                .maxIterations(3)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("predicate throws continues loop")
    void predicateThrowsContinuesLoop() {
        var counter = new AtomicInteger();

        var action = Loop.builder("loop")
                .body(Step.of("step", context -> counter.incrementAndGet()))
                .until(context -> {
                    throw new RuntimeException("error");
                })
                .maxIterations(3)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("abort on first iteration")
    void abortOnFirstIteration() {
        var counter = new AtomicInteger();

        var action = Loop.builder("loop")
                .body(Step.of("step", context -> {
                    counter.incrementAndGet();
                    AbortedException.abort("abort");
                }))
                .maxIterations(5)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isAborted()).isTrue();
        assertThat(counter.get()).isEqualTo(1);
        assertThat(root.children().get(0).isAborted()).isTrue();
        assertThat(root.children().get(1).isSkipped()).isTrue();
        assertThat(root.children().get(2).isSkipped()).isTrue();
        assertThat(root.children().get(3).isSkipped()).isTrue();
        assertThat(root.children().get(4).isSkipped()).isTrue();
    }

    @Test
    @DisplayName("abort on last iteration")
    void abortOnLastIteration() {
        var counter = new AtomicInteger();

        var action = Loop.builder("loop")
                .body(Step.of("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count == 5) {
                        AbortedException.abort("abort");
                    }
                    FailException.fail("fail");
                }))
                .maxIterations(5)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isAborted()).isTrue();
        assertThat(counter.get()).isEqualTo(5);
    }

    @Test
    @DisplayName("execution count matches expected")
    void executionCountMatchesExpected() {
        var counter = new AtomicInteger();

        var action = Loop.builder("loop")
                .body(Step.of("step", context -> counter.incrementAndGet()))
                .until(context -> false)
                .maxIterations(7)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(counter.get()).isEqualTo(7);
    }
}

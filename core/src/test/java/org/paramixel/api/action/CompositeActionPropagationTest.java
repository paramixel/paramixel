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
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Composite action propagation")
class CompositeActionPropagationTest {

    @Test
    @DisplayName("scope: before failure skips body, runs after, and aggregates FAILED")
    void scopeBeforeFailurePropagatesSkipAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.builder("scope")
                .before(Step.of("before", ignored -> FailException.fail("before failed")))
                .body(Sequence.builder("body")
                        .child(Step.of("body-1", ignored -> bodyCalls.incrementAndGet()))
                        .child(Step.of("body-2", ignored -> bodyCalls.incrementAndGet()))
                        .build())
                .after(Step.of("after", ignored -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().isFailed()).isTrue();
        assertThat(body.children().get(0).isSkipped()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("static: aborted body propagates ABORT to remaining body and keeps after RUN")
    void staticAbortedBodyPropagatesAbortAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .body(Sequence.builder("body")
                        .child(Step.of("body-1", context -> {
                            bodyCalls.incrementAndGet();
                            AbortedException.abort("aborted");
                        }))
                        .child(Step.of("body-2", context -> bodyCalls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isAborted()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isAborted()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(bodyCalls.get()).isEqualTo(2);
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("instance: skipped body skips remaining body, runs destroy, and aggregates SKIPPED")
    void instanceSkippedBodyPropagatesSkipAndRunsDestroy() {
        var secondBodyCalls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Sequence.builder("body")
                        .child(Step.of("body-1", context -> SkipException.skip("skipped")))
                        .child(Step.of("body-2", context -> secondBodyCalls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isSkipped()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(body.children().get(0).isSkipped()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(secondBodyCalls.get()).isZero();
        assertThat(closed.get()).isEqualTo(1);
    }

    private record CloseableFixture(AtomicInteger closed) implements AutoCloseable {

        @Override
        public void close() {
            closed.incrementAndGet();
        }
    }

    @Test
    @DisplayName("parallel with 0 children completes immediately with PASSED")
    void parallelEmptyChildrenPasses() {
        var action = Parallel.builder("empty").build();
        var root = Runner.builder().build().run(action).descriptor().orElseThrow();
        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).isEmpty();
    }

    @Test
    @DisplayName("sequential with 0 children completes immediately with PASSED")
    void sequentialEmptyChildrenPasses() {
        var action = Sequence.builder("empty").build();
        var root = Runner.builder().build().run(action).descriptor().orElseThrow();
        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).isEmpty();
    }

    @Test
    @DisplayName("scope with no body throws IllegalStateException")
    void scopeEmptyBodyThrows() {
        assertThatThrownBy(() -> Scope.builder("empty").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("body action must be configured");
    }

    @Test
    @DisplayName("static with no body throws IllegalStateException")
    void staticEmptyBodyThrows() {
        assertThatThrownBy(() -> Static.builder("empty").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("body action must be configured");
    }

    @Test
    @DisplayName("instance with no body throws IllegalStateException")
    void instanceEmptyBodyThrows() {
        assertThatThrownBy(() -> Instance.builder("empty", Object::new).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("body action must be configured");
    }
}

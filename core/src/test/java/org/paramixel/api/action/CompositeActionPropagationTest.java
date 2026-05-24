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
import org.paramixel.api.Status;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Composite action propagation")
class CompositeActionPropagationTest {

    @Test
    @DisplayName("lifecycle: before failure skips body, runs after, and aggregates FAILED")
    void lifecycleBeforeFailurePropagatesSkipAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Lifecycle.of("lifecycle")
                .before("before", ignored -> FailException.fail("before failed"))
                .child("body-1", ignored -> bodyCalls.incrementAndGet())
                .child("body-2", ignored -> bodyCalls.incrementAndGet())
                .after("after", ignored -> afterCalls.incrementAndGet())
                .resolve();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();
        var children = root.children();

        assertThat(root.metadata().status().isFailed()).isTrue();
        assertThat(children).hasSize(4);
        assertThat(children.get(0).metadata().status().isFailed()).isTrue();
        assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
        assertThat(children.get(2).metadata().status()).isSameAs(Status.SKIPPED);
        assertThat(children.get(3).metadata().status()).isSameAs(Status.PASSED);
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("static: aborted body propagates ABORT to remaining body and keeps after RUN")
    void staticAbortedBodyPropagatesAbortAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.of("static")
                .child("body-1", () -> {
                    bodyCalls.incrementAndGet();
                    AbortedException.abort("aborted");
                })
                .child("body-2", () -> bodyCalls.incrementAndGet())
                .after("after", () -> afterCalls.incrementAndGet())
                .resolve();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();
        var children = root.children();

        assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
        assertThat(children).hasSize(3);
        assertThat(children.get(0).metadata().status().isAborted()).isTrue();
        assertThat(children.get(1).metadata().status()).isSameAs(Status.ABORTED);
        assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
        assertThat(bodyCalls.get()).isEqualTo(1);
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("instance: skipped body skips remaining body, runs destroy, and aggregates SKIPPED")
    void instanceSkippedBodyPropagatesSkipAndRunsDestroy() {
        var secondBodyCalls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.of("instance", () -> new CloseableFixture(closed))
                .child("body-1", fixture -> SkipException.skip("skipped"))
                .child("body-2", fixture -> secondBodyCalls.incrementAndGet())
                .resolve();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();
        var children = root.children();

        assertThat(root.metadata().status()).isSameAs(Status.SKIPPED);
        assertThat(children).hasSize(4);
        assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
        assertThat(children.get(1).metadata().status().isSkipped()).isTrue();
        assertThat(children.get(2).metadata().status()).isSameAs(Status.SKIPPED);
        assertThat(children.get(3).metadata().status()).isSameAs(Status.PASSED);
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
        var action = Parallel.of("empty").resolve();
        var root = Runner.builder().build().run(action).descriptor().orElseThrow();
        assertThat(root.metadata().status()).isSameAs(Status.PASSED);
        assertThat(root.children()).isEmpty();
    }

    @Test
    @DisplayName("sequential with 0 children completes immediately with PASSED")
    void sequentialEmptyChildrenPasses() {
        var action = Sequential.of("empty").resolve();
        var root = Runner.builder().build().run(action).descriptor().orElseThrow();
        assertThat(root.metadata().status()).isSameAs(Status.PASSED);
        assertThat(root.children()).isEmpty();
    }

    @Test
    @DisplayName("lifecycle with no before, children, or after completes immediately with PASSED")
    void lifecycleEmptyBodyPasses() {
        var action = Lifecycle.of("empty").resolve();
        var root = Runner.builder().build().run(action).descriptor().orElseThrow();
        assertThat(root.metadata().status()).isSameAs(Status.PASSED);
        assertThat(root.children()).isEmpty();
    }

    @Test
    @DisplayName("static with no before, children, or after completes immediately with PASSED")
    void staticEmptyBodyPasses() {
        var action = Static.of("empty").resolve();
        var root = Runner.builder().build().run(action).descriptor().orElseThrow();
        assertThat(root.metadata().status()).isSameAs(Status.PASSED);
        assertThat(root.children()).isEmpty();
    }

    @Test
    @DisplayName("instance with 0 body children passes with Instantiate and Destroy descriptors")
    void instanceEmptyBodyPasses() {
        var action = Instance.of("empty", Object::new).resolve();
        var root = Runner.builder().build().run(action).descriptor().orElseThrow();
        assertThat(root.metadata().status()).isSameAs(Status.PASSED);
        assertThat(root.children()).hasSize(2);
        assertThat(root.children().get(0).metadata().name()).isEqualTo("Instantiate");
        assertThat(root.children().get(1).metadata().name()).isEqualTo("Destroy");
    }
}

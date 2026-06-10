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

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Sequential execution")
class SequentialExecutionTest {

    @Nested
    @DisplayName("dependent mode")
    class Dependent {

        @Test
        @DisplayName("all pass")
        void allPass() {
            var action = Sequential.builder("sequential")
                    .child(Step.of("child-1", ignored -> {}))
                    .child(Step.of("child-2", ignored -> {}))
                    .child(Step.of("child-3", ignored -> {}))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isPassed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isPassed()).isTrue();
            assertThat(children.get(1).isPassed()).isTrue();
            assertThat(children.get(2).isPassed()).isTrue();
        }

        @Test
        @DisplayName("first child fails skips remaining")
        void firstChildFailsSkipsRemaining() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.builder("sequential")
                    .child(Step.of("child-1", ignored -> FailException.fail("child failed")))
                    .child(Step.of("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.of("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isFailed()).isTrue();
            assertThat(children.get(1).isSkipped()).isTrue();
            assertThat(children.get(2).isSkipped()).isTrue();
            assertThat(child2Calls.get()).isZero();
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("first child aborts aborts remaining")
        void firstChildAbortsAbortsRemaining() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.builder("sequential")
                    .child(Step.of("child-1", ignored -> AbortedException.abort("child aborted")))
                    .child(Step.of("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.of("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isAborted()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isAborted()).isTrue();
            assertThat(children.get(1).isPassed()).isTrue();
            assertThat(children.get(2).isPassed()).isTrue();
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child skips skips remaining")
        void firstChildSkipsSkipsRemaining() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.builder("sequential")
                    .child(Step.of("child-1", ignored -> SkipException.skip("child skipped")))
                    .child(Step.of("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.of("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isSkipped()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isSkipped()).isTrue();
            assertThat(children.get(1).isSkipped()).isTrue();
            assertThat(children.get(2).isSkipped()).isTrue();
            assertThat(child2Calls.get()).isZero();
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("middle child fails skips remaining")
        void middleChildFailsSkipsRemaining() {
            var child3Calls = new AtomicInteger();

            var action = Sequential.builder("sequential")
                    .child(Step.of("child-1", ignored -> {}))
                    .child(Step.of("child-2", ignored -> FailException.fail("child failed")))
                    .child(Step.of("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isPassed()).isTrue();
            assertThat(children.get(1).isFailed()).isTrue();
            assertThat(children.get(2).isSkipped()).isTrue();
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("last child fails")
        void lastChildFails() {
            var action = Sequential.builder("sequential")
                    .child(Step.of("child-1", ignored -> {}))
                    .child(Step.of("child-2", ignored -> {}))
                    .child(Step.of("child-3", ignored -> FailException.fail("child failed")))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isPassed()).isTrue();
            assertThat(children.get(1).isPassed()).isTrue();
            assertThat(children.get(2).isFailed()).isTrue();
        }
    }

    @Nested
    @DisplayName("independent mode")
    class Independent {

        @Test
        @DisplayName("all children run despite failure")
        void allChildrenRunDespiteFailure() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.builder("sequential")
                    .independent()
                    .child(Step.of("child-1", ignored -> FailException.fail("child failed")))
                    .child(Step.of("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.of("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isFailed()).isTrue();
            assertThat(children.get(1).isPassed()).isTrue();
            assertThat(children.get(2).isPassed()).isTrue();
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("multiple failures reported")
        void multipleFailuresReported() {
            var action = Sequential.builder("sequential")
                    .independent()
                    .child(Step.of("child-1", ignored -> FailException.fail("child-1 failed")))
                    .child(Step.of("child-2", ignored -> {}))
                    .child(Step.of("child-3", ignored -> FailException.fail("child-3 failed")))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isFailed()).isTrue();
            assertThat(children.get(1).isPassed()).isTrue();
            assertThat(children.get(2).isFailed()).isTrue();
        }

        @Test
        @DisplayName("all children run despite abort")
        void allChildrenRunDespiteAbort() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.builder("sequential")
                    .independent()
                    .child(Step.of("child-1", ignored -> AbortedException.abort("child aborted")))
                    .child(Step.of("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.of("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isAborted()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isAborted()).isTrue();
            assertThat(children.get(1).isPassed()).isTrue();
            assertThat(children.get(2).isPassed()).isTrue();
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("all children run despite skip")
        void allChildrenRunDespiteSkip() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.builder("sequential")
                    .independent()
                    .child(Step.of("child-1", ignored -> SkipException.skip("child skipped")))
                    .child(Step.of("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.of("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isSkipped()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isSkipped()).isTrue();
            assertThat(children.get(1).isPassed()).isTrue();
            assertThat(children.get(2).isPassed()).isTrue();
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("second child fails all children still run")
        void secondChildFailsAllChildrenStillRun() {
            var child3Calls = new AtomicInteger();

            var action = Sequential.builder("sequential")
                    .independent()
                    .child(Step.of("child-1", ignored -> {}))
                    .child(Step.of("child-2", ignored -> FailException.fail("child failed")))
                    .child(Step.of("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isPassed()).isTrue();
            assertThat(children.get(1).isFailed()).isTrue();
            assertThat(children.get(2).isPassed()).isTrue();
            assertThat(child3Calls.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("shuffled sequence")
    class Shuffled {

        @Test
        @DisplayName("shuffled children execute")
        void shuffledChildrenExecute() {
            var executionOrder = new ArrayList<String>();
            var action = Sequential.builder("seq")
                    .shuffle(789L)
                    .child(Step.of("a", ctx -> executionOrder.add("a")))
                    .child(Step.of("b", ctx -> executionOrder.add("b")))
                    .child(Step.of("c", ctx -> executionOrder.add("c")))
                    .build();
            Runner.builder().build().run(action);
            assertThat(executionOrder).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        @DisplayName("shuffled order is reproducible")
        void shuffledOrderIsReproducible() {
            var executionOrder = new ArrayList<String>();
            var action = Sequential.builder("seq")
                    .shuffle(789L)
                    .child(Step.of("a", ctx -> executionOrder.add("a")))
                    .child(Step.of("b", ctx -> executionOrder.add("b")))
                    .child(Step.of("c", ctx -> executionOrder.add("c")))
                    .build();
            Runner.builder().build().run(action);
            var executionOrder2 = new ArrayList<String>();
            var action2 = Sequential.builder("seq")
                    .shuffle(789L)
                    .child(Step.of("a", ctx -> executionOrder2.add("a")))
                    .child(Step.of("b", ctx -> executionOrder2.add("b")))
                    .child(Step.of("c", ctx -> executionOrder2.add("c")))
                    .build();
            Runner.builder().build().run(action2);
            assertThat(executionOrder2).isEqualTo(executionOrder);
        }

        @Test
        @DisplayName("shuffled dependent skips after first failure")
        void shuffledDependentSkipsAfterFirstFailure() {
            var action = Sequential.builder("seq")
                    .shuffle(42L)
                    .child(Step.of("child-1", ignored -> {
                        throw new RuntimeException("child-1 failed");
                    }))
                    .child(Step.of("child-2", ignored -> {}))
                    .child(Step.of("child-3", ignored -> {}))
                    .build();
            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            assertThat(root.isFailed()).isTrue();
        }

        @Test
        @DisplayName("shuffled independent runs all despite failure")
        void shuffledIndependentRunsAllDespiteFailure() {
            var childCallCount = new AtomicInteger();
            var action = Sequential.builder("seq")
                    .independent()
                    .shuffle(42L)
                    .child(Step.of("child-1", ignored -> {
                        throw new RuntimeException("child-1 failed");
                    }))
                    .child(Step.of("child-2", ignored -> childCallCount.incrementAndGet()))
                    .child(Step.of("child-3", ignored -> {}))
                    .build();
            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            assertThat(root.isFailed()).isTrue();
            assertThat(childCallCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("single child shuffled no-op")
        void singleChildShuffledNoOp() {
            var executionOrder = new ArrayList<String>();
            var action = Sequential.builder("seq")
                    .shuffle()
                    .child(Step.of("a", ctx -> executionOrder.add("a")))
                    .build();
            Runner.builder().build().run(action);
            assertThat(executionOrder).containsExactly("a");
        }
    }
}

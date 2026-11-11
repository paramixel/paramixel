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
import org.paramixel.api.exception.AbortException;
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
            var action = Sequential.sequential("sequential")
                    .child(Step.step("child-1", ignored -> {}))
                    .child(Step.step("child-2", ignored -> {}))
                    .child(Step.step("child-3", ignored -> {}))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isPassed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).status().isPassed()).isTrue();
            assertThat(children.get(1).status().isPassed()).isTrue();
            assertThat(children.get(2).status().isPassed()).isTrue();
        }

        @Test
        @DisplayName("first child fails skips remaining")
        void firstChildFailsSkipsRemaining() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.sequential("sequential")
                    .child(Step.step("child-1", ignored -> {
                        throw FailException.fail("child failed");
                    }))
                    .child(Step.step("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.step("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).status().isFailed()).isTrue();
            assertThat(children.get(1).status().isSkipped()).isTrue();
            assertThat(children.get(2).status().isSkipped()).isTrue();
            assertThat(child2Calls.get()).isZero();
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("first child aborts aborts remaining")
        void firstChildAbortsAbortsRemaining() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.sequential("sequential")
                    .child(Step.step("child-1", ignored -> {
                        throw AbortException.abort("child aborted");
                    }))
                    .child(Step.step("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.step("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isAborted()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).status().isAborted()).isTrue();
            assertThat(children.get(1).status().isPassed()).isTrue();
            assertThat(children.get(2).status().isPassed()).isTrue();
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child skips skips remaining")
        void firstChildSkipsSkipsRemaining() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.sequential("sequential")
                    .child(Step.step("child-1", ignored -> {
                        throw SkipException.skip("child skipped");
                    }))
                    .child(Step.step("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.step("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isSkipped()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).status().isSkipped()).isTrue();
            assertThat(children.get(1).status().isSkipped()).isTrue();
            assertThat(children.get(2).status().isSkipped()).isTrue();
            assertThat(child2Calls.get()).isZero();
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("middle child fails skips remaining")
        void middleChildFailsSkipsRemaining() {
            var child3Calls = new AtomicInteger();

            var action = Sequential.sequential("sequential")
                    .child(Step.step("child-1", ignored -> {}))
                    .child(Step.step("child-2", ignored -> {
                        throw FailException.fail("child failed");
                    }))
                    .child(Step.step("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).status().isPassed()).isTrue();
            assertThat(children.get(1).status().isFailed()).isTrue();
            assertThat(children.get(2).status().isSkipped()).isTrue();
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("last child fails")
        void lastChildFails() {
            var action = Sequential.sequential("sequential")
                    .child(Step.step("child-1", ignored -> {}))
                    .child(Step.step("child-2", ignored -> {}))
                    .child(Step.step("child-3", ignored -> {
                        throw FailException.fail("child failed");
                    }))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).status().isPassed()).isTrue();
            assertThat(children.get(1).status().isPassed()).isTrue();
            assertThat(children.get(2).status().isFailed()).isTrue();
        }

        @Test
        @DisplayName("first child runtime exception skips remaining")
        void firstChildRuntimeExceptionSkipsRemaining() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.sequential("sequential")
                    .child(Step.step("child-1", ignored -> {
                        throw new RuntimeException("runtime error");
                    }))
                    .child(Step.step("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.step("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).status().isFailed()).isTrue();
            assertThat(children.get(1).status().isSkipped()).isTrue();
            assertThat(children.get(2).status().isSkipped()).isTrue();
            assertThat(child2Calls.get()).isZero();
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("empty children passes")
        void emptyChildrenPasses() {
            var action = Sequential.sequential("sequential").build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();

            assertThat(root.status().isPassed()).isTrue();
            assertThat(root.children()).isEmpty();
        }

        @Test
        @DisplayName("single child passes")
        void singleChildPasses() {
            var action = Sequential.sequential("sequential")
                    .child(Step.step("child-1", ignored -> {}))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isPassed()).isTrue();
            assertThat(children).hasSize(1);
            assertThat(children.get(0).status().isPassed()).isTrue();
        }

        @Test
        @DisplayName("single child fails")
        void singleChildFails() {
            var action = Sequential.sequential("sequential")
                    .child(Step.step("child-1", ignored -> {
                        throw FailException.fail("child failed");
                    }))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isFailed()).isTrue();
            assertThat(children).hasSize(1);
            assertThat(children.get(0).status().isFailed()).isTrue();
        }

        @Test
        @DisplayName("single child aborts")
        void singleChildAborts() {
            var action = Sequential.sequential("sequential")
                    .child(Step.step("child-1", ignored -> {
                        throw AbortException.abort("child aborted");
                    }))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isAborted()).isTrue();
            assertThat(children).hasSize(1);
            assertThat(children.get(0).status().isAborted()).isTrue();
        }

        @Test
        @DisplayName("single child skips")
        void singleChildSkips() {
            var action = Sequential.sequential("sequential")
                    .child(Step.step("child-1", ignored -> {
                        throw SkipException.skip("child skipped");
                    }))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isSkipped()).isTrue();
            assertThat(children).hasSize(1);
            assertThat(children.get(0).status().isSkipped()).isTrue();
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

            var action = Sequential.sequential("sequential")
                    .independent()
                    .child(Step.step("child-1", ignored -> {
                        throw FailException.fail("child failed");
                    }))
                    .child(Step.step("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.step("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).status().isFailed()).isTrue();
            assertThat(children.get(1).status().isPassed()).isTrue();
            assertThat(children.get(2).status().isPassed()).isTrue();
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("multiple failures reported")
        void multipleFailuresReported() {
            var action = Sequential.sequential("sequential")
                    .independent()
                    .child(Step.step("child-1", ignored -> {
                        throw FailException.fail("child-1 failed");
                    }))
                    .child(Step.step("child-2", ignored -> {}))
                    .child(Step.step("child-3", ignored -> {
                        throw FailException.fail("child-3 failed");
                    }))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).status().isFailed()).isTrue();
            assertThat(children.get(1).status().isPassed()).isTrue();
            assertThat(children.get(2).status().isFailed()).isTrue();
        }

        @Test
        @DisplayName("all children run despite abort")
        void allChildrenRunDespiteAbort() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.sequential("sequential")
                    .independent()
                    .child(Step.step("child-1", ignored -> {
                        throw AbortException.abort("child aborted");
                    }))
                    .child(Step.step("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.step("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isAborted()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).status().isAborted()).isTrue();
            assertThat(children.get(1).status().isPassed()).isTrue();
            assertThat(children.get(2).status().isPassed()).isTrue();
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("all children run despite skip")
        void allChildrenRunDespiteSkip() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.sequential("sequential")
                    .independent()
                    .child(Step.step("child-1", ignored -> {
                        throw SkipException.skip("child skipped");
                    }))
                    .child(Step.step("child-2", ignored -> child2Calls.incrementAndGet()))
                    .child(Step.step("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isSkipped()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).status().isSkipped()).isTrue();
            assertThat(children.get(1).status().isPassed()).isTrue();
            assertThat(children.get(2).status().isPassed()).isTrue();
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("second child fails all children still run")
        void secondChildFailsAllChildrenStillRun() {
            var child3Calls = new AtomicInteger();

            var action = Sequential.sequential("sequential")
                    .independent()
                    .child(Step.step("child-1", ignored -> {}))
                    .child(Step.step("child-2", ignored -> {
                        throw FailException.fail("child failed");
                    }))
                    .child(Step.step("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.status().isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).status().isPassed()).isTrue();
            assertThat(children.get(1).status().isFailed()).isTrue();
            assertThat(children.get(2).status().isPassed()).isTrue();
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
            var action = Sequential.sequential("seq")
                    .shuffle(789L)
                    .child(Step.step("a", ctx -> executionOrder.add("a")))
                    .child(Step.step("b", ctx -> executionOrder.add("b")))
                    .child(Step.step("c", ctx -> executionOrder.add("c")))
                    .build();
            Runner.builder().build().run(action);
            assertThat(executionOrder).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        @DisplayName("shuffled order is reproducible")
        void shuffledOrderIsReproducible() {
            var executionOrder = new ArrayList<String>();
            var action = Sequential.sequential("seq")
                    .shuffle(789L)
                    .child(Step.step("a", ctx -> executionOrder.add("a")))
                    .child(Step.step("b", ctx -> executionOrder.add("b")))
                    .child(Step.step("c", ctx -> executionOrder.add("c")))
                    .build();
            Runner.builder().build().run(action);
            var executionOrder2 = new ArrayList<String>();
            var action2 = Sequential.sequential("seq")
                    .shuffle(789L)
                    .child(Step.step("a", ctx -> executionOrder2.add("a")))
                    .child(Step.step("b", ctx -> executionOrder2.add("b")))
                    .child(Step.step("c", ctx -> executionOrder2.add("c")))
                    .build();
            Runner.builder().build().run(action2);
            assertThat(executionOrder2).isEqualTo(executionOrder);
        }

        @Test
        @DisplayName("shuffled dependent skips after first failure")
        void shuffledDependentSkipsAfterFirstFailure() {
            var action = Sequential.sequential("seq")
                    .shuffle(42L)
                    .child(Step.step("child-1", ignored -> {
                        throw new RuntimeException("child-1 failed");
                    }))
                    .child(Step.step("child-2", ignored -> {}))
                    .child(Step.step("child-3", ignored -> {}))
                    .build();
            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            assertThat(root.status().isFailed()).isTrue();
        }

        @Test
        @DisplayName("shuffled independent runs all despite failure")
        void shuffledIndependentRunsAllDespiteFailure() {
            var childCallCount = new AtomicInteger();
            var action = Sequential.sequential("seq")
                    .independent()
                    .shuffle(42L)
                    .child(Step.step("child-1", ignored -> {
                        throw new RuntimeException("child-1 failed");
                    }))
                    .child(Step.step("child-2", ignored -> childCallCount.incrementAndGet()))
                    .child(Step.step("child-3", ignored -> {}))
                    .build();
            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            assertThat(root.status().isFailed()).isTrue();
            assertThat(childCallCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("single child shuffled no-op")
        void singleChildShuffledNoOp() {
            var executionOrder = new ArrayList<String>();
            var action = Sequential.sequential("seq")
                    .shuffle()
                    .child(Step.step("a", ctx -> executionOrder.add("a")))
                    .build();
            Runner.builder().build().run(action);
            assertThat(executionOrder).containsExactly("a");
        }
    }
}

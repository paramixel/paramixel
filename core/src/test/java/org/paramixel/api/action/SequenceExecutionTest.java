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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Sequence execution")
class SequenceExecutionTest {

    @Nested
    @DisplayName("dependent mode")
    class Dependent {

        @Test
        @DisplayName("all pass")
        void allPass() {
            var action = Sequence.builder("sequential")
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

            var action = Sequence.builder("sequential")
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

            var action = Sequence.builder("sequential")
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

            var action = Sequence.builder("sequential")
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
        @DisplayName("second child fails skips remaining")
        void secondChildFailsSkipsRemaining() {
            var child3Calls = new AtomicInteger();

            var action = Sequence.builder("sequential")
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
        @DisplayName("second child aborts aborts remaining")
        void secondChildAbortsAbortsRemaining() {
            var child3Calls = new AtomicInteger();

            var action = Sequence.builder("sequential")
                    .child(Step.of("child-1", ignored -> {}))
                    .child(Step.of("child-2", ignored -> AbortedException.abort("child aborted")))
                    .child(Step.of("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isAborted()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isPassed()).isTrue();
            assertThat(children.get(1).isAborted()).isTrue();
            assertThat(children.get(2).isPassed()).isTrue();
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("second child skips skips remaining")
        void secondChildSkipsSkipsRemaining() {
            var child3Calls = new AtomicInteger();

            var action = Sequence.builder("sequential")
                    .child(Step.of("child-1", ignored -> {}))
                    .child(Step.of("child-2", ignored -> SkipException.skip("child skipped")))
                    .child(Step.of("child-3", ignored -> child3Calls.incrementAndGet()))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isSkipped()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).isPassed()).isTrue();
            assertThat(children.get(1).isSkipped()).isTrue();
            assertThat(children.get(2).isSkipped()).isTrue();
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("first child runtime exception skips remaining")
        void firstChildRuntimeExceptionSkipsRemaining() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequence.builder("sequential")
                    .child(Step.of("child-1", ignored -> {
                        throw new RuntimeException("runtime error");
                    }))
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
        @DisplayName("empty children passes")
        void emptyChildrenPasses() {
            var action = Sequence.builder("sequential").build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();

            assertThat(root.isPassed()).isTrue();
            assertThat(root.children()).isEmpty();
        }

        @Test
        @DisplayName("single child passes")
        void singleChildPasses() {
            var action = Sequence.builder("sequential")
                    .child(Step.of("child-1", ignored -> {}))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isPassed()).isTrue();
            assertThat(children).hasSize(1);
            assertThat(children.get(0).isPassed()).isTrue();
        }

        @Test
        @DisplayName("single child fails")
        void singleChildFails() {
            var action = Sequence.builder("sequential")
                    .child(Step.of("child-1", ignored -> FailException.fail("child failed")))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isFailed()).isTrue();
            assertThat(children).hasSize(1);
            assertThat(children.get(0).isFailed()).isTrue();
        }

        @Test
        @DisplayName("single child aborts")
        void singleChildAborts() {
            var action = Sequence.builder("sequential")
                    .child(Step.of("child-1", ignored -> AbortedException.abort("child aborted")))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isAborted()).isTrue();
            assertThat(children).hasSize(1);
            assertThat(children.get(0).isAborted()).isTrue();
        }

        @Test
        @DisplayName("single child skips")
        void singleChildSkips() {
            var action = Sequence.builder("sequential")
                    .child(Step.of("child-1", ignored -> SkipException.skip("child skipped")))
                    .build();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.isSkipped()).isTrue();
            assertThat(children).hasSize(1);
            assertThat(children.get(0).isSkipped()).isTrue();
        }
    }

    @Nested
    @DisplayName("independent mode")
    class Independent {

        @Test
        @DisplayName("all pass")
        void allPass() {
            var action = Sequence.builder("sequential")
                    .independent()
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
        @DisplayName("first child fails all children still run")
        void firstChildFailsAllChildrenStillRun() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequence.builder("sequential")
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
        @DisplayName("first child aborts all children still run")
        void firstChildAbortsAllChildrenStillRun() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequence.builder("sequential")
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
        @DisplayName("first child skips all children still run")
        void firstChildSkipsAllChildrenStillRun() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequence.builder("sequential")
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

            var action = Sequence.builder("sequential")
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
}

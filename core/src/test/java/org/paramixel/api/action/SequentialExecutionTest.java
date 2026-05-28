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
import org.paramixel.api.Status;
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
            var action = Sequential.of("sequential")
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> {})
                    .child("child-3", ignored -> {})
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("first child fails skips remaining")
        void firstChildFailsSkipsRemaining() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.of("sequential")
                    .child("child-1", ignored -> FailException.fail("child failed"))
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .child("child-3", ignored -> child3Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status().isFailed()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children.get(2).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(child2Calls.get()).isZero();
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("first child aborts aborts remaining")
        void firstChildAbortsAbortsRemaining() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.of("sequential")
                    .child("child-1", ignored -> AbortedException.abort("child aborted"))
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .child("child-3", ignored -> child3Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status().isAborted()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child skips skips remaining")
        void firstChildSkipsSkipsRemaining() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.of("sequential")
                    .child("child-1", ignored -> SkipException.skip("child skipped"))
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .child("child-3", ignored -> child3Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status().isSkipped()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children.get(2).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(child2Calls.get()).isZero();
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("second child fails skips remaining")
        void secondChildFailsSkipsRemaining() {
            var child3Calls = new AtomicInteger();

            var action = Sequential.of("sequential")
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> FailException.fail("child failed"))
                    .child("child-3", ignored -> child3Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status().isFailed()).isTrue();
            assertThat(children.get(2).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("second child aborts aborts remaining")
        void secondChildAbortsAbortsRemaining() {
            var child3Calls = new AtomicInteger();

            var action = Sequential.of("sequential")
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> AbortedException.abort("child aborted"))
                    .child("child-3", ignored -> child3Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status().isAborted()).isTrue();
            assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("second child skips skips remaining")
        void secondChildSkipsSkipsRemaining() {
            var child3Calls = new AtomicInteger();

            var action = Sequential.of("sequential")
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> SkipException.skip("child skipped"))
                    .child("child-3", ignored -> child3Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status().isSkipped()).isTrue();
            assertThat(children.get(2).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("first child runtime exception skips remaining")
        void firstChildRuntimeExceptionSkipsRemaining() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.of("sequential")
                    .child("child-1", ignored -> {
                        throw new RuntimeException("runtime error");
                    })
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .child("child-3", ignored -> child3Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status().isFailed()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children.get(2).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(child2Calls.get()).isZero();
            assertThat(child3Calls.get()).isZero();
        }

        @Test
        @DisplayName("empty children passes")
        void emptyChildrenPasses() {
            var action = Sequential.of("sequential").resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.children()).isEmpty();
        }

        @Test
        @DisplayName("single child passes")
        void singleChildPasses() {
            var action =
                    Sequential.of("sequential").child("child-1", ignored -> {}).resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(1);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("single child fails")
        void singleChildFails() {
            var action = Sequential.of("sequential")
                    .child("child-1", ignored -> FailException.fail("child failed"))
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(1);
            assertThat(children.get(0).metadata().status().isFailed()).isTrue();
        }

        @Test
        @DisplayName("single child aborts")
        void singleChildAborts() {
            var action = Sequential.of("sequential")
                    .child("child-1", ignored -> AbortedException.abort("child aborted"))
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
            assertThat(children).hasSize(1);
            assertThat(children.get(0).metadata().status().isAborted()).isTrue();
        }

        @Test
        @DisplayName("single child skips")
        void singleChildSkips() {
            var action = Sequential.of("sequential")
                    .child("child-1", ignored -> SkipException.skip("child skipped"))
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children).hasSize(1);
            assertThat(children.get(0).metadata().status().isSkipped()).isTrue();
        }
    }

    @Nested
    @DisplayName("independent mode")
    class Independent {

        @Test
        @DisplayName("all pass")
        void allPass() {
            var action = Sequential.of("sequential")
                    .independent()
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> {})
                    .child("child-3", ignored -> {})
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("first child fails all children still run")
        void firstChildFailsAllChildrenStillRun() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.of("sequential")
                    .independent()
                    .child("child-1", ignored -> FailException.fail("child failed"))
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .child("child-3", ignored -> child3Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status().isFailed()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child aborts all children still run")
        void firstChildAbortsAllChildrenStillRun() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.of("sequential")
                    .independent()
                    .child("child-1", ignored -> AbortedException.abort("child aborted"))
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .child("child-3", ignored -> child3Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status().isAborted()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child skips all children still run")
        void firstChildSkipsAllChildrenStillRun() {
            var child2Calls = new AtomicInteger();
            var child3Calls = new AtomicInteger();

            var action = Sequential.of("sequential")
                    .independent()
                    .child("child-1", ignored -> SkipException.skip("child skipped"))
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .child("child-3", ignored -> child3Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status().isSkipped()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(child3Calls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("second child fails all children still run")
        void secondChildFailsAllChildrenStillRun() {
            var child3Calls = new AtomicInteger();

            var action = Sequential.of("sequential")
                    .independent()
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> FailException.fail("child failed"))
                    .child("child-3", ignored -> child3Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(3);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status().isFailed()).isTrue();
            assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
            assertThat(child3Calls.get()).isEqualTo(1);
        }
    }
}

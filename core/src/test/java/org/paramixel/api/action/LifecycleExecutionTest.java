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

@DisplayName("Lifecycle execution")
class LifecycleExecutionTest {

    @Nested
    @DisplayName("dependent mode")
    class Dependent {

        @Test
        @DisplayName("all pass")
        void allPass() {
            var action = Lifecycle.of("lifecycle")
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> {})
                    .after("after", ignored -> {})
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("before fails skips body and runs after")
        void beforeFailureSkipsBodyAndRunsAfter() {
            var bodyCalls = new AtomicInteger();
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .before("before", ignored -> FailException.fail("before failed"))
                    .child("child-1", ignored -> bodyCalls.incrementAndGet())
                    .child("child-2", ignored -> bodyCalls.incrementAndGet())
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(before.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(bodyCalls.get()).isZero();
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("before aborts skips body and runs after")
        void beforeAbortsSkipsBodyAndRunsAfter() {
            var bodyCalls = new AtomicInteger();
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .before("before", ignored -> AbortedException.abort("before aborted"))
                    .child("child-1", ignored -> bodyCalls.incrementAndGet())
                    .child("child-2", ignored -> bodyCalls.incrementAndGet())
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
            assertThat(before.metadata().status().isAborted()).isTrue();
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(bodyCalls.get()).isZero();
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("before skips skips body and runs after")
        void beforeSkipsSkipsBodyAndRunsAfter() {
            var bodyCalls = new AtomicInteger();
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .before("before", ignored -> SkipException.skip("before skipped"))
                    .child("child-1", ignored -> bodyCalls.incrementAndGet())
                    .child("child-2", ignored -> bodyCalls.incrementAndGet())
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(before.metadata().status().isSkipped()).isTrue();
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(bodyCalls.get()).isZero();
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child fails skips remaining and runs after")
        void firstChildFailsSkipsRemainingAndRunsAfter() {
            var child2Calls = new AtomicInteger();
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> FailException.fail("child failed"))
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status().isFailed()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isZero();
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child aborts aborts remaining and runs after")
        void firstChildAbortsAbortsRemainingAndRunsAfter() {
            var child2Calls = new AtomicInteger();
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> AbortedException.abort("child aborted"))
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status().isAborted()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child skips skips remaining and runs after")
        void firstChildSkipsSkipsRemainingAndRunsAfter() {
            var child2Calls = new AtomicInteger();
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> SkipException.skip("child skipped"))
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status().isSkipped()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isZero();
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child runtime exception skips remaining and runs after")
        void firstChildRuntimeExceptionSkipsRemainingAndRunsAfter() {
            var child2Calls = new AtomicInteger();
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> {
                        throw new RuntimeException("runtime error");
                    })
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status().isFailed()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isZero();
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("after fails aggregates FAILED")
        void afterFailsAggregatesFailed() {
            var action = Lifecycle.of("lifecycle")
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> {})
                    .after("after", ignored -> FailException.fail("after failed"))
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(after.metadata().status().isFailed()).isTrue();
        }

        @Test
        @DisplayName("no before, no after passes")
        void noBeforeNoAfterPasses() {
            var action = Lifecycle.of("lifecycle")
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> {})
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("no before, after present passes")
        void noBeforeAfterPresentPasses() {
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> {})
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("no after, before present passes")
        void noAfterBeforePresentPasses() {
            var action = Lifecycle.of("lifecycle")
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> {})
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("no before, no after, no children passes")
        void noBeforeNoAfterNoChildrenPasses() {
            var action = Lifecycle.of("lifecycle").resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.children()).isEmpty();
        }

        @Test
        @DisplayName("single child passes")
        void singleChildPasses() {
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> {})
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(1);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("single child fails skips nothing and runs after")
        void singleChildFailsRunsAfter() {
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> FailException.fail("child failed"))
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(1);
            assertThat(children.get(0).metadata().status().isFailed()).isTrue();
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(afterCalls.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("independent mode")
    class Independent {

        @Test
        @DisplayName("all pass")
        void allPass() {
            var action = Lifecycle.of("lifecycle")
                    .independent()
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> {})
                    .after("after", ignored -> {})
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("before fails still skips body and runs after")
        void beforeFailsStillSkipsBodyAndRunsAfter() {
            var bodyCalls = new AtomicInteger();
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .independent()
                    .before("before", ignored -> FailException.fail("before failed"))
                    .child("child-1", ignored -> bodyCalls.incrementAndGet())
                    .child("child-2", ignored -> bodyCalls.incrementAndGet())
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(before.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(bodyCalls.get()).isZero();
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child fails all children still run")
        void firstChildFailsAllChildrenStillRun() {
            var child2Calls = new AtomicInteger();
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .independent()
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> FailException.fail("child failed"))
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status().isFailed()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child aborts all children still run")
        void firstChildAbortsAllChildrenStillRun() {
            var child2Calls = new AtomicInteger();
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .independent()
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> AbortedException.abort("child aborted"))
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status().isAborted()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child skips all children still run")
        void firstChildSkipsAllChildrenStillRun() {
            var child2Calls = new AtomicInteger();
            var afterCalls = new AtomicInteger();

            var action = Lifecycle.of("lifecycle")
                    .independent()
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> SkipException.skip("child skipped"))
                    .child("child-2", ignored -> child2Calls.incrementAndGet())
                    .after("after", ignored -> afterCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status().isSkipped()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(after.metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(afterCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("after fails aggregates FAILED")
        void afterFailsAggregatesFailed() {
            var action = Lifecycle.of("lifecycle")
                    .independent()
                    .before("before", ignored -> {})
                    .child("child-1", ignored -> {})
                    .child("child-2", ignored -> {})
                    .after("after", ignored -> FailException.fail("after failed"))
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var before = root.before().orElseThrow();
            var children = root.children();
            var after = root.after().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(before.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(after.metadata().status().isFailed()).isTrue();
        }
    }
}

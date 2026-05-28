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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Instance execution")
class InstanceExecutionTest {

    record CloseableFixture(AtomicInteger closed) implements AutoCloseable {

        @Override
        public void close() {
            closed.incrementAndGet();
        }
    }

    record FailingFixture() implements AutoCloseable {

        @Override
        public void close() {
            throw new RuntimeException("close failed");
        }
    }

    @Nested
    @DisplayName("dependent mode")
    class Dependent {

        @Test
        @DisplayName("all pass")
        void allPass() {
            var closed = new AtomicInteger();

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .child("child-1", fixture -> {})
                    .child("child-2", fixture -> {})
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.after().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(closed.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("instantiate fails skips body and runs destroy")
        void instantiateFailsSkipsBodyAndRunsDestroy() {
            var childCalls = new AtomicInteger();

            var action = Instance.of("instance", () -> {
                        throw new RuntimeException("instantiate failed");
                    })
                    .child("child-1", fixture -> childCalls.incrementAndGet())
                    .child("child-2", fixture -> childCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status().isFailed())
                    .isTrue();
            assertThat(children.get(0).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(root.after().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(childCalls.get()).isZero();
        }

        @Test
        @DisplayName("instantiate aborts skips body and runs destroy")
        void instantiateAbortsSkipsBodyAndRunsDestroy() {
            var childCalls = new AtomicInteger();

            var action = Instance.of("instance", () -> {
                        AbortedException.abort("instantiate aborted");
                        return new Object();
                    })
                    .child("child-1", fixture -> childCalls.incrementAndGet())
                    .child("child-2", fixture -> childCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status().isAborted())
                    .isTrue();
            assertThat(children.get(0).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(root.after().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(childCalls.get()).isZero();
        }

        @Test
        @DisplayName("first child fails skips remaining and runs destroy")
        void firstChildFailsSkipsRemainingAndRunsDestroy() {
            var child2Calls = new AtomicInteger();
            var closed = new AtomicInteger();

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .child("child-1", fixture -> FailException.fail("child failed"))
                    .child("child-2", fixture -> child2Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(0).metadata().status().isFailed()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(root.after().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isZero();
            assertThat(closed.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child aborts aborts remaining and runs destroy")
        void firstChildAbortsAbortsRemainingAndRunsDestroy() {
            var child2Calls = new AtomicInteger();
            var closed = new AtomicInteger();

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .child("child-1", fixture -> AbortedException.abort("child aborted"))
                    .child("child-2", fixture -> child2Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(0).metadata().status().isAborted()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.after().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(closed.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child skips skips remaining and runs destroy")
        void firstChildSkipsSkipsRemainingAndRunsDestroy() {
            var child2Calls = new AtomicInteger();
            var closed = new AtomicInteger();

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .child("child-1", fixture -> SkipException.skip("child skipped"))
                    .child("child-2", fixture -> child2Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(0).metadata().status().isSkipped()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(root.after().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isZero();
            assertThat(closed.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child runtime exception skips remaining and runs destroy")
        void firstChildRuntimeExceptionSkipsRemainingAndRunsDestroy() {
            var child2Calls = new AtomicInteger();
            var closed = new AtomicInteger();

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .child("child-1", fixture -> {
                        throw new RuntimeException("runtime error");
                    })
                    .child("child-2", fixture -> child2Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(0).metadata().status().isFailed()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(root.after().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isZero();
            assertThat(closed.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("destroy fails via AutoCloseable aggregates FAILED")
        void destroyFailsViaAutoCloseableAggregatesFailed() {
            var action = Instance.of("instance", FailingFixture::new)
                    .child("child-1", fixture -> {})
                    .child("child-2", fixture -> {})
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.after().orElseThrow().metadata().status().isFailed())
                    .isTrue();
        }

        @Test
        @DisplayName("empty body instantiates and destroys only")
        void emptyBodyInstantiatesAndDestroysOnly() {
            var closed = new AtomicInteger();

            var action =
                    Instance.of("instance", () -> new CloseableFixture(closed)).resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(0);
            assertThat(root.before().orElseThrow().metadata().name()).isEqualTo("Instantiate");
            assertThat(root.after().orElseThrow().metadata().name()).isEqualTo("Destroy");
            assertThat(closed.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("AutoCloseable closed exactly once")
        void autoCloseableClosedExactlyOnce() {
            var closed = new AtomicInteger();

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .child("child-1", fixture -> {})
                    .child("child-2", fixture -> {})
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(closed.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("instance available to children")
        void instanceAvailableToChildren() {
            var instanceRef = new AtomicReference<Object>();
            var closed = new AtomicInteger();

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .child("child-1", fixture -> instanceRef.set(fixture))
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(instanceRef.get()).isNotNull();
            assertThat(instanceRef.get()).isInstanceOf(CloseableFixture.class);
        }

        @Test
        @DisplayName("instance holder cleared after destroy")
        void instanceHolderClearedAfterDestroy() {
            var closed = new AtomicInteger();
            var instanceDuringBody = new AtomicReference<Object>();

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .child("child-1", fixture -> instanceDuringBody.set(fixture))
                    .child("child-2", fixture -> {})
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(instanceDuringBody.get()).isNotNull();
            assertThat(closed.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("mixed child types execute in call order")
        void mixedChildTypesExecuteInCallOrder() {
            var closed = new AtomicInteger();
            var executionOrder = new java.util.ArrayList<String>();

            var childSpec = Instance.of("child-c", () -> new CloseableFixture(closed))
                    .child("c-action", fixture -> executionOrder.add("c"));

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .child("a", fixture -> executionOrder.add("a"))
                    .child("b", "kind", fixture -> executionOrder.add("b"))
                    .child(childSpec)
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(executionOrder).containsExactly("a", "b", "c");
        }
    }

    @Nested
    @DisplayName("independent mode")
    class Independent {

        @Test
        @DisplayName("all pass")
        void allPass() {
            var closed = new AtomicInteger();

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .independent()
                    .child("child-1", fixture -> {})
                    .child("child-2", fixture -> {})
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.after().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(closed.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("instantiate fails still skips body and runs destroy")
        void instantiateFailsStillSkipsBodyAndRunsDestroy() {
            var childCalls = new AtomicInteger();

            var action = Instance.of("instance", () -> {
                        throw new RuntimeException("instantiate failed");
                    })
                    .independent()
                    .child("child-1", fixture -> childCalls.incrementAndGet())
                    .child("child-2", fixture -> childCalls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status().isFailed())
                    .isTrue();
            assertThat(children.get(0).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(root.after().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(childCalls.get()).isZero();
        }

        @Test
        @DisplayName("first child fails all children still run")
        void firstChildFailsAllChildrenStillRun() {
            var child2Calls = new AtomicInteger();
            var closed = new AtomicInteger();

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .independent()
                    .child("child-1", fixture -> FailException.fail("child failed"))
                    .child("child-2", fixture -> child2Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(0).metadata().status().isFailed()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.after().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(closed.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child aborts all children still run")
        void firstChildAbortsAllChildrenStillRun() {
            var child2Calls = new AtomicInteger();
            var closed = new AtomicInteger();

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .independent()
                    .child("child-1", fixture -> AbortedException.abort("child aborted"))
                    .child("child-2", fixture -> child2Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(0).metadata().status().isAborted()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.after().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(closed.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("first child skips all children still run")
        void firstChildSkipsAllChildrenStillRun() {
            var child2Calls = new AtomicInteger();
            var closed = new AtomicInteger();

            var action = Instance.of("instance", () -> new CloseableFixture(closed))
                    .independent()
                    .child("child-1", fixture -> SkipException.skip("child skipped"))
                    .child("child-2", fixture -> child2Calls.incrementAndGet())
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(0).metadata().status().isSkipped()).isTrue();
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.after().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(child2Calls.get()).isEqualTo(1);
            assertThat(closed.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("destroy fails via AutoCloseable aggregates FAILED")
        void destroyFailsViaAutoCloseableAggregatesFailed() {
            var action = Instance.of("instance", FailingFixture::new)
                    .independent()
                    .child("child-1", fixture -> {})
                    .child("child-2", fixture -> {})
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children).hasSize(2);
            assertThat(root.before().orElseThrow().metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.after().orElseThrow().metadata().status().isFailed())
                    .isTrue();
        }
    }
}

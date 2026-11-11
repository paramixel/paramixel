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

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.AbortException;
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

    @Test
    @DisplayName("all pass")
    void allPass() {
        var closed = new AtomicInteger();

        var action = Instance.of("instance", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Step.step("child", context -> {}))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("instantiate fails skips body and runs destroy")
    void instantiateFailsSkipsBodyAndRunsDestroy() {
        var childCalls = new AtomicInteger();

        var action = Instance.of("instance", Object.class, () -> {
                    throw new RuntimeException("instantiate failed");
                })
                .body(Step.step("child", context -> childCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(childCalls.get()).isZero();
    }

    @Test
    @DisplayName("null factory result fails instantiate and skips body")
    void nullFactoryResultFailsInstantiateAndSkipsBody() {
        var childCalls = new AtomicInteger();

        var action = Instance.of("instance", Object.class, () -> null)
                .body(Step.step("child", context -> childCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().message()).contains("factory returned null");
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(childCalls.get()).isZero();
    }

    @Test
    @DisplayName("instantiate aborts skips body and runs destroy")
    void instantiateAbortsSkipsBodyAndRunsDestroy() {
        var childCalls = new AtomicInteger();

        var action = Instance.of("instance", Object.class, () -> {
                    throw AbortException.abort("instantiate aborted");
                })
                .body(Step.step("child", context -> childCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isAborted()).isTrue();
        assertThat(root.before().orElseThrow().status().isAborted()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(childCalls.get()).isZero();
    }

    @Test
    @DisplayName("body child fails, remaining skipped via dependent Sequence, destroy runs")
    void bodyBodyFailsRemainingSkippedDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.of("instance", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {
                            throw FailException.fail("child failed");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isFailed()).isTrue();
        assertThat(body.children().get(1).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isZero();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body child aborts, remaining skipped via dependent Sequence, destroy runs")
    void bodyBodyAbortsRemainingSkippedDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.of("instance", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {
                            throw AbortException.abort("child aborted");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isAborted()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isAborted()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body child skips, remaining skipped via dependent Sequence, destroy runs")
    void bodyBodySkipsRemainingSkippedDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.of("instance", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {
                            throw SkipException.skip("child skipped");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isSkipped()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isSkipped()).isTrue();
        assertThat(body.children().get(1).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isZero();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body child runtime exception, remaining skipped via dependent Sequence, destroy runs")
    void bodyBodyRuntimeExceptionRemainingSkippedDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.of("instance", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {
                            throw new RuntimeException("runtime error");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isFailed()).isTrue();
        assertThat(body.children().get(1).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isZero();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body child fails, all still run via independent Sequence, destroy runs")
    void bodyBodyFailsAllStillRunDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.of("instance", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Sequential.sequential("body")
                        .independent()
                        .child(Step.step("child-1", context -> {
                            throw FailException.fail("child failed");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isFailed()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body child aborts, all still run via independent Sequence, destroy runs")
    void bodyBodyAbortsAllStillRunDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.of("instance", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Sequential.sequential("body")
                        .independent()
                        .child(Step.step("child-1", context -> {
                            throw AbortException.abort("child aborted");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isAborted()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isAborted()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body child skips, all still run via independent Sequence, destroy runs")
    void bodyBodySkipsAllStillRunDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.of("instance", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Sequential.sequential("body")
                        .independent()
                        .child(Step.step("child-1", context -> {
                            throw SkipException.skip("child skipped");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isSkipped()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isSkipped()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("destroy fails via AutoCloseable aggregates FAILED")
    void destroyFailsViaAutoCloseableAggregatesFailed() {
        var action = Instance.of("instance", FailingFixture.class, FailingFixture::new)
                .body(Step.step("child", context -> {}))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isFailed()).isTrue();
    }

    @Test
    @DisplayName("empty body throws IllegalStateException")
    void emptyBodyThrowsIllegalStateException() {
        assertThatThrownBy(
                        () -> Instance.of("instance", Object.class, Object::new).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("body action must be configured");
    }

    @Test
    @DisplayName("AutoCloseable closed exactly once")
    void autoCloseableClosedExactlyOnce() {
        var closed = new AtomicInteger();

        var action = Instance.of("instance", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Step.step("child", context -> {}))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("instance available to children")
    void instanceAvailableToChildren() {
        var instanceRef = new AtomicReference<CloseableFixture>();
        var closed = new AtomicInteger();

        var action = Instance.of("instance", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Step.step("child", context -> instanceRef.set(context.requireInstance(CloseableFixture.class))))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(instanceRef.get()).isNotNull();
        assertThat(instanceRef.get()).isInstanceOf(CloseableFixture.class);
    }

    @Test
    @DisplayName("instance holder cleared after destroy")
    void instanceHolderClearedAfterDestroy() {
        var closed = new AtomicInteger();
        var instanceDuringBody = new AtomicReference<CloseableFixture>();

        var action = Instance.of("instance", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Step.step(
                        "child", context -> instanceDuringBody.set(context.requireInstance(CloseableFixture.class))))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(instanceDuringBody.get()).isNotNull();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("mixed child types execute in call order")
    void mixedBodyTypesExecuteInCallOrder() {
        var closed = new AtomicInteger();
        var executionOrder = new ArrayList<String>();

        var childBuilder = Instance.of("child-c", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Step.step("c-action", context -> executionOrder.add("c")));

        var action = Instance.of("instance", CloseableFixture.class, () -> new CloseableFixture(closed))
                .body(Sequential.sequential("body")
                        .child(Step.step("a", context -> executionOrder.add("a")))
                        .child(Step.step("b", context -> executionOrder.add("b")))
                        .child(childBuilder.build()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(executionOrder).containsExactly("a", "b", "c");
    }

    static final class SimpleFixture {

        SimpleFixture() {}
    }

    static final class NoArgConstructorFixture {

        NoArgConstructorFixture(int value) {
            throw new RuntimeException("should not be called");
        }
    }

    @Test
    @DisplayName("builder(Class) uses simple name and default constructor")
    void builderClassUsesSimpleNameAndDefaultConstructor() {
        var action = Instance.of(Object.class)
                .body(Step.step(
                        "child",
                        context -> assertThat(context.instance(Object.class)).isPresent()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.action().displayName()).isEqualTo("Object");
    }

    @Test
    @DisplayName("builder(String, Class) uses provided display name and default constructor")
    void builderStringClassUsesProvidedNameAndDefaultConstructor() {
        var action = Instance.of("my-fixture", Object.class)
                .body(Step.step(
                        "child",
                        context -> assertThat(context.instance(Object.class)).isPresent()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.action().displayName()).isEqualTo("my-fixture");
    }

    @Test
    @DisplayName("builder(String, Class) throws IllegalArgumentException when class has no no-arg constructor")
    void builderStringClassThrowsWhenNoNoArgConstructor() {
        assertThatThrownBy(() -> Instance.of("fixture", NoArgConstructorFixture.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not have a public no-argument constructor");
    }

    @Test
    @DisplayName("builder(String, Class) throws IllegalArgumentException when display name is blank")
    void builderStringClassThrowsWhenDisplayNameBlank() {
        assertThatThrownBy(() -> Instance.of("   ", SimpleFixture.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName is blank");
    }
}

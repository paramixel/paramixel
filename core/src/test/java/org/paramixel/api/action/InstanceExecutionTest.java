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
import static org.paramixel.api.Context.withInstance;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Instance execution")
@SuppressWarnings("removal")
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

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Step.of("child", context -> {}))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("instantiate fails skips body and runs destroy")
    void instantiateFailsSkipsBodyAndRunsDestroy() {
        var childCalls = new AtomicInteger();

        var action = Instance.builder("instance", () -> {
                    throw new RuntimeException("instantiate failed");
                })
                .body(Step.of("child", context -> childCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(childCalls.get()).isZero();
    }

    @Test
    @DisplayName("null factory result fails instantiate and skips body")
    void nullFactoryResultFailsInstantiateAndSkipsBody() {
        var childCalls = new AtomicInteger();

        var action = Instance.builder("instance", () -> null)
                .body(Step.of("child", context -> childCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().message()).contains("factory returned null");
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(childCalls.get()).isZero();
    }

    @Test
    @DisplayName("instantiate aborts skips body and runs destroy")
    void instantiateAbortsSkipsBodyAndRunsDestroy() {
        var childCalls = new AtomicInteger();

        var action = Instance.builder("instance", () -> {
                    AbortedException.abort("instantiate aborted");
                    return new Object();
                })
                .body(Step.of("child", context -> childCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isAborted()).isTrue();
        assertThat(root.before().orElseThrow().isAborted()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(childCalls.get()).isZero();
    }

    @Test
    @DisplayName("body child fails, remaining skipped via dependent Sequence, destroy runs")
    void bodyBodyFailsRemainingSkippedDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> FailException.fail("child failed")))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isFailed()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isZero();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body child aborts, remaining skipped via dependent Sequence, destroy runs")
    void bodyBodyAbortsRemainingSkippedDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> AbortedException.abort("child aborted")))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isAborted()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isAborted()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body child skips, remaining skipped via dependent Sequence, destroy runs")
    void bodyBodySkipsRemainingSkippedDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> SkipException.skip("child skipped")))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isSkipped()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isSkipped()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isZero();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body child runtime exception, remaining skipped via dependent Sequence, destroy runs")
    void bodyBodyRuntimeExceptionRemainingSkippedDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> {
                            throw new RuntimeException("runtime error");
                        }))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isFailed()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isZero();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body child fails, all still run via independent Sequence, destroy runs")
    void bodyBodyFailsAllStillRunDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Sequence.builder("body")
                        .independent()
                        .child(Step.of("child-1", context -> FailException.fail("child failed")))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isFailed()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body child aborts, all still run via independent Sequence, destroy runs")
    void bodyBodyAbortsAllStillRunDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Sequence.builder("body")
                        .independent()
                        .child(Step.of("child-1", context -> AbortedException.abort("child aborted")))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isAborted()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isAborted()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body child skips, all still run via independent Sequence, destroy runs")
    void bodyBodySkipsAllStillRunDestroyRuns() {
        var child2Calls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Sequence.builder("body")
                        .independent()
                        .child(Step.of("child-1", context -> SkipException.skip("child skipped")))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isSkipped()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isSkipped()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("destroy fails via AutoCloseable aggregates FAILED")
    void destroyFailsViaAutoCloseableAggregatesFailed() {
        var action = Instance.builder("instance", FailingFixture::new)
                .body(Step.of("child", context -> {}))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isFailed()).isTrue();
    }

    @Test
    @DisplayName("empty body throws IllegalStateException")
    void emptyBodyThrowsIllegalStateException() {
        assertThatThrownBy(() -> Instance.builder("instance", Object::new).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("body action must be configured");
    }

    @Test
    @DisplayName("AutoCloseable closed exactly once")
    void autoCloseableClosedExactlyOnce() {
        var closed = new AtomicInteger();

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Step.of("child", context -> {}))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("instance available to children")
    void instanceAvailableToChildren() {
        var instanceRef = new AtomicReference<CloseableFixture>();
        var closed = new AtomicInteger();

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Step.of("child", withInstance(CloseableFixture.class, instanceRef::set)))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(instanceRef.get()).isNotNull();
        assertThat(instanceRef.get()).isInstanceOf(CloseableFixture.class);
    }

    @Test
    @DisplayName("instance holder cleared after destroy")
    void instanceHolderClearedAfterDestroy() {
        var closed = new AtomicInteger();
        var instanceDuringBody = new AtomicReference<CloseableFixture>();

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Step.of("child", withInstance(CloseableFixture.class, instanceDuringBody::set)))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(instanceDuringBody.get()).isNotNull();
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("mixed child types execute in call order")
    void mixedBodyTypesExecuteInCallOrder() {
        var closed = new AtomicInteger();
        var executionOrder = new ArrayList<String>();

        var childBuilder = Instance.builder("child-c", () -> new CloseableFixture(closed))
                .body(Step.of("c-action", context -> executionOrder.add("c")));

        var action = Instance.builder("instance", () -> new CloseableFixture(closed))
                .body(Sequence.builder("body")
                        .child(Step.of("a", context -> executionOrder.add("a")))
                        .child(Step.of("b", context -> executionOrder.add("b")))
                        .child(childBuilder.build()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
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
        var action = Instance.builder(Object.class)
                .body(Step.of(
                        "child",
                        context -> assertThat(context.instance(Object.class)).isPresent()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.action().displayName()).isEqualTo("Object");
    }

    @Test
    @DisplayName("builder(String, Class) uses provided display name and default constructor")
    void builderStringClassUsesProvidedNameAndDefaultConstructor() {
        var action = Instance.builder("my-fixture", Object.class)
                .body(Step.of(
                        "child",
                        context -> assertThat(context.instance(Object.class)).isPresent()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.action().displayName()).isEqualTo("my-fixture");
    }

    @Test
    @DisplayName("builder(String, Class) throws IllegalArgumentException when class has no no-arg constructor")
    void builderStringClassThrowsWhenNoNoArgConstructor() {
        assertThatThrownBy(() -> Instance.builder("fixture", NoArgConstructorFixture.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not have a public no-argument constructor");
    }

    @Test
    @DisplayName("builder(String, Class) throws IllegalArgumentException when display name is blank")
    void builderStringClassThrowsWhenDisplayNameBlank() {
        assertThatThrownBy(() -> Instance.builder("   ", SimpleFixture.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName is blank");
    }
}

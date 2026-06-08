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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.SkipException;

@DisplayName("Timeout action")
class TimeoutTest {

    @Test
    @DisplayName("child completes within timeout — child's status propagates")
    void bodyCompletesWithinTimeout() {
        var action = Timeout.builder("fast-child")
                .body(Step.of("step", context -> {}))
                .timeout(Duration.ofSeconds(5))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isPassed()).isTrue();
    }

    @Test
    @DisplayName("child exceeds timeout — action FAILED, child FAILED")
    void bodyExceedsTimeout() {
        var action = Timeout.builder("slow-child")
                .body(Step.of("blocking-step", context -> {
                    Thread.sleep(5000);
                }))
                .timeout(Duration.ofMillis(50))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isFailed()).isTrue();
    }

    @Test
    @DisplayName("child thread is interrupted on timeout")
    void bodyThreadIsInterruptedOnTimeout() throws Exception {
        var interrupted = new AtomicBoolean(false);
        var latch = new CountDownLatch(1);

        var action = Timeout.builder("interrupt-child")
                .body(Step.of("sleep-step", context -> {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                }))
                .timeout(Duration.ofMillis(50))
                .build();

        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(interrupted.get()).isTrue();
        assertThat(root.isFailed()).isTrue();
    }

    @Test
    @DisplayName("child fails on its own within timeout — child's FAILED propagates")
    void bodyFailsWithinTimeout() {
        var action = Timeout.builder("failing-child")
                .body(Step.of("fail-step", context -> {
                    throw new RuntimeException("intentional failure");
                }))
                .timeout(Duration.ofSeconds(5))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.children().get(0).isFailed()).isTrue();
    }

    @Test
    @DisplayName("child RuntimeException preserves instance and stack trace")
    void bodyRuntimeExceptionPreservesInstance() {
        var exception = new RuntimeException("intentional failure");
        var action = Timeout.builder("failing-child")
                .body(Step.of("fail-step", context -> {
                    throw exception;
                }))
                .timeout(Duration.ofSeconds(5))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        var child = root.children().get(0);
        assertThat(child.isFailed()).isTrue();
        assertThat(child.throwable()).isPresent();
        assertThat(child.throwable().get()).isSameAs(exception);
    }

    @Test
    @DisplayName("child throws SkipException within timeout — SKIPPED propagates")
    void bodySkipsWithinTimeout() {
        var action = Timeout.builder("skipping-child")
                .body(Step.of("skip-step", context -> {
                    SkipException.skip("skip reason");
                }))
                .timeout(Duration.ofSeconds(5))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isSkipped()).isTrue();
        assertThat(root.children().get(0).isSkipped()).isTrue();
    }

    @Test
    @DisplayName("accessors return configured values")
    void accessorsReturnConfiguredValues() {
        var child = Step.of("child", context -> {});
        var duration = Duration.ofMillis(123);

        var action =
                Timeout.builder("accessor-test").body(child).timeout(duration).build();

        assertThat(action.displayName()).isEqualTo("accessor-test");
        assertThat(action.body()).isSameAs(child);
        assertThat(action.timeout()).isEqualTo(duration);
    }

    @Test
    @DisplayName("descriptor tree contains timed child")
    void descriptorTreeContainsTimedBody() {
        var action = Timeout.builder("tree-test")
                .body(Step.of("leaf", context -> {}))
                .timeout(Duration.ofSeconds(5))
                .build();

        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.action().displayName()).isEqualTo("tree-test");
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).action().displayName()).isEqualTo("leaf");
    }

    @Nested
    @DisplayName("Builder")
    class Builder {

        @Test
        @DisplayName("build throws when body not configured")
        void buildThrowsWhenBodyNotConfigured() {
            assertThatThrownBy(() -> Timeout.builder("bad-timeout")
                            .timeout(Duration.ofSeconds(1))
                            .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("child action must be configured");
        }

        @Test
        @DisplayName("body accepts another Builder")
        void bodyAcceptsAnotherBuilder() {
            var child = Step.of("inner-step", context -> {});
            var timeout = Timeout.builder("from-builder")
                    .body(Instance.builder("inner", Object::new).body(child))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            assertThat(timeout.displayName()).isEqualTo("from-builder");
            assertThat(timeout.timeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(timeout.body()).isNotNull();
        }
    }
}

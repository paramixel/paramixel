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

package org.paramixel.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.SkipException;

@DisplayName("Lifecycle")
class LifecycleTest {

    record TestData(String value) {}

    @Test
    @DisplayName("before and after share same context, main runs in child context")
    void beforeAndAfterShareSameContextMainRunsInChildContext() {
        var mainAccessedLifecycle = new AtomicBoolean();
        var afterAccessedAttachment = new AtomicBoolean();
        var attachmentData = new TestData("shared");

        Lifecycle lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> {
                    context.setAttachment(attachmentData);
                }),
                Direct.of("test", context -> {
                    var lifecycleContext = context.findContext(1).orElseThrow();
                    var data = lifecycleContext
                            .getAttachment()
                            .flatMap(a -> a.to(TestData.class))
                            .orElse(null);
                    assertThat(data).isNotNull();
                    assertThat(data.value()).isEqualTo("shared");
                    mainAccessedLifecycle.set(true);
                }),
                Direct.of("after", context -> {
                    var data = context.getAttachment()
                            .flatMap(a -> a.to(TestData.class))
                            .orElse(null);
                    assertThat(data).isNotNull();
                    assertThat(data.value()).isEqualTo("shared");
                    afterAccessedAttachment.set(true);
                }));

        Runner runner = Runner.builder().build();
        runner.run(lifecycle);
        Result result = lifecycle.getResult();

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(mainAccessedLifecycle).isTrue();
        assertThat(afterAccessedAttachment).isTrue();
    }

    @Test
    @DisplayName("of(name, before, main, after) creates full lifecycle that passes")
    void ofNameBeforeMainAfterCreatesFullLifecycleThatPasses() {
        var beforeRan = new AtomicBoolean();
        var afterRan = new AtomicBoolean();
        Action main = Noop.of("main");
        var lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> beforeRan.set(true)),
                main,
                Direct.of("after", context -> afterRan.set(true)));

        Runner runner = Runner.builder().build();
        runner.run(lifecycle);
        Result result = lifecycle.getResult();

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(beforeRan).isTrue();
        assertThat(afterRan).isTrue();
        assertThat(lifecycle.getChildren()).hasSize(3);
    }

    @Test
    @DisplayName("before failure prevents main execution but runs after")
    void beforeFailurePreventsMainExecutionButRunsAfter() {
        var beforeException = new RuntimeException("before failed");
        var mainRan = new AtomicBoolean();
        var afterRan = new AtomicBoolean();
        Action main = Direct.of("main", context -> mainRan.set(true));
        Lifecycle lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> {
                    throw beforeException;
                }),
                main,
                Direct.of("after", context -> afterRan.set(true)));

        Runner runner = Runner.builder().build();
        runner.run(lifecycle);
        Result result = lifecycle.getResult();

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getStatus().getMessage()).isPresent();
        assertThat(result.getStatus().getMessage().get()).isEqualTo("before failed");
        assertThat(mainRan).isFalse();
        assertThat(afterRan).isTrue();
        assertThat(lifecycle.getChildren()).hasSize(3);
        assertThat(lifecycle.getChildren().get(0).getResult().getStatus().isFailure())
                .isTrue();
        assertThat(lifecycle.getChildren().get(0).getResult().getStatus().getThrowable())
                .containsSame(beforeException);
        assertThat(lifecycle.getChildren().get(1).getResult().getStatus().isSkip())
                .isTrue();
        assertThat(lifecycle.getChildren().get(2).getResult().getStatus().isPass())
                .isTrue();
    }

    @Test
    @DisplayName("before SkipException skips main but runs after")
    void beforeSkipExceptionSkipsMainButRunsAfter() {
        var skipException = new SkipException("before skipped");
        var mainRan = new AtomicBoolean();
        var afterRan = new AtomicBoolean();
        Action main = Direct.of("main", context -> mainRan.set(true));
        Lifecycle lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> {
                    throw skipException;
                }),
                main,
                Direct.of("after", context -> afterRan.set(true)));

        Runner runner = Runner.builder().build();
        runner.run(lifecycle);
        Result result = lifecycle.getResult();

        assertThat(result.getStatus().isSkip()).isTrue();
        assertThat(result.getStatus().getMessage()).isPresent();
        assertThat(result.getStatus().getMessage().get()).isEqualTo("before skipped");
        assertThat(mainRan).isFalse();
        assertThat(afterRan).isTrue();
        assertThat(lifecycle.getChildren()).hasSize(3);
        assertThat(lifecycle.getChildren().get(0).getResult().getStatus().isSkip())
                .isTrue();
        assertThat(lifecycle.getChildren().get(1).getResult().getStatus().isSkip())
                .isTrue();
        assertThat(lifecycle.getChildren().get(2).getResult().getStatus().isPass())
                .isTrue();
    }

    @Test
    @DisplayName("after failure after main pass fails with after exception")
    void afterFailureAfterMainPassFailsWithAfterException() {
        var afterException = new RuntimeException("after failed");
        var beforeRan = new AtomicBoolean();
        Action main = Noop.of("main");
        var lifecycle = Lifecycle.of(
                "lifecycle", Direct.of("before", context -> beforeRan.set(true)), main, Direct.of("after", context -> {
                    throw afterException;
                }));

        Runner runner = Runner.builder().build();
        runner.run(lifecycle);
        Result result = lifecycle.getResult();

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getStatus().getMessage()).isPresent();
        assertThat(result.getStatus().getMessage().get()).isEqualTo("after failed");
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(beforeRan).isTrue();
        assertThat(lifecycle.getChildren()).hasSize(3);
        assertThat(lifecycle.getChildren().get(0).getResult().getStatus().isPass())
                .isTrue();
        assertThat(lifecycle.getChildren().get(1).getResult().getStatus().isPass())
                .isTrue();
        assertThat(lifecycle.getChildren().get(2).getResult().getStatus().isFailure())
                .isTrue();
        assertThat(lifecycle
                        .getChildren()
                        .get(2)
                        .getResult()
                        .getStatus()
                        .getThrowable()
                        .get())
                .isSameAs(afterException);
    }

    @Test
    @DisplayName("after runs even when before throws SkipException")
    void afterRunsEvenWhenBeforeThrowsSkipException() {
        var afterRan = new AtomicBoolean();
        var lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> {
                    throw new SkipException("skip");
                }),
                Noop.of("main"),
                Direct.of("after", context -> afterRan.set(true)));

        Runner runner = Runner.builder().build();
        runner.run(lifecycle);
        Result result = lifecycle.getResult();

        assertThat(result.getStatus().isSkip()).isTrue();
        assertThat(afterRan).isTrue();
    }

    @Test
    @DisplayName("after runs even when before throws exception")
    void afterRunsEvenWhenBeforeThrowsException() {
        var afterRan = new AtomicBoolean();
        var lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> {
                    throw new RuntimeException("before failed");
                }),
                Noop.of("main"),
                Direct.of("after", context -> afterRan.set(true)));

        Runner runner = Runner.builder().build();
        runner.run(lifecycle);
        Result result = lifecycle.getResult();

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(afterRan).isTrue();
    }

    @Test
    @DisplayName("after runs even when main fails")
    void afterRunsEvenWhenMainFails() {
        var afterRan = new AtomicBoolean();
        var lifecycle = Lifecycle.of(
                "lifecycle",
                Noop.of("before"),
                Direct.of("main", context -> {
                    throw new RuntimeException("main failed");
                }),
                Direct.of("after", context -> afterRan.set(true)));

        Runner runner = Runner.builder().build();
        runner.run(lifecycle);
        Result result = lifecycle.getResult();

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(afterRan).isTrue();
    }

    @Test
    @DisplayName("after runs even when both before and main fail")
    void afterRunsEvenWhenBothBeforeAndMainFail() {
        var afterRan = new AtomicBoolean();
        var lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> {
                    throw new RuntimeException("before failed");
                }),
                Noop.of("main"),
                Direct.of("after", context -> afterRan.set(true)));

        Runner runner = Runner.builder().build();
        runner.run(lifecycle);
        Result result = lifecycle.getResult();

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(afterRan).isTrue();
    }
}

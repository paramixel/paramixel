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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
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
        var skipException = SkipException.of("before skipped");
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
                    SkipException.skip();
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

    @Test
    @DisplayName("skipped main receives child context with proper parent hierarchy")
    void skippedMainReceivesChildContextWithProperParentHierarchy() {
        var mainContext = new AtomicReference<Context>();

        Listener capturingListener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                if (action.getName().equals("main")) {
                    mainContext.set(context);
                }
            }
        };

        Action main = Noop.of("main");
        Lifecycle lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> {
                    throw new RuntimeException("before failed");
                }),
                main,
                Noop.of("after"));

        Runner runner = Runner.builder().listener(capturingListener).build();
        runner.run(lifecycle);

        assertThat(main.getResult().getStatus().isSkip()).isTrue();
        assertThat(mainContext.get()).isNotNull();
        assertThat(mainContext.get().getParent()).isPresent();
    }

    @Test
    @DisplayName("skipped descendants receive child contexts mirroring action tree")
    void skippedDescendantsReceiveChildContextsMirroringActionTree() {
        var nestedContext = new AtomicReference<Context>();
        var grandchildContext = new AtomicReference<Context>();

        Listener capturingListener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                if (action.getName().equals("nested")) {
                    nestedContext.set(context);
                } else if (action.getName().equals("grandchild")) {
                    grandchildContext.set(context);
                }
            }
        };

        Action grandchild = Noop.of("grandchild");
        Action nestedMain = Lifecycle.of("nested", Noop.of("nested-before"), grandchild, Noop.of("nested-after"));

        Lifecycle lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> {
                    throw new RuntimeException("before failed");
                }),
                nestedMain,
                Noop.of("after"));

        Runner runner = Runner.builder().listener(capturingListener).build();
        runner.run(lifecycle);

        assertThat(nestedMain.getResult().getStatus().isSkip()).isTrue();
        assertThat(nestedContext.get()).isNotNull();
        assertThat(grandchildContext.get()).isNotNull();
        assertThat(grandchildContext.get().getParent()).contains(nestedContext.get());
    }

    @Test
    @DisplayName("skip listener callbacks interleave parent before children then parent after")
    void skipListenerCallbacksInterleaveCorrectly() {
        List<String> callbackOrder = new ArrayList<>();

        Listener trackingListener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                callbackOrder.add("before:" + action.getName());
            }

            @Override
            public void afterAction(Context context, Action action, Result result) {
                callbackOrder.add("after:" + action.getName());
            }
        };

        Action grandchild = Noop.of("grandchild");
        Action nestedMain = Lifecycle.of("nested", Noop.of("nested-before"), grandchild, Noop.of("nested-after"));

        Lifecycle lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> {
                    throw new RuntimeException("before failed");
                }),
                nestedMain,
                Noop.of("after"));

        Runner runner = Runner.builder().listener(trackingListener).build();
        runner.run(lifecycle);

        int nestedBeforeIdx = callbackOrder.indexOf("before:nested");
        int grandchildBeforeIdx = callbackOrder.indexOf("before:grandchild");
        int grandchildAfterIdx = callbackOrder.indexOf("after:grandchild");
        int nestedAfterIdx = callbackOrder.indexOf("after:nested");

        assertThat(nestedBeforeIdx).isGreaterThan(-1);
        assertThat(grandchildBeforeIdx).isGreaterThan(-1);
        assertThat(grandchildAfterIdx).isGreaterThan(-1);
        assertThat(nestedAfterIdx).isGreaterThan(-1);

        assertThat(nestedBeforeIdx).isLessThan(grandchildBeforeIdx);
        assertThat(grandchildAfterIdx).isLessThan(nestedAfterIdx);
    }

    @Test
    @DisplayName("skipped descendants can access parent attachment via findAttachment")
    void skippedDescendantsCanAccessParentAttachmentViaFindAttachment() {
        var grandchildFoundAttachment = new AtomicBoolean();

        Listener capturingListener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                if (action.getName().equals("grandchild")) {
                    context.findAttachment(2).ifPresent(a -> grandchildFoundAttachment.set(true));
                }
            }
        };

        Action grandchild = Noop.of("grandchild");
        Action nestedMain = Lifecycle.of(
                "nested",
                Direct.of("nested-before", context -> {
                    context.setAttachment(new TestData("nested-data"));
                }),
                grandchild,
                Noop.of("nested-after"));

        Lifecycle lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> {
                    context.setAttachment(new TestData("lifecycle-data"));
                    throw new RuntimeException("before failed");
                }),
                nestedMain,
                Noop.of("after"));

        Runner runner = Runner.builder().listener(capturingListener).build();
        runner.run(lifecycle);

        assertThat(nestedMain.getResult().getStatus().isSkip()).isTrue();
        assertThat(grandchildFoundAttachment).isTrue();
    }
}

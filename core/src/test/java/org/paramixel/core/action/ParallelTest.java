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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.exception.FailException;
import org.paramixel.core.exception.SkipException;

@DisplayName("Parallel")
class ParallelTest {

    @Test
    @DisplayName("passes when all children pass")
    void passesWhenAllChildrenPass() {
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {});
        Action root = Parallel.of("root", List.of(first, second));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(root.getChildren()).hasSize(2);
        assertThat(result.getChildren().get(0).getStatus().isPass()).isTrue();
        assertThat(result.getChildren().get(1).getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("fails when any child fails")
    void failsWhenAnyChildFails() {
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {
            throw new RuntimeException("boom");
        });
        Action root = Parallel.of("root", List.of(first, second));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getChildren().get(0).getStatus().isPass()).isTrue();
        assertThat(result.getChildren().get(1).getStatus().isFailure()).isTrue();
    }

    @Test
    @DisplayName("skips when any child skips with no failures")
    void skipsWhenAnyChildSkipsWithNoFailures() {
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {
            throw SkipException.of("not available");
        });
        Action root = Parallel.of("root", List.of(first, second));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isSkip()).isTrue();
        assertThat(result.getChildren().get(0).getStatus().isPass()).isTrue();
        assertThat(result.getChildren().get(1).getStatus().isSkip()).isTrue();
    }

    @Test
    @DisplayName("fails when children have mix of fail and skip")
    void failsWhenChildrenHaveMixOfFailAndSkip() {
        Action first = Direct.of("first", context -> {
            throw FailException.of("intentional fail");
        });
        Action second = Direct.of("second", context -> {
            throw SkipException.of("not available");
        });
        Action root = Parallel.of("root", List.of(first, second));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isFailure()).isTrue();
    }

    @Test
    @DisplayName("fires beforeAction and afterAction for all children and parent")
    void firesBeforeAndAfterCallbacks() {
        var events = new CopyOnWriteArrayList<String>();
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {});
        Action root = Parallel.of("root", List.of(first, second));

        Listener listener = new Listener() {
            @Override
            public void beforeAction(Result result) {
                events.add("before:" + result.getAction().getName());
            }

            @Override
            public void afterAction(Result result) {
                events.add("after:" + result.getAction().getName() + ":"
                        + result.getStatus().getDisplayName());
            }
        };

        Runner runner = Runner.builder().listener(listener).build();
        runner.run(root);

        assertThat(events).contains("before:root", "after:root:PASS");
        assertThat(events).contains("before:first", "after:first:PASS");
        assertThat(events).contains("before:second", "after:second:PASS");
    }

    @Test
    @DisplayName("afterAction always fires on successful execution")
    void afterActionAlwaysFiresOnSuccessfulExecution() {
        AtomicBoolean afterActionCalled = new AtomicBoolean(false);
        AtomicReference<Result> capturedResult = new AtomicReference<>();

        Action root = Parallel.of("root", Direct.of("a", context -> {}), Direct.of("b", context -> {}));

        Listener listener = new Listener() {
            @Override
            public void afterAction(Result result) {
                if (result.getAction() == root) {
                    afterActionCalled.set(true);
                    capturedResult.set(result);
                }
            }
        };

        Runner runner = Runner.builder().listener(listener).build();
        runner.run(root);

        assertThat(afterActionCalled).isTrue();
        assertThat(capturedResult.get().getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("has correct parent references")
    void hasCorrectParentReferences() {
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {});
        Action root = Parallel.of("root", List.of(first, second));

        Runner runner = Runner.builder().build();
        runner.run(root);

        assertThat(root.getParent()).isEmpty();
        assertThat(root.getChildren()).hasSize(2);
        assertThat(first.getParent()).contains(root);
        assertThat(second.getParent()).contains(root);
    }

    @Test
    @DisplayName("rejects null name")
    void rejectsNullName() {
        assertThatThrownBy(() -> Parallel.of(null, List.of(Direct.of("a", context -> {}))))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name must not be null");
    }

    @Test
    @DisplayName("rejects blank name")
    void rejectsBlankName() {
        assertThatThrownBy(() -> Parallel.of("  ", List.of(Direct.of("a", context -> {}))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }

    @Test
    @DisplayName("rejects empty children list")
    void rejectsEmptyChildrenList() {
        assertThatThrownBy(() -> Parallel.of("root", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("children must not be empty");
    }

    @Test
    @DisplayName("rejects null children in list")
    void rejectsNullChildrenInList() {
        assertThatThrownBy(() -> Parallel.of("root", List.of((Action) null))).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects zero parallelism")
    void rejectsZeroParallelism() {
        assertThatThrownBy(() -> Parallel.of("root", 0, Direct.of("a", context -> {})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("parallelism must be positive, was: 0");
    }

    @Test
    @DisplayName("parallelism limits concurrent execution")
    void parallelismLimitsConcurrentExecution() {
        var maxConcurrent = new java.util.concurrent.atomic.AtomicInteger(0);
        var currentConcurrent = new java.util.concurrent.atomic.AtomicInteger(0);

        Action a = Direct.of("a", context -> {
            int current = currentConcurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(current, Math::max);
            currentConcurrent.decrementAndGet();
        });
        Action b = Direct.of("b", context -> {
            int current = currentConcurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(current, Math::max);
            currentConcurrent.decrementAndGet();
        });
        Action c = Direct.of("c", context -> {
            int current = currentConcurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(current, Math::max);
            currentConcurrent.decrementAndGet();
        });

        Action root = Parallel.of("root", 1, List.of(a, b, c));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(maxConcurrent.get()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("interrupt during semaphore acquisition sets FAIL result and fires afterAction")
    void interruptDuringSemaphoreAcquisitionSetsFailResultAndFiresAfterAction() throws Exception {
        CountDownLatch firstChildStarted = new CountDownLatch(1);
        CountDownLatch allowFirstChildToFinish = new CountDownLatch(1);

        Action first = Direct.of("first", context -> {
            firstChildStarted.countDown();
            try {
                allowFirstChildToFinish.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Action second = Direct.of("second", context -> {});
        Action root = Parallel.of("root", 1, List.of(first, second));

        AtomicBoolean beforeActionCalled = new AtomicBoolean(false);
        AtomicBoolean afterActionCalled = new AtomicBoolean(false);
        AtomicReference<Result> capturedResult = new AtomicReference<>();

        Listener listener = new Listener() {
            @Override
            public void beforeAction(Result result) {
                if (result.getAction() == root) {
                    beforeActionCalled.set(true);
                }
            }

            @Override
            public void afterAction(Result result) {
                if (result.getAction() == root) {
                    afterActionCalled.set(true);
                    capturedResult.set(result);
                }
            }
        };

        Runner runner = Runner.builder().listener(listener).build();

        AtomicReference<RuntimeException> caught = new AtomicReference<>();
        Thread testThread = new Thread(() -> {
            try {
                runner.run(root);
            } catch (RuntimeException e) {
                caught.set(e);
            }
        });

        testThread.start();
        firstChildStarted.await();
        Thread.sleep(100);
        testThread.interrupt();
        allowFirstChildToFinish.countDown();
        testThread.join(10_000);

        assertThat(testThread.isAlive()).isFalse();
        assertThat(beforeActionCalled).isTrue();
        assertThat(afterActionCalled).isTrue();
        assertThat(capturedResult.get()).isNotNull();
        assertThat(capturedResult.get().getStatus().isFailure()).isTrue();
        assertThat(capturedResult.get().getStatus().getThrowable()).isPresent();
        assertThat(capturedResult.get().getStatus().getThrowable().get()).isInstanceOf(InterruptedException.class);
    }

    @Test
    @DisplayName("interrupt during semaphore acquisition throws RuntimeException wrapping InterruptedException")
    void interruptDuringSemaphoreAcquisitionThrowsRuntimeException() throws Exception {
        CountDownLatch firstChildStarted = new CountDownLatch(1);
        CountDownLatch allowFirstChildToFinish = new CountDownLatch(1);

        Action first = Direct.of("first", context -> {
            firstChildStarted.countDown();
            try {
                allowFirstChildToFinish.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Action second = Direct.of("second", context -> {});
        Action root = Parallel.of("root", 1, List.of(first, second));

        Runner runner = Runner.builder().build();

        AtomicReference<RuntimeException> caught = new AtomicReference<>();
        Thread testThread = new Thread(() -> {
            try {
                runner.run(root);
            } catch (RuntimeException e) {
                caught.set(e);
            }
        });

        testThread.start();
        firstChildStarted.await();
        Thread.sleep(100);
        testThread.interrupt();
        allowFirstChildToFinish.countDown();
        testThread.join(10_000);

        assertThat(testThread.isAlive()).isFalse();
        assertThat(caught.get()).isNotNull();
        assertThat(caught.get().getCause()).isInstanceOf(InterruptedException.class);
    }

    @Test
    @DisplayName("interrupt during semaphore acquisition preserves interrupt flag after re-throw")
    void interruptDuringSemaphoreAcquisitionPreservesInterruptFlag() throws Exception {
        CountDownLatch firstChildStarted = new CountDownLatch(1);
        CountDownLatch allowFirstChildToFinish = new CountDownLatch(1);

        Action first = Direct.of("first", context -> {
            firstChildStarted.countDown();
            try {
                allowFirstChildToFinish.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Action second = Direct.of("second", context -> {});
        Action root = Parallel.of("root", 1, List.of(first, second));

        Runner runner = Runner.builder().build();

        AtomicBoolean interruptFlagAfterExecution = new AtomicBoolean(false);
        Thread testThread = new Thread(() -> {
            try {
                runner.run(root);
            } catch (RuntimeException e) {
                interruptFlagAfterExecution.set(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        });

        testThread.start();
        firstChildStarted.await();
        Thread.sleep(100);
        testThread.interrupt();
        allowFirstChildToFinish.countDown();
        testThread.join(10_000);

        assertThat(testThread.isAlive()).isFalse();
        assertThat(interruptFlagAfterExecution).isTrue();
    }

    @Test
    @DisplayName("interrupt during semaphore accumulation calls both beforeAction and afterAction")
    void interruptDuringSemaphoreAcquisitionCallsBeforeAndAfterAction() throws Exception {
        CountDownLatch firstChildStarted = new CountDownLatch(1);
        CountDownLatch allowFirstChildToFinish = new CountDownLatch(1);

        Action first = Direct.of("first", context -> {
            firstChildStarted.countDown();
            try {
                allowFirstChildToFinish.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Action second = Direct.of("second", context -> {});
        Action root = Parallel.of("root", 1, List.of(first, second));

        var listenerCalls = new CopyOnWriteArrayList<String>();

        Listener listener = new Listener() {
            @Override
            public void beforeAction(Result result) {
                if (result.getAction() == root) {
                    listenerCalls.add("beforeAction:" + result.getAction().getName());
                }
            }

            @Override
            public void afterAction(Result result) {
                if (result.getAction() == root) {
                    listenerCalls.add("afterAction:" + result.getAction().getName() + ":"
                            + result.getStatus().getDisplayName());
                }
            }
        };

        Runner runner = Runner.builder().listener(listener).build();

        Thread testThread = new Thread(() -> {
            try {
                runner.run(root);
            } catch (RuntimeException e) {
                // expected
            }
        });

        testThread.start();
        firstChildStarted.await();
        Thread.sleep(100);
        testThread.interrupt();
        allowFirstChildToFinish.countDown();
        testThread.join(10_000);

        assertThat(testThread.isAlive()).isFalse();
        assertThat(listenerCalls).containsExactly("beforeAction:root", "afterAction:root:FAIL");
    }
}

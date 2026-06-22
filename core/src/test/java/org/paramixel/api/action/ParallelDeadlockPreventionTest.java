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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;

@DisplayName("Parallel deadlock prevention")
class ParallelDeadlockPreventionTest {

    @Test
    @DisplayName("Parallel with many children does not deadlock")
    void parallelWithManyChildrenDoesNotDeadlock() {
        int childCount = 2_000;
        var executionCount = new AtomicInteger(0);

        var parallel = Parallel.builder("many-children").parallelism(50);

        for (int i = 0; i < childCount; i++) {
            int index = i;
            parallel.child(Step.of("child-" + index, context -> {
                executionCount.incrementAndGet();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        var result = Runner.defaultRunner().run(parallel.build());

        assertThat(result.isPassed() || result.isFailed() || result.isSkipped() || result.isAborted())
                .isTrue();
        assertThat(executionCount.get()).isEqualTo(childCount);
    }

    @Test
    @DisplayName("Nested Parallel actions do not deadlock")
    void nestedParallelActionsDoNotDeadlock() {
        var executionCount = new AtomicInteger(0);
        int outerParallelism = 20;
        int innerParallelism = 10;
        int outerChildren = 50;
        int innerChildren = 50;

        var outer = Parallel.builder("nested-parallel").parallelism(outerParallelism);

        for (int i = 0; i < outerChildren; i++) {
            int outerIndex = i;
            var inner = Parallel.builder("outer-" + outerIndex).parallelism(innerParallelism);

            for (int j = 0; j < innerChildren; j++) {
                int innerIndex = j;
                inner.child(Step.of("inner-" + innerIndex, context -> {
                    executionCount.incrementAndGet();
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            outer.child(inner.build());
        }

        var result = Runner.defaultRunner().run(outer.build());

        assertThat(result.isPassed() || result.isFailed() || result.isSkipped() || result.isAborted())
                .isTrue();
        assertThat(executionCount.get()).isEqualTo(outerChildren * innerChildren);
    }

    @Test
    @DisplayName("Parallel with constrained scheduler does not deadlock")
    void parallelWithConstrainedSchedulerDoesNotDeadlock() {
        int childCount = 500;
        var executionCount = new AtomicInteger(0);

        var parallel = Parallel.builder("constrained-scheduler").parallelism(20);

        for (int i = 0; i < childCount; i++) {
            int index = i;
            parallel.child(Step.of("child-" + index, context -> {
                executionCount.incrementAndGet();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        var result = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "10")))
                .build()
                .run(parallel.build());

        assertThat(result.isPassed() || result.isFailed() || result.isSkipped() || result.isAborted())
                .isTrue();
        assertThat(executionCount.get()).isEqualTo(childCount);
    }

    @Test
    @DisplayName("Parallel fully utilizes global parallelism")
    void parallelFullyUtilizesGlobalParallelism() throws Exception {
        int childCount = 50;
        var executionCount = new AtomicInteger(0);
        var active = new AtomicInteger(0);
        var maxActive = new AtomicInteger(0);
        var allStarted = new CountDownLatch(8);
        var mayProceed = new CountDownLatch(1);

        var parallel = Parallel.builder("parallelism-test").parallelism(50);

        for (int i = 0; i < childCount; i++) {
            int index = i;
            parallel.child(Step.of("child-" + index, context -> {
                executionCount.incrementAndGet();
                int current = active.incrementAndGet();
                maxActive.accumulateAndGet(current, Math::max);
                allStarted.countDown();
                try {
                    mayProceed.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    active.decrementAndGet();
                }
            }));
        }

        var runnerFuture = CompletableFuture.supplyAsync(() -> Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "8")))
                .build()
                .run(parallel.build()));

        assertThat(allStarted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(maxActive.get()).isEqualTo(8);
        mayProceed.countDown();

        var result = runnerFuture.get(10, TimeUnit.SECONDS);

        assertThat(result.isPassed() || result.isFailed() || result.isSkipped() || result.isAborted())
                .isTrue();
        assertThat(executionCount.get()).isEqualTo(childCount);
    }
}

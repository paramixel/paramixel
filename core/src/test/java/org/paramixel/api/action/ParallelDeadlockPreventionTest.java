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
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;

/**
 * Tests verifying that Parallel actions do not deadlock under high-concurrency scenarios.
 *
 * <p>These tests verify the backpressure mechanism that prevents scheduler thread exhaustion
 * when executing many children or nested Parallel actions. The scheduler implements automatic
 * queue capacity checking to avoid blocking all worker threads.
 */
@DisplayName("Parallel deadlock prevention")
class ParallelDeadlockPreventionTest {

    /**
     * Verifies that Parallel actions with many children (2000) complete without deadlock.
     *
     * <p>This test exercises the backpressure mechanism that pauses child scheduling when
     * the scheduler's ready queue reaches 90% capacity, allowing completion callbacks to
     * drain the queue naturally.
     */
    @Test
    @DisplayName("Parallel with many children does not deadlock")
    void parallelWithManyChildrenDoesNotDeadlock() {
        int childCount = 2000;
        AtomicInteger executionCount = new AtomicInteger(0);

        var parallel = Parallel.of("many-children").parallelism(50);

        for (int i = 0; i < childCount; i++) {
            int index = i;
            parallel.child(Step.of("child-" + index, ctx -> {
                executionCount.incrementAndGet();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        var result = Runner.defaultRunner().run(parallel);

        assertThat(result.status().isTerminal()).isTrue();
        assertThat(executionCount.get()).isEqualTo(childCount);
    }

    /**
     * Verifies that nested Parallel actions (50×50 = 2500 total children) complete without deadlock.
     *
     * <p>This test exercises hierarchical scheduling where inner Parallel actions compete for
     * scheduler slots while outer Parallel actions continue scheduling. The backpressure mechanism
     * prevents all scheduler threads from blocking on queue capacity.
     */
    @Test
    @DisplayName("Nested Parallel actions do not deadlock")
    void nestedParallelActionsDoNotDeadlock() {
        AtomicInteger executionCount = new AtomicInteger(0);
        int outerParallelism = 20;
        int innerParallelism = 10;
        int outerChildren = 50;
        int innerChildren = 50;

        var outer = Parallel.of("nested-parallel").parallelism(outerParallelism);

        for (int i = 0; i < outerChildren; i++) {
            int outerIndex = i;
            var inner = Parallel.of("outer-" + outerIndex).parallelism(innerParallelism);

            for (int j = 0; j < innerChildren; j++) {
                int innerIndex = j;
                inner.child(Step.of("inner-" + innerIndex, ctx -> {
                    executionCount.incrementAndGet();
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            outer.child(inner);
        }

        var result = Runner.defaultRunner().run(outer);

        assertThat(result.status().isTerminal()).isTrue();
        assertThat(executionCount.get()).isEqualTo(outerChildren * innerChildren);
    }

    /**
     * Verifies that Parallel actions with constrained parallelism complete without deadlock.
     *
     * <p>This test exercises the backpressure mechanism with a runner configured for
     * 10 parallelism slots and a Parallel action requesting 20, verifying that queue
     * capacity checking works correctly when the scheduler is more constrained than
     * the action's parallelism.
     */
    @Test
    @DisplayName("Parallel with constrained scheduler does not deadlock")
    void parallelWithConstrainedSchedulerDoesNotDeadlock() {
        int childCount = 500;
        AtomicInteger executionCount = new AtomicInteger(0);

        var parallel = Parallel.of("constrained-scheduler").parallelism(20);

        for (int i = 0; i < childCount; i++) {
            int index = i;
            parallel.child(Step.of("child-" + index, ctx -> {
                executionCount.incrementAndGet();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        var result = Runner.builder()
                .configuration(org.paramixel.api.Configuration.of(
                        java.util.Map.of(org.paramixel.api.Configuration.RUNNER_PARALLELISM, "10")))
                .build()
                .run(parallel);

        assertThat(result.status().isTerminal()).isTrue();
        assertThat(executionCount.get()).isEqualTo(childCount);
    }

    /**
     * Verifies that Parallel actions fully utilize global parallelism slots.
     *
     * <p>This test verifies that when Parallel parallelism is higher than global parallelism,
     * all global slots are utilized. The execution callback mechanism ensures that Parallel
     * tracks actually executing tasks rather than queued tasks.
     */
    @Test
    @DisplayName("Parallel fully utilizes global parallelism")
    void parallelFullyUtilizesGlobalParallelism() {
        int childCount = 50;
        AtomicInteger executionCount = new AtomicInteger(0);
        AtomicInteger active = new AtomicInteger(0);
        AtomicInteger maxActive = new AtomicInteger(0);

        var parallel = Parallel.of("parallelism-test").parallelism(50);

        for (int i = 0; i < childCount; i++) {
            int index = i;
            parallel.child(Step.of("child-" + index, ctx -> {
                executionCount.incrementAndGet();
                int current = active.incrementAndGet();
                maxActive.accumulateAndGet(current, Math::max);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    active.decrementAndGet();
                }
            }));
        }

        var result = Runner.builder()
                .configuration(org.paramixel.api.Configuration.of(
                        java.util.Map.of(org.paramixel.api.Configuration.RUNNER_PARALLELISM, "8")))
                .build()
                .run(parallel);

        assertThat(result.status().isTerminal()).isTrue();
        assertThat(executionCount.get()).isEqualTo(childCount);
        assertThat(maxActive.get()).isEqualTo(8);
    }
}

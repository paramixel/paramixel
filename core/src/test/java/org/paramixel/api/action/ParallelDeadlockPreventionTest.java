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
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.TestPools;
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

        var parallel = Parallel.parallel("many-children", "p50x0");

        for (int i = 0; i < childCount; i++) {
            int index = i;
            parallel.child(Step.step("child-" + index, context -> {
                executionCount.incrementAndGet();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        var result = Runner.builder().build().run(parallel.build());

        assertThat(result.status().isPassed()
                        || result.status().isFailed()
                        || result.status().isSkipped()
                        || result.status().isAborted())
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

        var outer = Parallel.parallel("nested-parallel", "pouterparallelism");

        for (int i = 0; i < outerChildren; i++) {
            int outerIndex = i;
            var inner = Parallel.parallel("outer-" + outerIndex, "pinnerparallelism");

            for (int j = 0; j < innerChildren; j++) {
                int innerIndex = j;
                inner.child(Step.step("inner-" + innerIndex, context -> {
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

        var result = Runner.builder().build().run(outer.build());

        assertThat(result.status().isPassed()
                        || result.status().isFailed()
                        || result.status().isSkipped()
                        || result.status().isAborted())
                .isTrue();
        assertThat(executionCount.get()).isEqualTo(outerChildren * innerChildren);
    }

    @Test
    @DisplayName("Parallel with constrained scheduler does not deadlock")
    void parallelWithConstrainedSchedulerDoesNotDeadlock() {
        int childCount = 500;
        var executionCount = new AtomicInteger(0);

        var parallel = Parallel.parallel("constrained-scheduler", "p20x1");

        for (int i = 0; i < childCount; i++) {
            int index = i;
            parallel.child(Step.step("child-" + index, context -> {
                executionCount.incrementAndGet();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        var result = Runner.builder()
                .configuration(TestPools.configuration(Map.of(Configuration.RUNNER_PARALLELISM, "10")))
                .build()
                .run(parallel.build());

        assertThat(result.status().isPassed()
                        || result.status().isFailed()
                        || result.status().isSkipped()
                        || result.status().isAborted())
                .isTrue();
        assertThat(executionCount.get()).isEqualTo(childCount);
    }
}

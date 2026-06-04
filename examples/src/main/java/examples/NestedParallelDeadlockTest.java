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

package examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Step;

/**
 * Demonstrates that nested Parallel actions complete without deadlock.
 *
 * <p>This test verifies that the scheduler correctly handles deeply nested Parallel
 * structures. The scheduler uses a single shared {@link java.util.concurrent.ThreadPoolExecutor}
 * with semaphore-based admission control (see {@link nonapi.org.paramixel.Scheduler}).
 * When a thread blocks waiting for a child Parallel to complete, it releases its semaphore
 * permit, allowing other leaf actions at any depth to proceed. This prevents deadlock even
 * with multiple levels of nested Parallels sharing the same executor thread pool.</p>
 *
 * <p>Test structure:
 * <ul>
 *   <li>Root Parallel (parallelism=2)</li>
 *   <li>Two mid-level Parallels (parallelism=2 each)</li>
 *   <li>Four leaf Parallels with 2 children each</li>
 * </ul>
 * This creates three levels of nesting, all sharing the single scheduler executor.</p>
 */
public class NestedParallelDeadlockTest {

    private static final AtomicInteger executedCount = new AtomicInteger();
    private static final int EXPECTED_EXECUTIONS = 8;

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        executedCount.set(0);
        Runner.defaultRunner().runAndExit(factory());
    }

    /**
     * Builds a deeply nested Parallel action tree to verify deadlock-free execution.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        executedCount.set(0);

        var testName = NestedParallelDeadlockTest.class.getName();

        var leaf1 = Parallel.builder("leaf1")
                .child(Step.of("l1-1", context -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                }))
                .child(Step.of("l1-2", context -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                }));

        var leaf2 = Parallel.builder("leaf2")
                .child(Step.of("l2-1", context -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                }))
                .child(Step.of("l2-2", context -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                }));

        var mid1 = Parallel.builder("mid1").parallelism(2).child(leaf1.build()).child(leaf2.build());

        var leaf3 = Parallel.builder("leaf3")
                .child(Step.of("l3-1", context -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                }))
                .child(Step.of("l3-2", context -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                }));

        var leaf4 = Parallel.builder("leaf4")
                .child(Step.of("l4-1", context -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                }))
                .child(Step.of("l4-2", context -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                }));

        var mid2 = Parallel.builder("mid2").parallelism(2).child(leaf3.build()).child(leaf4.build());

        return Scope.builder(testName)
                .body(Parallel.builder("root")
                        .parallelism(2)
                        .child(mid1.build())
                        .child(mid2.build())
                        .build())
                .after(Step.of("validate", ignored -> validate()))
                .build();
    }

    /**
     * Validates that all leaf tasks executed exactly once.
     */
    public static void validate() {
        assertThat(executedCount.get())
                .as("All leaf tasks should execute exactly once")
                .isEqualTo(EXPECTED_EXECUTIONS);
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

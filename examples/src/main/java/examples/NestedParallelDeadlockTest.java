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
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Spec;
import org.paramixel.api.action.Step;

/**
 * Demonstrates that nested Parallel actions complete without deadlock.
 *
 * <p>This test verifies that the scheduler's managedJoin mechanism correctly handles
 * deeply nested Parallel structures by executing queued work inline during join operations.
 * Without proper inline execution slot management, nested Parallel actions can deadlock
 * when all scheduler workers become blocked waiting for child completion.</p>
 *
 * <p>Test structure:
 * <ul>
 *   <li>Root Parallel (parallelism=2)</li>
 *   <li>Two mid-level Parallels (parallelism=2 each)</li>
 *   <li>Four leaf Parallels with 2 children each</li>
 * </ul>
 * This creates three levels of nesting that stress the managedJoin inline execution path.</p>
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
    public static Spec<?> factory() {
        executedCount.set(0);

        var testName = NestedParallelDeadlockTest.class.getName();

        var leaf1 = Parallel.of("leaf1")
                .child("l1-1", ctx -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                })
                .child("l1-2", ctx -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                });

        var leaf2 = Parallel.of("leaf2")
                .child("l2-1", ctx -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                })
                .child("l2-2", ctx -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                });

        var mid1 = Parallel.of("mid1").parallelism(2).child(leaf1).child(leaf2);

        var leaf3 = Parallel.of("leaf3")
                .child("l3-1", ctx -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                })
                .child("l3-2", ctx -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                });

        var leaf4 = Parallel.of("leaf4")
                .child("l4-1", ctx -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                })
                .child("l4-2", ctx -> {
                    executedCount.incrementAndGet();
                    sleep(10);
                });

        var mid2 = Parallel.of("mid2").parallelism(2).child(leaf3).child(leaf4);

        return Lifecycle.of(testName)
                .child(Parallel.of("root").parallelism(2).child(mid1).child(mid2))
                .after(Step.of("validate", ignored -> validate()));
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

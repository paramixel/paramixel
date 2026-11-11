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
import static org.paramixel.api.Tasks.sequential;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates that nested parallel groups with distinct pools complete without deadlock and without leaking permits.
 *
 * <p>Each nested group names a different pool, so the acquisition order is acyclic. Nesting the same pool is rejected at
 * compile time regardless of configured capacity, because a configuration change could otherwise turn a passing plan
 * into a deadlock.
 */
public class NestedParallelDeadlockTest {

    private static final int EXPECTED_EXECUTIONS = 8;

    private static final AtomicInteger executedCount = new AtomicInteger();

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        executedCount.set(0);
        System.exit(Runner.builder()
                .pool(Pool.named("outer"), 2)
                .pool(Pool.named("middle"), 2)
                .pool(Pool.named("inner"), 2)
                .build()
                .run(factory())
                .exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.Factory
    public static Task factory() {
        executedCount.set(0);

        return sequential("NestedParallelDeadlockTest")
                .parallel(
                        "root",
                        Pool.named("outer"),
                        outer -> outer.parallel(
                                        "mid1",
                                        Pool.named("middle"),
                                        middle -> middle.parallel(
                                                        "leaf1",
                                                        Pool.named("inner"),
                                                        inner -> inner.step("l1-1", NestedParallelDeadlockTest::work)
                                                                .step("l1-2", NestedParallelDeadlockTest::work))
                                                .parallel(
                                                        "leaf2",
                                                        Pool.named("inner"),
                                                        inner -> inner.step("l2-1", NestedParallelDeadlockTest::work)
                                                                .step("l2-2", NestedParallelDeadlockTest::work)))
                                .parallel(
                                        "mid2",
                                        Pool.named("middle"),
                                        middle -> middle.parallel(
                                                        "leaf3",
                                                        Pool.named("inner"),
                                                        inner -> inner.step("l3-1", NestedParallelDeadlockTest::work)
                                                                .step("l3-2", NestedParallelDeadlockTest::work))
                                                .parallel(
                                                        "leaf4",
                                                        Pool.named("inner"),
                                                        inner -> inner.step("l4-1", NestedParallelDeadlockTest::work)
                                                                .step("l4-2", NestedParallelDeadlockTest::work))))
                .step("validate", () -> assertThat(executedCount.get()).isEqualTo(EXPECTED_EXECUTIONS))
                .build();
    }

    private static void work() throws InterruptedException {
        executedCount.incrementAndGet();
        Thread.sleep(5L);
    }
}

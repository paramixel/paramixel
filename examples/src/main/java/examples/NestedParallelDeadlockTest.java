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
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.sequential;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Context;
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

    private static final String ID_L1_1 = "l1-1";
    private static final String ID_L1_2 = "l1-2";
    private static final String ID_L2_1 = "l2-1";
    private static final String ID_L2_2 = "l2-2";
    private static final String ID_L3_1 = "l3-1";
    private static final String ID_L3_2 = "l3-2";
    private static final String ID_L4_1 = "l4-1";
    private static final String ID_L4_2 = "l4-2";
    private static final String ID_VALIDATE = "validate";

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

        return sequential(NestedParallelDeadlockTest.class, "NestedParallelDeadlockTest")
                .child(parallel("root", Pool.named("outer"))
                        .child(parallel("mid1", Pool.named("middle"))
                                .child(parallel("leaf1", Pool.named("inner"))
                                        .step(ID_L1_1)
                                        .step(ID_L1_2))
                                .child(parallel("leaf2", Pool.named("inner"))
                                        .step(ID_L2_1)
                                        .step(ID_L2_2)))
                        .child(parallel("mid2", Pool.named("middle"))
                                .child(parallel("leaf3", Pool.named("inner"))
                                        .step(ID_L3_1)
                                        .step(ID_L3_2))
                                .child(parallel("leaf4", Pool.named("inner"))
                                        .step(ID_L4_1)
                                        .step(ID_L4_2))))
                .step(ID_VALIDATE)
                .build();
    }

    /**
     * Runs one leaf body.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_L1_1)
    public static void leafOneOne(final Context context) throws InterruptedException {
        work();
    }

    /**
     * Runs one leaf body.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_L1_2)
    public static void leafOneTwo(final Context context) throws InterruptedException {
        work();
    }

    /**
     * Runs one leaf body.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_L2_1)
    public static void leafTwoOne(final Context context) throws InterruptedException {
        work();
    }

    /**
     * Runs one leaf body.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_L2_2)
    public static void leafTwoTwo(final Context context) throws InterruptedException {
        work();
    }

    /**
     * Runs one leaf body.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_L3_1)
    public static void leafThreeOne(final Context context) throws InterruptedException {
        work();
    }

    /**
     * Runs one leaf body.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_L3_2)
    public static void leafThreeTwo(final Context context) throws InterruptedException {
        work();
    }

    /**
     * Runs one leaf body.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_L4_1)
    public static void leafFourOne(final Context context) throws InterruptedException {
        work();
    }

    /**
     * Runs one leaf body.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_L4_2)
    public static void leafFourTwo(final Context context) throws InterruptedException {
        work();
    }

    /**
     * Confirms every leaf ran.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_VALIDATE)
    public static void validate(final Context context) {
        assertThat(executedCount.get()).isEqualTo(EXPECTED_EXECUTIONS);
    }

    private static void work() throws InterruptedException {
        executedCount.incrementAndGet();
        Thread.sleep(5L);
    }
}

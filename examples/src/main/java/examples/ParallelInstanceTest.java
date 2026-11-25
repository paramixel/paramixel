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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Context;
import org.paramixel.api.Parallel;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Sequential;
import org.paramixel.api.Task;

/**
 * Demonstrates that parallel siblings reading one shared fixture are concurrent calls on the same object.
 *
 * <p>Paramixel does not synchronize hooks: the fixture decides what is safe. The fixture is published with
 * {@code bind}, so it is one object captured at authoring time and readable by every descendant, while each parallel
 * sibling keeps its own context scope. Each method records the fixture identity and meets at a barrier sized to the
 * pool capacity, proving both that the calls overlap and that they all target the same object.
 */
public class ParallelInstanceTest {

    private static final String ID_NOOP = "noop";
    private static final String ID_METHOD_1 = "method1";
    private static final String ID_METHOD_2 = "method2";
    private static final String ID_METHOD_3 = "method3";
    private static final String ID_VALIDATE = "validate";

    private static final String FIXTURE = "fixture";

    private static final int METHOD_COUNT = 3;
    private static final int PARALLELISM = METHOD_COUNT;

    private static final AtomicInteger testCount = new AtomicInteger();
    private static final AtomicInteger concurrentCount = new AtomicInteger();
    private static final AtomicInteger maxConcurrentCount = new AtomicInteger();
    private static final Set<Integer> instanceIds = ConcurrentHashMap.newKeySet();
    private static volatile CountDownLatch latch = new CountDownLatch(1);

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        resetCounts();
        System.exit(Runner.builder()
                .pool(Pool.named("methods"), PARALLELISM)
                .build()
                .run(factory())
                .exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.TaskFactory
    public static Task factory() {
        resetCounts();

        return Sequential.builder(ParallelInstanceTest.class, "ParallelInstanceTest")
                .bind(FIXTURE, new ParallelInstanceTest())
                .before(ID_NOOP)
                .step(Parallel.builder("parallel-methods", Pool.named("methods"))
                        .step(ID_METHOD_1)
                        .step(ID_METHOD_2)
                        .step(ID_METHOD_3))
                .after(Sequential.builder("cleanup")
                        .independent()
                        .step(ID_VALIDATE)
                        .step(ID_NOOP))
                .build();
    }

    /**
     * A lifecycle hook that deliberately does nothing; concurrency is measured in the parallel methods.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_NOOP)
    public static void noop(final Context context) {
        // Intentionally empty
    }

    /**
     * Runs the first parallel method.
     *
     * @param context the execution context
     * @throws InterruptedException if interrupted while waiting
     */
    @Paramixel.Id(ID_METHOD_1)
    public static void methodOne(final Context context) throws InterruptedException {
        context.require(FIXTURE, ParallelInstanceTest.class).method();
    }

    /**
     * Runs the second parallel method.
     *
     * @param context the execution context
     * @throws InterruptedException if interrupted while waiting
     */
    @Paramixel.Id(ID_METHOD_2)
    public static void methodTwo(final Context context) throws InterruptedException {
        context.require(FIXTURE, ParallelInstanceTest.class).method();
    }

    /**
     * Runs the third parallel method.
     *
     * @param context the execution context
     * @throws InterruptedException if interrupted while waiting
     */
    @Paramixel.Id(ID_METHOD_3)
    public static void methodThree(final Context context) throws InterruptedException {
        context.require(FIXTURE, ParallelInstanceTest.class).method();
    }

    /**
     * Verifies that all methods shared one fixture and overlapped.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_VALIDATE)
    public static void validate(final Context context) {
        verify();
    }

    /**
     * A lifecycle hook body that does nothing.
     */
    public void noopBody() {
        // Intentionally empty
    }

    /**
     * Records overlap and waits until every parallel sibling arrived.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void method() throws InterruptedException {
        final var currentLatch = latch;
        instanceIds.add(System.identityHashCode(this));
        final var current = concurrentCount.incrementAndGet();
        maxConcurrentCount.accumulateAndGet(current, Math::max);
        testCount.incrementAndGet();
        currentLatch.countDown();
        currentLatch.await();
        concurrentCount.decrementAndGet();
    }

    private static void verify() {
        assertThat(testCount.get()).isEqualTo(METHOD_COUNT);
        assertThat(concurrentCount.get()).isZero();
        assertThat(maxConcurrentCount.get()).isGreaterThan(1);
        assertThat(maxConcurrentCount.get()).isLessThanOrEqualTo(PARALLELISM);
        assertThat(instanceIds).hasSize(1);
    }

    private static void resetCounts() {
        latch = new CountDownLatch(PARALLELISM);
        testCount.set(0);
        concurrentCount.set(0);
        maxConcurrentCount.set(0);
        instanceIds.clear();
    }
}

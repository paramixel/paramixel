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
import static org.paramixel.api.Context.withInstance;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Configuration;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Step;

/**
 * Demonstrates parallel method execution on a single instance to verify thread safety.
 * Verifies that all methods execute exactly once, that concurrent method count drops
 * to zero on completion, and that peak concurrency exceeds 1 (proving true parallelism
 * on the same instance). Also verifies that all parallel methods received the same
 * instance object by collecting identity hash codes.
 */
public class ParallelInstanceTest {

    private static final int METHOD_COUNT = 3;
    private static final int PARALLELISM = METHOD_COUNT;

    private static final AtomicInteger testCount = new AtomicInteger();
    private static final AtomicInteger concurrentCount = new AtomicInteger();
    private static final AtomicInteger maxConcurrentCount = new AtomicInteger();
    private static final AtomicInteger expectedConcurrency = new AtomicInteger();
    private static volatile CountDownLatch latch = new CountDownLatch(1);
    private static final Set<Integer> instanceIds = ConcurrentHashMap.newKeySet();

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        resetCounts();
        Runner.defaultRunner().runAndExit(factory());
    }

    /**
     * Builds an instance tree with a parallel body that runs multiple methods
     * concurrently on a single instance.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        resetCounts();

        var testName = ParallelInstanceTest.class.getName();

        var parallelMethods = Parallel.<ParallelInstanceTest>builder("parallel-methods")
                .parallelism(PARALLELISM)
                .child(Step.of("method1()", withInstance(ParallelInstanceTest.class, ParallelInstanceTest::method1)))
                .child(Step.of("method2()", withInstance(ParallelInstanceTest.class, ParallelInstanceTest::method2)))
                .child(Step.of("method3()", withInstance(ParallelInstanceTest.class, ParallelInstanceTest::method3)));

        return Scope.builder(testName)
                .body(Instance.builder(testName, ParallelInstanceTest::new)
                        .body(Scope.<ParallelInstanceTest>builder("lifecycle")
                                .before(Step.of(
                                        "before()",
                                        withInstance(ParallelInstanceTest.class, ParallelInstanceTest::before)))
                                .body(parallelMethods.build())
                                .after(Step.of(
                                        "after()",
                                        withInstance(ParallelInstanceTest.class, ParallelInstanceTest::after)))
                                .build())
                        .build())
                .after(Step.of("validate", ignored -> validate()))
                .build();
    }

    public ParallelInstanceTest() {
        // Intentionally empty
    }

    public void before() {
        // Intentionally empty — concurrency is measured in parallel methods, not lifecycle hooks
    }

    public void method1() throws InterruptedException {
        var currentLatch = latch;
        instanceIds.add(System.identityHashCode(this));
        enter();
        testCount.incrementAndGet();
        currentLatch.countDown();
        currentLatch.await();
        exit();
    }

    public void method2() throws InterruptedException {
        var currentLatch = latch;
        instanceIds.add(System.identityHashCode(this));
        enter();
        testCount.incrementAndGet();
        currentLatch.countDown();
        currentLatch.await();
        exit();
    }

    public void method3() throws InterruptedException {
        var currentLatch = latch;
        instanceIds.add(System.identityHashCode(this));
        enter();
        testCount.incrementAndGet();
        currentLatch.countDown();
        currentLatch.await();
        exit();
    }

    public void after() {
        // Intentionally empty — concurrency is measured in parallel methods, not lifecycle hooks
    }

    private void enter() {
        int current = concurrentCount.incrementAndGet();
        maxConcurrentCount.accumulateAndGet(current, Math::max);
    }

    private void exit() {
        concurrentCount.decrementAndGet();
    }

    public static void validate() {
        var expected = expectedConcurrency.get();
        assertThat(testCount.get()).isEqualTo(METHOD_COUNT);
        assertThat(concurrentCount.get()).isEqualTo(0);
        if (expected > 1) {
            assertThat(maxConcurrentCount.get()).isGreaterThan(1);
        } else {
            assertThat(maxConcurrentCount.get()).isEqualTo(1);
        }
        assertThat(maxConcurrentCount.get()).isLessThanOrEqualTo(expected);
        assertThat(instanceIds).hasSize(1);
    }

    private static void resetCounts() {
        var configuredParallelism = Integer.getInteger(Configuration.RUNNER_PARALLELISM, PARALLELISM);
        var effectiveParallelism = Math.max(1, Math.min(PARALLELISM, configuredParallelism));
        expectedConcurrency.set(effectiveParallelism);
        latch = new CountDownLatch(effectiveParallelism);
        testCount.set(0);
        concurrentCount.set(0);
        maxConcurrentCount.set(0);
        instanceIds.clear();
    }
}

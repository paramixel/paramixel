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
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Spec;
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
    private static final CountDownLatch latch = new CountDownLatch(METHOD_COUNT);
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
    public static Spec<?> factory() {
        resetCounts();

        var testName = ParallelInstanceTest.class.getName();

        var parallelMethods = Parallel.<ParallelInstanceTest>of("parallel-methods")
                .parallelism(PARALLELISM)
                .child("method1()", ParallelInstanceTest::method1)
                .child("method2()", ParallelInstanceTest::method2)
                .child("method3()", ParallelInstanceTest::method3);

        return Lifecycle.of(testName)
                .child(Instance.of(testName, ParallelInstanceTest::new)
                        .child(Lifecycle.<ParallelInstanceTest>of("lifecycle")
                                .before("before()", ParallelInstanceTest::before)
                                .child(parallelMethods)
                                .after("after()", ParallelInstanceTest::after)
                                .resolve()))
                .after(Step.of("validate", ignored -> validate()));
    }

    public ParallelInstanceTest() {
        // Intentionally empty
    }

    public void before() {
        // Intentionally empty — concurrency is measured in parallel methods, not lifecycle hooks
    }

    public void method1() throws InterruptedException {
        instanceIds.add(System.identityHashCode(this));
        enter();
        testCount.incrementAndGet();
        latch.countDown();
        latch.await();
        exit();
    }

    public void method2() throws InterruptedException {
        instanceIds.add(System.identityHashCode(this));
        enter();
        testCount.incrementAndGet();
        latch.countDown();
        latch.await();
        exit();
    }

    public void method3() throws InterruptedException {
        instanceIds.add(System.identityHashCode(this));
        enter();
        testCount.incrementAndGet();
        latch.countDown();
        latch.await();
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
        assertThat(testCount.get()).isEqualTo(METHOD_COUNT);
        assertThat(concurrentCount.get()).isEqualTo(0);
        assertThat(maxConcurrentCount.get()).isGreaterThan(1);
        assertThat(maxConcurrentCount.get()).isLessThanOrEqualTo(PARALLELISM);
        assertThat(instanceIds).hasSize(1);
    }

    private static void resetCounts() {
        testCount.set(0);
        concurrentCount.set(0);
        maxConcurrentCount.set(0);
        instanceIds.clear();
    }
}

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
import static org.paramixel.api.Tasks.forInstance;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates that parallel siblings sharing one owned instance are concurrent calls on the same object.
 *
 * <p>Paramixel does not synchronize typed callbacks: the fixture decides what is safe. Here each parallel method records
 * the instance identity and meets at a barrier sized to the pool capacity, proving both that the methods overlap and
 * that they all target the same instance.
 */
public class ParallelInstanceTest {

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
    @Paramixel.Factory
    public static Task factory() {
        resetCounts();

        return forInstance("ParallelInstanceTest", ParallelInstanceTest::new)
                .before(ParallelInstanceTest::noop)
                .parallel(
                        "parallel-methods",
                        Pool.named("methods"),
                        methods -> methods.step("method1", ParallelInstanceTest::method)
                                .step("method2", ParallelInstanceTest::method)
                                .step("method3", ParallelInstanceTest::method))
                .after(ParallelInstanceTest::noop)
                .after(ParallelInstanceTest::validate)
                .build();
    }

    /**
     * No-argument constructor used by the framework.
     */
    public ParallelInstanceTest() {
        // Intentionally empty
    }

    /**
     * A lifecycle hook that deliberately does nothing; concurrency is measured in the parallel methods.
     */
    public void noop() {
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

    /**
     * Verifies that all methods shared one instance and overlapped.
     */
    public static void validate() {
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

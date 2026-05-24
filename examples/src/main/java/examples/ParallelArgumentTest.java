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
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Spec;
import org.paramixel.api.action.Step;

/**
 * Demonstrates parallel argument execution with bounded parallelism. Verifies that
 * all tests execute exactly once, that concurrent argument count drops to zero on
 * completion, and that peak concurrency stays within the configured limit.
 */
public class ParallelArgumentTest {

    private static final int ARGUMENT_COUNT = 5;
    private static final int PARALLELISM = 4;
    private static final int TEST_COUNT_PER_ARGUMENT = 3;

    private static final AtomicInteger testCount = new AtomicInteger();
    private static final AtomicInteger concurrentCount = new AtomicInteger();
    private static final AtomicInteger maxConcurrentCount = new AtomicInteger();

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
     * Builds a parallel argument tree with per-argument before/after callbacks.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Spec<?> factory() {
        resetCounts();

        var testName = ParallelArgumentTest.class.getName();

        var parallel = Parallel.of(testName).parallelism(PARALLELISM);
        for (int i = 0; i < ARGUMENT_COUNT; i++) {
            String argumentValue = "string-" + i;

            var tests = Sequential.<ParallelArgumentTest>of(argumentValue)
                    .independent()
                    .child("test()", ParallelArgumentTest::test)
                    .child("test()", ParallelArgumentTest::test)
                    .child("test()", ParallelArgumentTest::test);

            parallel.child(Instance.of(argumentValue, ParallelArgumentTest::new)
                    .child(Lifecycle.<ParallelArgumentTest>of("lifecycle")
                            .before("before()", ParallelArgumentTest::before)
                            .child(tests)
                            .after("after()", ParallelArgumentTest::after)));
        }

        return Lifecycle.of(testName).child(parallel).after(Step.of("validate", ignored -> validate()));
    }

    public ParallelArgumentTest() {
        // Intentionally empty
    }

    public void before() {
        int current = concurrentCount.incrementAndGet();
        maxConcurrentCount.accumulateAndGet(current, Math::max);
    }

    public void test() {
        testCount.incrementAndGet();
    }

    public void after() {
        concurrentCount.decrementAndGet();
    }

    public static void validate() {
        assertThat(testCount.get()).isEqualTo(ARGUMENT_COUNT * TEST_COUNT_PER_ARGUMENT);
        assertThat(concurrentCount.get()).isEqualTo(0);
        assertThat(maxConcurrentCount.get()).isGreaterThan(0);
        assertThat(maxConcurrentCount.get()).isLessThanOrEqualTo(PARALLELISM);
    }

    private static void resetCounts() {
        testCount.set(0);
        concurrentCount.set(0);
        maxConcurrentCount.set(0);
    }
}

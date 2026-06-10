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
import static org.paramixel.api.action.Instance.instance;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Demonstrates independent sequential argument execution where arguments run one at a time.
 * Verifies that all tests execute exactly once and that max concurrency never exceeds 1.
 */
public class IndependentArgumentTest {

    private static final int ARGUMENT_COUNT = 5;
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
     * Builds a sequential argument tree with per-argument before/after callbacks.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        resetCounts();

        var testName = IndependentArgumentTest.class.getName();

        var arguments = sequential(testName).independent();

        for (int i = 0; i < ARGUMENT_COUNT; i++) {
            String argumentValue = "string-" + i;

            var tests = sequential(argumentValue)
                    .child(step("test()", withInstance(IndependentArgumentTest.class, IndependentArgumentTest::test)))
                    .child(step("test()", withInstance(IndependentArgumentTest.class, IndependentArgumentTest::test)))
                    .child(step("test()", withInstance(IndependentArgumentTest.class, IndependentArgumentTest::test)));

            arguments.child(instance(argumentValue, IndependentArgumentTest::new)
                    .body(scope("lifecycle")
                            .before(step(
                                    "before()",
                                    withInstance(IndependentArgumentTest.class, IndependentArgumentTest::before)))
                            .body(tests)
                            .after(step(
                                    "after()",
                                    withInstance(IndependentArgumentTest.class, IndependentArgumentTest::after)))));
        }

        return scope(testName)
                .body(arguments)
                .after(step("validate", ignored -> validate()))
                .build();
    }

    public IndependentArgumentTest() {
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
        assertThat(maxConcurrentCount.get()).isEqualTo(1);
    }

    private static void resetCounts() {
        testCount.set(0);
        concurrentCount.set(0);
        maxConcurrentCount.set(0);
    }
}

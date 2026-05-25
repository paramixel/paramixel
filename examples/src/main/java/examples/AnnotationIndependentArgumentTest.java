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
import org.paramixel.api.AnnotationResolver;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Spec;
import org.paramixel.api.action.Step;

/**
 * Demonstrates independent sequential argument execution using annotation-based
 * method references. Arguments run one at a time. Verifies that all tests execute
 * exactly once and that max concurrency never exceeds 1.
 */
public class AnnotationIndependentArgumentTest {

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
     * Builds a sequential argument tree using annotation-based method references.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Spec<?> factory() {
        resetCounts();

        var annotationResolver = AnnotationResolver.create(AnnotationIndependentArgumentTest.class);

        var testName = AnnotationIndependentArgumentTest.class.getName();

        var arguments = Sequential.of(testName).independent();

        for (int i = 0; i < ARGUMENT_COUNT; i++) {
            String argumentValue = "string-" + i;

            var lifecycle = Lifecycle.of(argumentValue)
                    .before(annotationResolver.byId("before"))
                    .child(Sequential.of("tests")
                            .child(annotationResolver.byId("test"))
                            .child(annotationResolver.byId("test"))
                            .child(annotationResolver.byId("test")))
                    .after(annotationResolver.byId("after"));

            arguments.child(Instance.of(argumentValue, AnnotationIndependentArgumentTest::new)
                    .independent()
                    .child(lifecycle));
        }

        return Lifecycle.of(testName).child(arguments).after(Step.of("validate", ignored -> validate()));
    }

    public AnnotationIndependentArgumentTest() {
        // Intentionally empty
    }

    /**
     * Per-argument before callback.
     */
    @Paramixel.Id("before")
    public void before() {
        int current = concurrentCount.incrementAndGet();
        maxConcurrentCount.accumulateAndGet(current, Math::max);
    }

    /**
     * Per-argument test step.
     */
    @Paramixel.Id("test")
    public void test() {
        testCount.incrementAndGet();
    }

    /**
     * Per-argument after callback.
     */
    @Paramixel.Id("after")
    public void after() {
        concurrentCount.decrementAndGet();
    }

    /**
     * Validates that all counts match expectations.
     */
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

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
import static org.paramixel.api.action.Instance.instance;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.AnnotationResolver;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Demonstrates dependent argument execution using annotation-based method references.
 * Arguments run one at a time. Verifies that each argument waits for the previous
 * argument to complete, that all tests execute exactly once, and that max concurrency
 * never exceeds 1.
 */
public class AnnotationDependentArgumentTest {

    private static final int ARGUMENT_COUNT = 5;
    private static final int TEST_COUNT_PER_ARGUMENT = 3;

    private static final AtomicInteger beforeCount = new AtomicInteger();
    private static final AtomicInteger testCount = new AtomicInteger();
    private static final AtomicInteger afterCount = new AtomicInteger();
    private static final AtomicInteger concurrentCount = new AtomicInteger();
    private static final AtomicInteger maxConcurrentCount = new AtomicInteger();
    private static final AtomicInteger nextExpectedArgument = new AtomicInteger();

    private final int argumentIndex;

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
    public static Action factory() {
        resetCounts();

        var annotationResolver = AnnotationResolver.create(AnnotationDependentArgumentTest.class);

        var testName = AnnotationDependentArgumentTest.class.getName();

        var arguments = sequential(testName).dependent();

        for (int i = 0; i < ARGUMENT_COUNT; i++) {
            int argumentIndex = i;
            var argumentValue = "string-" + i;

            var lifecycle = scope(argumentValue)
                    .before(annotationResolver.byId("before"))
                    .body(sequential("tests")
                            .child(annotationResolver.byId("test"))
                            .child(annotationResolver.byId("test"))
                            .child(annotationResolver.byId("test")))
                    .after(annotationResolver.byId("after"));

            arguments.child(instance(argumentValue, () -> new AnnotationDependentArgumentTest(argumentIndex))
                    .body(lifecycle));
        }

        return scope(testName)
                .body(arguments)
                .after(step("validate", ignored -> validate()))
                .build();
    }

    private AnnotationDependentArgumentTest(final int argumentIndex) {
        this.argumentIndex = argumentIndex;
    }

    /**
     * Per-argument before callback.
     */
    @Paramixel.Id("before")
    public void before() {
        int current = concurrentCount.incrementAndGet();
        maxConcurrentCount.accumulateAndGet(current, Math::max);
        beforeCount.incrementAndGet();
        assertThat(nextExpectedArgument.get()).isEqualTo(argumentIndex);
    }

    /**
     * Per-argument test step.
     */
    @Paramixel.Id("test")
    public void test() {
        assertThat(concurrentCount.get()).isEqualTo(1);
        testCount.incrementAndGet();
    }

    /**
     * Per-argument after callback.
     */
    @Paramixel.Id("after")
    public void after() {
        assertThat(nextExpectedArgument.get()).isEqualTo(argumentIndex);
        afterCount.incrementAndGet();
        nextExpectedArgument.incrementAndGet();
        concurrentCount.decrementAndGet();
    }

    /**
     * Validates that all counts match expectations.
     */
    public static void validate() {
        assertThat(beforeCount.get()).isEqualTo(ARGUMENT_COUNT);
        assertThat(testCount.get()).isEqualTo(ARGUMENT_COUNT * TEST_COUNT_PER_ARGUMENT);
        assertThat(afterCount.get()).isEqualTo(ARGUMENT_COUNT);
        assertThat(concurrentCount.get()).isEqualTo(0);
        assertThat(maxConcurrentCount.get()).isEqualTo(1);
        assertThat(nextExpectedArgument.get()).isEqualTo(ARGUMENT_COUNT);
    }

    private static void resetCounts() {
        beforeCount.set(0);
        testCount.set(0);
        afterCount.set(0);
        concurrentCount.set(0);
        maxConcurrentCount.set(0);
        nextExpectedArgument.set(0);
    }
}

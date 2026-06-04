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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Each;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

/**
 * Demonstrates {@code Each.parallel()} as syntactic sugar over a hand-rolled
 * parameterization for-loop. Produces the same action tree as
 * {@link ParallelArgumentTest} but with less boilerplate.
 */
public class ParallelEachExample {

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
     * Builds a parallel argument tree using {@code Each.parallel()} instead of
     * a for-loop.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        resetCounts();
        var testName = ParallelEachExample.class.getName();
        var arguments =
                IntStream.range(0, ARGUMENT_COUNT).mapToObj(i -> "string-" + i).toList();

        return Scope.builder(testName)
                .body(Each.parallel(
                                "arguments",
                                arguments,
                                value -> Instance.builder(value, ParallelEachExample::new)
                                        .body(Scope.<ParallelEachExample>builder("lifecycle")
                                                .before(Step.of(
                                                        "before()",
                                                        withInstance(
                                                                ParallelEachExample.class,
                                                                ParallelEachExample::before)))
                                                .body(Sequence.builder("tests")
                                                        .child(Step.of(
                                                                "test()",
                                                                withInstance(
                                                                        ParallelEachExample.class,
                                                                        ParallelEachExample::test)))
                                                        .child(Step.of(
                                                                "test()",
                                                                withInstance(
                                                                        ParallelEachExample.class,
                                                                        ParallelEachExample::test)))
                                                        .child(Step.of(
                                                                "test()",
                                                                withInstance(
                                                                        ParallelEachExample.class,
                                                                        ParallelEachExample::test)))
                                                        .build())
                                                .after(Step.of(
                                                        "after()",
                                                        withInstance(
                                                                ParallelEachExample.class, ParallelEachExample::after)))
                                                .build())
                                        .build())
                        .parallelism(PARALLELISM))
                .after(Step.of("validate", ignored -> validate()))
                .build();
    }

    public ParallelEachExample() {
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

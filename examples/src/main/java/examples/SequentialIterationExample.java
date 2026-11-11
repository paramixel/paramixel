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
import static org.paramixel.api.action.Instance.of;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Demonstrates {@code Sequential.Builder.each(...)} for parameterized runs: one child per item, mapped at define
 * time. Produces the same action tree as the hand-rolled for-loop in {@link DependentArgumentTest}.
 */
public class SequentialIterationExample {

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
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds a dependent sequential argument tree with {@code each(...)} instead
     * of a for-loop.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        resetCounts();
        var testName = SequentialIterationExample.class.getName();
        var arguments =
                IntStream.range(0, ARGUMENT_COUNT).mapToObj(i -> "string-" + i).toList();

        return scope(testName)
                .body(sequential("arguments").each(arguments, value -> {
                    int index = Integer.parseInt(value.substring("string-".length()));
                    return of(value, SequentialIterationExample.class, () -> new SequentialIterationExample(index))
                            .body(scope("scope")
                                    .before(step(
                                            "before()",
                                            SequentialIterationExample.class,
                                            SequentialIterationExample::before))
                                    .body(sequential("tests")
                                            .child(step(
                                                    "test()",
                                                    SequentialIterationExample.class,
                                                    SequentialIterationExample::test))
                                            .child(step(
                                                    "test()",
                                                    SequentialIterationExample.class,
                                                    SequentialIterationExample::test))
                                            .child(step(
                                                    "test()",
                                                    SequentialIterationExample.class,
                                                    SequentialIterationExample::test)))
                                    .after(step(
                                            "after()",
                                            SequentialIterationExample.class,
                                            SequentialIterationExample::after)));
                }))
                .after(step("validate", ignored -> validate()))
                .build();
    }

    private SequentialIterationExample(final int argumentIndex) {
        this.argumentIndex = argumentIndex;
    }

    public void before() {
        int current = concurrentCount.incrementAndGet();
        maxConcurrentCount.accumulateAndGet(current, Math::max);
        beforeCount.incrementAndGet();
        assertThat(nextExpectedArgument.get()).isEqualTo(argumentIndex);
    }

    public void test() {
        assertThat(concurrentCount.get()).isEqualTo(1);
        testCount.incrementAndGet();
    }

    public void after() {
        assertThat(nextExpectedArgument.get()).isEqualTo(argumentIndex);
        afterCount.incrementAndGet();
        nextExpectedArgument.incrementAndGet();
        concurrentCount.decrementAndGet();
    }

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

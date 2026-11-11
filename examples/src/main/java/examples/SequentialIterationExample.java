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
import static org.paramixel.api.Tasks.sequential;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates a sequential matrix of fixtures, where every fixture runs its steps before the next fixture starts.
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
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        resetCounts();
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.Factory
    public static Task factory() {
        resetCounts();
        final var arguments =
                IntStream.range(0, ARGUMENT_COUNT).mapToObj(i -> "string-" + i).toList();

        return sequential("SequentialIterationExample")
                .each(
                        arguments,
                        value -> forInstance(
                                        value,
                                        () -> new SequentialIterationExample(
                                                Integer.parseInt(value.substring("string-".length()))))
                                .before(SequentialIterationExample::before)
                                .step("test", SequentialIterationExample::test)
                                .step("test", SequentialIterationExample::test)
                                .step("test", SequentialIterationExample::test)
                                .after(SequentialIterationExample::after))
                .after(SequentialIterationExample::validate)
                .build();
    }

    private SequentialIterationExample(final int argumentIndex) {
        this.argumentIndex = argumentIndex;
    }

    /**
     * Records fixture entry and verifies serialization.
     */
    public void before() {
        final var current = concurrentCount.incrementAndGet();
        maxConcurrentCount.accumulateAndGet(current, Math::max);
        beforeCount.incrementAndGet();
        assertThat(nextExpectedArgument.get()).isEqualTo(argumentIndex);
    }

    /**
     * Test body.
     */
    public void test() {
        assertThat(concurrentCount.get()).isEqualTo(1);
        testCount.incrementAndGet();
    }

    /**
     * Records fixture exit.
     */
    public void after() {
        afterCount.incrementAndGet();
        nextExpectedArgument.incrementAndGet();
        concurrentCount.decrementAndGet();
    }

    /**
     * Verifies the aggregate expectations.
     */
    public static void validate() {
        assertThat(beforeCount.get()).isEqualTo(ARGUMENT_COUNT);
        assertThat(testCount.get()).isEqualTo(ARGUMENT_COUNT * TEST_COUNT_PER_ARGUMENT);
        assertThat(afterCount.get()).isEqualTo(ARGUMENT_COUNT);
        assertThat(concurrentCount.get()).isZero();
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

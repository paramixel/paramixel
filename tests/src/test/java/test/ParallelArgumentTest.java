/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies argument-level parallel execution.
 *
 * <p>This test sets a parallelism value on the argument supplier context and uses simple counters
 * to assert that lifecycle callbacks and test methods execute the expected number of times.
 */
public class ParallelArgumentTest {

    /** Number of arguments supplied by {@link #arguments(ArgumentsCollector)}. */
    private static final int ARGUMENT_COUNT = 6;

    /** Number of times {@link #beforeAll(ArgumentContext)} is invoked. */
    private static final AtomicInteger beforeAllCount = new AtomicInteger(0);

    /** Number of {@code @Paramixel.Test} invocations across all arguments and methods. */
    private static final AtomicInteger testCount = new AtomicInteger(0);

    /** Number of times {@link #afterAll(ArgumentContext)} is invoked. */
    private static final AtomicInteger afterAllCount = new AtomicInteger(0);

    /**
     * Supplies arguments and configures argument-level parallelism.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.setParallelism(2);
        for (int i = 0; i < ARGUMENT_COUNT; i++) {
            collector.addArgument("String " + i);
        }
    }

    /**
     * Asserts that the class context is provided.
     *
     * @param context for the current class
     */
    @Paramixel.Initialize
    public void initialize(final ClassContext context) {
        assertThat(context).isNotNull();
    }

    /**
     * Runs once per argument before tests for that argument.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isNotNull();
        beforeAllCount.incrementAndGet();
    }

    /**
     * First test method; includes a small random delay to increase scheduling interleavings.
     *
     * @param context for the current argument
     * @throws InterruptedException if the sleep is interrupted
     */
    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext context) throws InterruptedException {
        assertThat(context.getArgument()).isNotNull();
        Thread.sleep(ThreadLocalRandom.current().nextInt(0, 25));
        testCount.incrementAndGet();
    }

    /**
     * Second test method; includes a small random delay to increase scheduling interleavings.
     *
     * @param context for the current argument
     * @throws InterruptedException if the sleep is interrupted
     */
    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext context) throws InterruptedException {
        assertThat(context.getArgument()).isNotNull();
        Thread.sleep(ThreadLocalRandom.current().nextInt(0, 25));
        testCount.incrementAndGet();
    }

    /**
     * Runs once per argument after tests for that argument.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isNotNull();
        afterAllCount.incrementAndGet();
    }

    /**
     * Performs end-of-class assertions on lifecycle callback counts.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        assertThat(context).isNotNull();
        assertThat(beforeAllCount.get()).as("beforeAll count").isEqualTo(ARGUMENT_COUNT);
        assertThat(testCount.get()).as("test count").isEqualTo(ARGUMENT_COUNT * 2);
        assertThat(afterAllCount.get()).as("afterAll count").isEqualTo(ARGUMENT_COUNT);
    }
}

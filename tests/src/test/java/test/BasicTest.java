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

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies the basic Paramixel lifecycle for a single test class.
 *
 * <p>This test supplies a fixed number of arguments and executes multiple test methods per
 * argument. It asserts that each lifecycle callback is invoked the expected number of times and
 * that each argument index is observed.
 */
public class BasicTest {

    /** Number of arguments supplied by {@link #arguments(ArgumentsCollector)}. */
    private static final int ARGUMENT_COUNT = 5;

    /** Number of {@code @Paramixel.Test} methods executed per argument. */
    private static final int TEST_COUNT = 2;

    /** Number of times {@link #initialize(ClassContext)} is invoked. */
    private static final AtomicInteger initializeCount = new AtomicInteger(0);

    /** Number of times {@link #beforeAll(ArgumentContext)} is invoked. */
    private static final AtomicInteger beforeAllCount = new AtomicInteger(0);

    /** Number of times {@link #beforeEach(ArgumentContext)} is invoked. */
    private static final AtomicInteger beforeEachCount = new AtomicInteger(0);

    /** Number of {@code @Paramixel.Test} invocations across all arguments and methods. */
    private static final AtomicInteger testCount = new AtomicInteger(0);

    /** Number of times {@link #afterEach(ArgumentContext)} is invoked. */
    private static final AtomicInteger afterEachCount = new AtomicInteger(0);

    /** Number of times {@link #afterAll(ArgumentContext)} is invoked. */
    private static final AtomicInteger afterAllCount = new AtomicInteger(0);

    /** Number of times {@link #finalize(ClassContext)} is invoked. */
    private static final AtomicInteger finalizeCount = new AtomicInteger(0);

    /** Tracks the set of observed argument indexes across lifecycle callbacks. */
    private static final Set<Integer> argumentIndexes = new ConcurrentSkipListSet<>();

    /**
     * Supplies a deterministic set of arguments used by all test methods.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        for (int i = 0; i < ARGUMENT_COUNT; i++) {
            collector.addArgument("String " + i);
        }
    }

    /**
     * Initializes per-class state.
     *
     * @param context for the current test class
     */
    @Paramixel.Initialize
    public void initialize(final ClassContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getTestClass()).isNotNull();
        initializeCount.incrementAndGet();
    }

    /**
     * Runs once per argument before any {@code @Paramixel.Test} methods for that argument.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isNotNull();
        argumentIndexes.add(context.getArgumentIndex());
        beforeAllCount.incrementAndGet();
    }

    /**
     * Runs before each {@code @Paramixel.Test} method invocation.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isNotNull();
        beforeEachCount.incrementAndGet();
    }

    /**
     * First test method executed for each argument.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isNotNull();
        testCount.incrementAndGet();
    }

    /**
     * Second test method executed for each argument.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isNotNull();
        testCount.incrementAndGet();
    }

    /**
     * Runs after each {@code @Paramixel.Test} method invocation.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isNotNull();
        afterEachCount.incrementAndGet();
    }

    /**
     * Runs once per argument after all {@code @Paramixel.Test} methods complete for that argument.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isNotNull();
        afterAllCount.incrementAndGet();
    }

    /**
     * Performs end-of-class assertions after all arguments and tests have completed.
     *
     * @param context for the current test class
     */
    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        assertThat(context).isNotNull();
        finalizeCount.incrementAndGet();

        assertThat(initializeCount.get()).as("initialize count").isEqualTo(1);
        assertThat(beforeAllCount.get()).as("beforeAll count").isEqualTo(ARGUMENT_COUNT);
        assertThat(beforeEachCount.get()).as("beforeEach count").isEqualTo(ARGUMENT_COUNT * TEST_COUNT);
        assertThat(testCount.get()).as("test count").isEqualTo(ARGUMENT_COUNT * TEST_COUNT);
        assertThat(afterEachCount.get()).as("afterEach count").isEqualTo(ARGUMENT_COUNT * TEST_COUNT);
        assertThat(afterAllCount.get()).as("afterAll count").isEqualTo(ARGUMENT_COUNT);
        assertThat(finalizeCount.get()).as("finalize count").isEqualTo(1);
        assertThat(argumentIndexes).hasSize(ARGUMENT_COUNT);
    }
}

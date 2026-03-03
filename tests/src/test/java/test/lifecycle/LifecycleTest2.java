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

package test.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

/**
 * Demonstrates a single test method lifecycle with argument supplier.
 */
@Paramixel.TestClass
public class LifecycleTest2 {

    private static final ConcurrentSkipListSet<Integer> seenArgumentIndices = new ConcurrentSkipListSet<>();

    private static final AtomicInteger initializeCount = new AtomicInteger(0);
    private static final AtomicInteger beforeAllCount = new AtomicInteger(0);
    private static final AtomicInteger beforeEachCount = new AtomicInteger(0);
    private static final AtomicInteger testCount = new AtomicInteger(0);
    private static final AtomicInteger afterEachCount = new AtomicInteger(0);
    private static final AtomicInteger afterAllCount = new AtomicInteger(0);
    private static final AtomicInteger finalizeCount = new AtomicInteger(0);

    /**
     * Supplies arguments for parameterized execution.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.addArguments("Argument 1", "Argument 2", "Argument 3");
    }

    /**
     * Initializes class-level resources.
     *
     * @param context the class context
     */
    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext context) {
        seenArgumentIndices.clear();
        initializeCount.set(0);
        beforeAllCount.set(0);
        beforeEachCount.set(0);
        testCount.set(0);
        afterEachCount.set(0);
        afterAllCount.set(0);
        finalizeCount.set(0);
        initializeCount.incrementAndGet();
    }

    /**
     * Executes before all tests for each argument.
     *
     * @param context the argument context
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        beforeAllCount.incrementAndGet();
        seenArgumentIndices.add(context.getArgumentIndex());
        assertThat(context.getArgument(String.class)).startsWith("Argument ");
    }

    /**
     * Executes before each test invocation.
     *
     * @param context the argument context
     */
    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext context) {
        beforeEachCount.incrementAndGet();
        assertThat(context.getArgument(String.class)).startsWith("Argument ");
    }

    /**
     * Executes the test method.
     *
     * @param context the argument context
     */
    @Paramixel.Test
    public void testMethod(final @NonNull ArgumentContext context) {
        testCount.incrementAndGet();
        assertThat(context.getArgument(String.class)).startsWith("Argument ");
    }

    /**
     * Executes after each test invocation.
     *
     * @param context the argument context
     */
    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext context) {
        afterEachCount.incrementAndGet();
        assertThat(context.getArgument(String.class)).startsWith("Argument ");
    }

    /**
     * Executes after all tests for each argument.
     *
     * @param context the argument context
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        afterAllCount.incrementAndGet();
        assertThat(context.getArgument(String.class)).startsWith("Argument ");
    }

    /**
     * Finalizes class-level resources after all tests complete.
     *
     * @param context the class context
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext context) {
        finalizeCount.incrementAndGet();

        assertThat(initializeCount.get()).isEqualTo(1);
        assertThat(beforeAllCount.get()).isEqualTo(3);
        assertThat(beforeEachCount.get()).isEqualTo(3);
        assertThat(testCount.get()).isEqualTo(3);
        assertThat(afterEachCount.get()).isEqualTo(3);
        assertThat(afterAllCount.get()).isEqualTo(3);
        assertThat(finalizeCount.get()).isEqualTo(1);
        assertThat(seenArgumentIndices).containsExactly(0, 1, 2);
    }
}

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
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Sequential;
import org.paramixel.api.Task;

/**
 * Demonstrates a dependent sequence of fixtures: each fixture runs its three tests in order, and the next fixture starts
 * only after the previous one finished, so at most one fixture is active at a time.
 *
 * <p>The parameter is authoring data — {@code bind} publishes the index and the {@code before} hook builds the fixture
 * from it, per branch. Nothing is captured in a lambda, so every hook is an ordinary method a reader can find by name.
 */
public class DependentArgumentTest {

    private static final String ID_BEFORE = "before";
    private static final String ID_TEST = "test";
    private static final String ID_AFTER = "after";
    private static final String ID_VALIDATE = "validate";

    private static final String INDEX = "index";
    private static final String FIXTURE = "fixture";

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
    @Paramixel.TaskFactory
    public static Task factory() {
        resetCounts();
        final var root = Sequential.builder(DependentArgumentTest.class, "DependentArgumentTest");
        for (var i = 0; i < ARGUMENT_COUNT; i++) {
            root.step(Sequential.builder("string-" + i)
                    .bind(INDEX, i)
                    .before(ID_BEFORE)
                    .step(ID_TEST)
                    .step(ID_TEST)
                    .step(ID_TEST)
                    .after(ID_AFTER));
        }
        return root.after(ID_VALIDATE).build();
    }

    private DependentArgumentTest(final int argumentIndex) {
        this.argumentIndex = argumentIndex;
    }

    /**
     * Builds this branch's fixture and records fixture entry.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_BEFORE)
    public static void beforeHook(final Context context) {
        final var fixture = new DependentArgumentTest(context.require(INDEX, Integer.class));
        context.put(FIXTURE, fixture);
        fixture.before();
    }

    /**
     * Runs one test body of this branch.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST)
    public static void testHook(final Context context) {
        context.require(FIXTURE, DependentArgumentTest.class).test();
    }

    /**
     * Records fixture exit, if the fixture was created.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_AFTER)
    public static void afterHook(final Context context) {
        context.find(FIXTURE, DependentArgumentTest.class).ifPresent(DependentArgumentTest::after);
    }

    /**
     * Verifies the aggregate expectations.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_VALIDATE)
    public static void validateHook(final Context context) {
        validate();
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
        assertThat(nextExpectedArgument.get()).isEqualTo(argumentIndex);
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

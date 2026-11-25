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

package examples.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Sequential;
import org.paramixel.api.Task;

/**
 * Demonstrates one fixture shared by two sibling steps.
 *
 * <p>The two steps are siblings in a sequential chain, so they share one context scope: the fixture stored by the
 * {@code before} hook is the same object in both, and neither step has to be told about the other. The {@code after}
 * hook reads it back with {@code find} and closes it explicitly — the framework never closes anything.
 */
public class TestLifecycleTest implements AutoCloseable {

    private static final String ID_CREATE = "create";
    private static final String ID_TEST_ONE = "testOne";
    private static final String ID_TEST_TWO = "testTwo";
    private static final String ID_CLOSE = "close";

    private static final String FIXTURE = "fixture";

    private static final int EXPECTED_TEST_COUNT = 2;

    private static final AtomicInteger testCount = new AtomicInteger();

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.TaskFactory
    public static Task factory() {
        testCount.set(0);

        return Sequential.builder(TestLifecycleTest.class, "TestLifecycleTest")
                .before(ID_CREATE)
                .step(ID_TEST_ONE)
                .step(ID_TEST_TWO)
                .after(ID_CLOSE)
                .build();
    }

    /**
     * Creates the fixture for this attempt.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_CREATE)
    public static void create(final Context context) {
        context.put(FIXTURE, new TestLifecycleTest());
    }

    /**
     * First test body.
     */
    public void testOne() {
        testCount.incrementAndGet();
    }

    /**
     * Second test body.
     */
    public void testTwo() {
        testCount.incrementAndGet();
    }

    /**
     * Runs the first test step.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_ONE)
    public static void runTestOne(final Context context) {
        context.require(FIXTURE, TestLifecycleTest.class).testOne();
    }

    /**
     * Runs the second test step.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_TWO)
    public static void runTestTwo(final Context context) {
        context.require(FIXTURE, TestLifecycleTest.class).testTwo();
    }

    /**
     * Releases the fixture, if the attempt managed to create one.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_CLOSE)
    public static void releaseFixture(final Context context) {
        context.find(FIXTURE, TestLifecycleTest.class).ifPresent(TestLifecycleTest::close);
    }

    @Override
    public void close() {
        assertThat(testCount.get()).isEqualTo(EXPECTED_TEST_COUNT);
    }
}

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
 * Demonstrates one fixture shared by several steps, with setup before and teardown after.
 *
 * <p>The four hooks are siblings in one sequential chain, so they share a context scope: the fixture stored by
 * {@code setUp} is the same object in both steps and in {@code tearDown}. That is the whole mechanism — there is no
 * separate instance lifecycle to configure.
 */
public class SetUpTearDownLifecycleTest {

    private static final String ID_SET_UP = "setUp";
    private static final String ID_TEST_ONE = "testOne";
    private static final String ID_TEST_TWO = "testTwo";
    private static final String ID_TEAR_DOWN = "tearDown";

    private static final String FIXTURE = "fixture";

    private static final int EXPECTED_TEST_COUNT = 2;

    private static final AtomicInteger setUpCount = new AtomicInteger();
    private static final AtomicInteger testCount = new AtomicInteger();
    private static final AtomicInteger tearDownCount = new AtomicInteger();

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
        resetCounts();

        return Sequential.builder(SetUpTearDownLifecycleTest.class, "SetUpTearDownLifecycleTest")
                .before(ID_SET_UP)
                .step(ID_TEST_ONE)
                .step(ID_TEST_TWO)
                .after(ID_TEAR_DOWN)
                .build();
    }

    /**
     * Creates the fixture and runs setup on it.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_SET_UP)
    public static void setUp(final Context context) {
        final var fixture = new SetUpTearDownLifecycleTest();
        context.put(FIXTURE, fixture);
        fixture.setUp();
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
     * Verifies the lifecycle counts and releases the fixture.
     */
    public void tearDown() {
        tearDownCount.incrementAndGet();

        assertThat(setUpCount.get()).isEqualTo(1);
        assertThat(testCount.get()).isEqualTo(EXPECTED_TEST_COUNT);
        assertThat(tearDownCount.get()).isEqualTo(1);
    }

    /**
     * Runs the first test step.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_ONE)
    public static void runTestOne(final Context context) {
        context.require(FIXTURE, SetUpTearDownLifecycleTest.class).testOne();
    }

    /**
     * Runs the second test step.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_TWO)
    public static void runTestTwo(final Context context) {
        context.require(FIXTURE, SetUpTearDownLifecycleTest.class).testTwo();
    }

    /**
     * Tears the fixture down, if the attempt managed to create one.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEAR_DOWN)
    public static void tearDown(final Context context) {
        context.find(FIXTURE, SetUpTearDownLifecycleTest.class).ifPresent(SetUpTearDownLifecycleTest::tearDown);
    }

    /**
     * Records setup.
     */
    public void setUp() {
        setUpCount.incrementAndGet();
    }

    private static void resetCounts() {
        setUpCount.set(0);
        testCount.set(0);
        tearDownCount.set(0);
    }
}

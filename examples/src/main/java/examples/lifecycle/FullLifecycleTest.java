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
import static org.paramixel.api.Tasks.sequential;

import java.util.ArrayList;
import java.util.List;
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates static setup, fixture setup, per-test before/after, fixture teardown, and static teardown.
 *
 * <p>Each level is an occurrence with its own hooks: {@code before} and {@code after} belong to the task that declares
 * them, run in declaration order and reverse declaration order respectively, and a step with a body and its own hooks
 * is written as a child group with {@code .body(id)}. The fixture lives in the scope of the group that created it, so
 * every descendant resolves it by name.
 */
public class FullLifecycleTest {

    private static final String ID_STATIC_SET_UP = "staticSetUp";
    private static final String ID_SET_UP = "setUp";
    private static final String ID_BEFORE_EACH = "beforeEach";
    private static final String ID_TEST_ONE = "testOne";
    private static final String ID_TEST_TWO = "testTwo";
    private static final String ID_AFTER_EACH = "afterEach";
    private static final String ID_TEAR_DOWN = "tearDown";
    private static final String ID_STATIC_TEAR_DOWN = "staticTearDown";

    private static final String FIXTURE = "fixture";

    private static final List<String> callOrder = new ArrayList<>();

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds the lifecycle definition.
     *
     * @return the definition for this test
     */
    @Paramixel.Factory
    public static Task factory() {
        return sequential(FullLifecycleTest.class, "FullLifecycleTest")
                .before(ID_STATIC_SET_UP)
                .child(sequential("instance")
                        .before(ID_SET_UP)
                        .child(sequential("tests")
                                .child(sequential("testOne")
                                        .body(ID_TEST_ONE)
                                        .before(ID_BEFORE_EACH)
                                        .after(ID_AFTER_EACH))
                                .child(sequential("testTwo")
                                        .body(ID_TEST_TWO)
                                        .before(ID_BEFORE_EACH)
                                        .after(ID_AFTER_EACH)))
                        .after(ID_TEAR_DOWN))
                .after(ID_STATIC_TEAR_DOWN)
                .build();
    }

    /**
     * Clears the recorded call order.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_STATIC_SET_UP)
    public static void staticSetUpHook(final Context context) {
        staticSetUp();
    }

    /**
     * Creates the fixture and records fixture setup.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_SET_UP)
    public static void setUpHook(final Context context) {
        final var fixture = new FullLifecycleTest();
        context.put(FIXTURE, fixture);
        fixture.setUp();
    }

    /**
     * Records per-test setup.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_BEFORE_EACH)
    public static void beforeEachHook(final Context context) {
        context.require(FIXTURE, FullLifecycleTest.class).beforeEach();
    }

    /**
     * First test body.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_ONE)
    public static void testOneHook(final Context context) {
        context.require(FIXTURE, FullLifecycleTest.class).testOne();
    }

    /**
     * Second test body.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_TWO)
    public static void testTwoHook(final Context context) {
        context.require(FIXTURE, FullLifecycleTest.class).testTwo();
    }

    /**
     * Records per-test cleanup.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_AFTER_EACH)
    public static void afterEachHook(final Context context) {
        context.require(FIXTURE, FullLifecycleTest.class).afterEach();
    }

    /**
     * Records fixture teardown, if the fixture was created.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEAR_DOWN)
    public static void tearDownHook(final Context context) {
        context.find(FIXTURE, FullLifecycleTest.class).ifPresent(FullLifecycleTest::tearDown);
    }

    /**
     * Records static teardown and verifies the recorded order.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_STATIC_TEAR_DOWN)
    public static void staticTearDownHook(final Context context) {
        staticTearDown();
    }

    /**
     * Clears the recorded call order.
     */
    public static void staticSetUp() {
        callOrder.clear();
        callOrder.add("staticSetUp");
    }

    /**
     * Records fixture setup.
     */
    public void setUp() {
        callOrder.add("setUp");
    }

    /**
     * Records per-test setup.
     */
    public void beforeEach() {
        callOrder.add("beforeEach");
    }

    /**
     * First test body.
     */
    public void testOne() {
        callOrder.add("testOne");
    }

    /**
     * Second test body.
     */
    public void testTwo() {
        callOrder.add("testTwo");
    }

    /**
     * Records per-test cleanup.
     */
    public void afterEach() {
        callOrder.add("afterEach");
    }

    /**
     * Records fixture cleanup.
     */
    public void tearDown() {
        callOrder.add("tearDown");
    }

    /**
     * Verifies the recorded order.
     */
    public static void staticTearDown() {
        callOrder.add("staticTearDown");

        assertThat(callOrder)
                .containsExactly(
                        "staticSetUp",
                        "setUp",
                        "beforeEach",
                        "testOne",
                        "afterEach",
                        "beforeEach",
                        "testTwo",
                        "afterEach",
                        "tearDown",
                        "staticTearDown");
    }
}

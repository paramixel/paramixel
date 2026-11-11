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
import static org.paramixel.api.Tasks.forInstance;
import static org.paramixel.api.Tasks.sequential;

import java.util.ArrayList;
import java.util.List;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates static setup, per-instance setup, per-test before/after, instance teardown, and static teardown.
 *
 * <p>The nesting shows the 7.0 lifecycle model: {@code before} and {@code after} belong to the task that declares
 * them, run in declaration order and reverse declaration order respectively, and inner tasks inherit the outer
 * instance without repeating a class token.
 */
public class FullLifecycleTest {

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
        return sequential("FullLifecycleTest")
                .before(FullLifecycleTest::staticSetUp)
                .child(forInstance("instance", FullLifecycleTest::new)
                        .before(FullLifecycleTest::setUp)
                        .sequential(
                                "tests",
                                tests -> tests.step(
                                                "testOne",
                                                FullLifecycleTest::testOne,
                                                children -> children.before(FullLifecycleTest::beforeEach)
                                                        .after(FullLifecycleTest::afterEach))
                                        .step(
                                                "testTwo",
                                                FullLifecycleTest::testTwo,
                                                children -> children.before(FullLifecycleTest::beforeEach)
                                                        .after(FullLifecycleTest::afterEach)))
                        .after(FullLifecycleTest::tearDown))
                .after(FullLifecycleTest::staticTearDown)
                .build();
    }

    /**
     * No-argument constructor used by the framework.
     */
    public FullLifecycleTest() {
        // Intentionally empty
    }

    /**
     * Clears the recorded call order.
     */
    public static void staticSetUp() {
        callOrder.clear();
        callOrder.add("staticSetUp");
    }

    /**
     * Records per-instance setup.
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
     * Records per-instance cleanup.
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

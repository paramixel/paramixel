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

import java.util.ArrayList;
import java.util.List;
import org.paramixel.api.Context;
import org.paramixel.api.Parallel;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Sequential;
import org.paramixel.api.SequentialBuilder;
import org.paramixel.api.Task;

/**
 * Demonstrates the same lifecycle shape running in two independent branches of one parallel group.
 *
 * <p>Both branches are built from the same hooks and differ only in their names. Each branch is a direct child of the
 * parallel group, so each owns a context scope: the fixture and its call-order list are per branch, and parallel
 * execution cannot corrupt either one. That is what replaces a base class with overridable hooks — the hooks are
 * shared, the state is not.
 *
 * <p>A step with a body and its own lifecycle is written as a child group with {@code .body(id)}; the body runs
 * between that group's {@code before} and {@code after} hooks.
 */
public class NestedTest {

    private static final String ID_CREATE = "create";
    private static final String ID_BEFORE_EACH = "beforeEach";
    private static final String ID_TEST_ONE = "testOne";
    private static final String ID_TEST_TWO = "testTwo";
    private static final String ID_AFTER_EACH = "afterEach";
    private static final String ID_TEAR_DOWN = "tearDown";

    private static final String FIXTURE = "fixture";

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        System.exit(Runner.builder()
                .pool(Pool.named("examples"), 2)
                .build()
                .run(factory())
                .exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.TaskFactory
    public static Task factory() {
        return Parallel.builder(NestedTest.class, "NestedTest", Pool.named("examples"))
                .step(branch("Nested1"))
                .step(branch("Nested2"))
                .build();
    }

    /**
     * Builds one branch: the same hooks, a name of its own, and therefore a scope of its own.
     *
     * <p>The branch carries no anchor of its own: it is built bottom-up, and the ids resolve against the root's anchor
     * when the definition is built.
     *
     * @param name the branch name
     * @return the branch definition
     */
    private static SequentialBuilder branch(final String name) {
        return Sequential.builder(name)
                .before(ID_CREATE)
                .step(Sequential.builder("testOne")
                        .body(ID_TEST_ONE)
                        .before(ID_BEFORE_EACH)
                        .after(ID_AFTER_EACH))
                .step(Sequential.builder("testTwo")
                        .body(ID_TEST_TWO)
                        .before(ID_BEFORE_EACH)
                        .after(ID_AFTER_EACH))
                .after(ID_TEAR_DOWN);
    }

    /**
     * Creates the branch fixture and records the outer setup.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_CREATE)
    public static void create(final Context context) {
        final var fixture = new Fixture();
        context.put(FIXTURE, fixture);
        fixture.staticSetUp();
        fixture.setUp();
    }

    /**
     * Records per-test setup.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_BEFORE_EACH)
    public static void beforeEach(final Context context) {
        context.require(FIXTURE, Fixture.class).beforeEach();
    }

    /**
     * Runs the first test body.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_ONE)
    public static void testOne(final Context context) {
        context.require(FIXTURE, Fixture.class).testOne();
    }

    /**
     * Runs the second test body.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_TWO)
    public static void testTwo(final Context context) {
        context.require(FIXTURE, Fixture.class).testTwo();
    }

    /**
     * Records per-test cleanup.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_AFTER_EACH)
    public static void afterEach(final Context context) {
        context.require(FIXTURE, Fixture.class).afterEach();
    }

    /**
     * Records the outer teardown and verifies this branch's call order.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEAR_DOWN)
    public static void tearDown(final Context context) {
        context.find(FIXTURE, Fixture.class).ifPresent(fixture -> {
            fixture.tearDown();
            fixture.staticTearDown();
        });
    }

    /**
     * One branch's state: the recorded call order.
     */
    public static final class Fixture {

        private final List<String> callOrder = new ArrayList<>();

        /**
         * Creates the fixture.
         */
        public Fixture() {
            // Intentionally empty
        }

        /**
         * Clears the call order and records the outer setup.
         */
        public void staticSetUp() {
            callOrder.clear();
            callOrder.add("staticSetUp");
        }

        /**
         * Records setup.
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
         * Records cleanup.
         */
        public void tearDown() {
            callOrder.add("tearDown");
        }

        /**
         * Records the outer cleanup and verifies the order.
         */
        public void staticTearDown() {
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
}

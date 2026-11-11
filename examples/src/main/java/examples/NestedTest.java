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
import static org.paramixel.api.Tasks.parallel;

import java.util.ArrayList;
import java.util.List;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates the same lifecycle nested inside two independent branches of one parallel group.
 *
 * <p>Each branch owns its own fixture and its own call-order list, so parallel execution cannot corrupt either one.
 */
public class NestedTest {

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
    @Paramixel.Factory
    public static Task factory() {
        return parallel("NestedTest", Pool.named("examples"))
                .child(Nested1.factory())
                .child(Nested2.factory())
                .build();
    }

    /**
     * The shared lifecycle shape used by both nested branches.
     */
    public abstract static class Lifecycle {

        private final List<String> callOrder = new ArrayList<>();

        /**
         * Clears the call order.
         */
        public void staticSetUp() {
            callOrder.clear();
            callOrder.add("staticSetUp");
        }

        /**
         * Records instance setup.
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
         * Records instance cleanup.
         */
        public void tearDown() {
            callOrder.add("tearDown");
        }

        /**
         * Verifies the recorded order.
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

        /**
         * Builds the lifecycle definition for a concrete subclass.
         *
         * @param <S> the concrete lifecycle type
         * @param name the task name
         * @param factory the instance factory
         * @return the definition
         */
        protected static <S extends Lifecycle> Task definition(
                final String name, final org.paramixel.api.ThrowingSupplier<S> factory) {
            return org.paramixel.api.Tasks.sequential(name)
                    .child(forInstance(name, factory)
                            .before(Lifecycle::setUp)
                            .step(
                                    "testOne",
                                    Lifecycle::testOne,
                                    children -> children.before(Lifecycle::beforeEach)
                                            .after(Lifecycle::afterEach))
                            .step(
                                    "testTwo",
                                    Lifecycle::testTwo,
                                    children -> children.before(Lifecycle::beforeEach)
                                            .after(Lifecycle::afterEach))
                            .after(Lifecycle::tearDown))
                    .build();
        }
    }

    /**
     * First nested branch.
     */
    public static final class Nested1 extends Lifecycle {

        /**
         * No-argument constructor used by the framework.
         */
        public Nested1() {
            // Intentionally empty
        }

        /**
         * Builds the branch definition.
         *
         * @return the definition
         */
        public static Task factory() {
            return sequentialBranch("Nested1", Nested1::new);
        }
    }

    /**
     * Second nested branch.
     */
    public static final class Nested2 extends Lifecycle {

        /**
         * No-argument constructor used by the framework.
         */
        public Nested2() {
            // Intentionally empty
        }

        /**
         * Builds the branch definition.
         *
         * @return the definition
         */
        public static Task factory() {
            return sequentialBranch("Nested2", Nested2::new);
        }
    }

    private static <S extends Lifecycle> Task sequentialBranch(
            final String name, final org.paramixel.api.ThrowingSupplier<S> factory) {
        return Lifecycle.definition(name, factory);
    }
}

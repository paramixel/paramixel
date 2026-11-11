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
import static org.paramixel.api.action.Instance.of;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

import java.util.ArrayList;
import java.util.List;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Scope;

public class NestedTest {

    public static void main(final String[] args) {
        System.exit(Runner.builder()
                .build()
                .run(parallel(NestedTest.class.getName(), "examples")
                        .child(Nested1.factory())
                        .child(Nested2.factory())
                        .build())
                .exitCode());
    }

    public static class Nested1 {

        private static final List<String> callOrder = new ArrayList<>();

        public static void main(final String[] args) {
            System.exit(Runner.builder().build().run(factory()).exitCode());
        }

        @Paramixel.Factory
        public static Action factory() {
            return Scope.scope(Nested1.class.getName())
                    .before(step("staticSetUp()", context -> Nested1.staticSetUp()))
                    .body(of("Nested1", Nested1.class, Nested1::new)
                            .body(scope("scope")
                                    .before(step("setUp()", Nested1.class, Nested1::setUp))
                                    .body(sequential("tests")
                                            .child(scope("testOne")
                                                    .before(step("beforeEach()", Nested1.class, Nested1::beforeEach))
                                                    .body(step("testOne()", Nested1.class, Nested1::testOne))
                                                    .after(step("afterEach()", Nested1.class, Nested1::afterEach)))
                                            .child(scope("testTwo")
                                                    .before(step("beforeEach()", Nested1.class, Nested1::beforeEach))
                                                    .body(step("testTwo()", Nested1.class, Nested1::testTwo))
                                                    .after(step("afterEach()", Nested1.class, Nested1::afterEach))))
                                    .after(step("tearDown()", Nested1.class, Nested1::tearDown))))
                    .after(step("staticTearDown()", context -> Nested1.staticTearDown()))
                    .build();
        }

        public Nested1() {
            // Intentionally empty
        }

        public static void staticSetUp() {
            callOrder.clear();
            callOrder.add("staticSetUp");
        }

        public void setUp() {
            callOrder.add("setUp");
        }

        public void beforeEach() {
            callOrder.add("beforeEach");
        }

        public void testOne() {
            callOrder.add("testOne");
        }

        public void testTwo() {
            callOrder.add("testTwo");
        }

        public void afterEach() {
            callOrder.add("afterEach");
        }

        public void tearDown() {
            callOrder.add("tearDown");
        }

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

    public static class Nested2 {

        private static final List<String> callOrder = new ArrayList<>();

        public static void main(final String[] args) {
            System.exit(Runner.builder().build().run(factory()).exitCode());
        }

        @Paramixel.Factory
        public static Action factory() {
            return Scope.scope(Nested2.class.getName())
                    .before(step("staticSetUp()", context -> Nested2.staticSetUp()))
                    .body(of("Nested2", Nested2.class, Nested2::new)
                            .body(scope("scope")
                                    .before(step("setUp()", Nested2.class, Nested2::setUp))
                                    .body(sequential("tests")
                                            .child(scope("testOne")
                                                    .before(step("beforeEach()", Nested2.class, Nested2::beforeEach))
                                                    .body(step("testOne()", Nested2.class, Nested2::testOne))
                                                    .after(step("afterEach()", Nested2.class, Nested2::afterEach)))
                                            .child(scope("testTwo")
                                                    .before(step("beforeEach()", Nested2.class, Nested2::beforeEach))
                                                    .body(step("testTwo()", Nested2.class, Nested2::testTwo))
                                                    .after(step("afterEach()", Nested2.class, Nested2::afterEach))))
                                    .after(step("tearDown()", Nested2.class, Nested2::tearDown))))
                    .after(step("staticTearDown()", context -> Nested2.staticTearDown()))
                    .build();
        }

        public Nested2() {
            // Intentionally empty
        }

        public static void staticSetUp() {
            callOrder.clear();
            callOrder.add("staticSetUp");
        }

        public void setUp() {
            callOrder.add("setUp");
        }

        public void beforeEach() {
            callOrder.add("beforeEach");
        }

        public void testOne() {
            callOrder.add("testOne");
        }

        public void testTwo() {
            callOrder.add("testTwo");
        }

        public void afterEach() {
            callOrder.add("afterEach");
        }

        public void tearDown() {
            callOrder.add("tearDown");
        }

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
}

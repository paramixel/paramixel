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
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Spec;
import org.paramixel.api.action.Static;

public class NestedTest {

    public static void main(final String[] args) {
        Runner.defaultRunner()
                .runAndExit(Parallel.<NestedTest>of(NestedTest.class.getName())
                        .child(Nested1.factory())
                        .child(Nested2.factory()));
    }

    public static class Nested1 {

        private static final List<String> callOrder = new ArrayList<>();

        public static void main(final String[] args) {
            Runner.defaultRunner().runAndExit(factory());
        }

        @Paramixel.Factory
        public static Spec<?> factory() {
            return Static.of(Nested1.class.getName())
                    .before("staticSetUp()", Nested1::staticSetUp)
                    .child(Instance.of("Nested1", Nested1::new)
                            .child(Lifecycle.<Nested1>of("lifecycle")
                                    .before("setUp()", Nested1::setUp)
                                    .child(Lifecycle.<Nested1>of("testOne")
                                            .before("beforeEach()", Nested1::beforeEach)
                                            .child("testOne()", Nested1::testOne)
                                            .after("afterEach()", Nested1::afterEach))
                                    .child(Lifecycle.<Nested1>of("testTwo")
                                            .before("beforeEach()", Nested1::beforeEach)
                                            .child("testTwo()", Nested1::testTwo)
                                            .after("afterEach()", Nested1::afterEach))
                                    .after("tearDown()", Nested1::tearDown)))
                    .after("staticTearDown()", Nested1::staticTearDown);
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
            Runner.defaultRunner().runAndExit(factory());
        }

        @Paramixel.Factory
        public static Spec<?> factory() {
            return Static.of(Nested2.class.getName())
                    .before("staticSetUp()", Nested2::staticSetUp)
                    .child(Instance.of("Nested2", Nested2::new)
                            .child(Lifecycle.<Nested2>of("lifecycle")
                                    .before("setUp()", Nested2::setUp)
                                    .child(Lifecycle.<Nested2>of("testOne")
                                            .before("beforeEach()", Nested2::beforeEach)
                                            .child("testOne()", Nested2::testOne)
                                            .after("afterEach()", Nested2::afterEach))
                                    .child(Lifecycle.<Nested2>of("testTwo")
                                            .before("beforeEach()", Nested2::beforeEach)
                                            .child("testTwo()", Nested2::testTwo)
                                            .after("afterEach()", Nested2::afterEach))
                                    .after("tearDown()", Nested2::tearDown)))
                    .after("staticTearDown()", Nested2::staticTearDown);
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

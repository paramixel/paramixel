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
import static org.paramixel.api.Context.withInstance;

import java.util.ArrayList;
import java.util.List;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Static;
import org.paramixel.api.action.Step;

public class NestedTest {

    public static void main(final String[] args) {
        Runner.defaultRunner()
                .runAndExit(Parallel.<NestedTest>builder(NestedTest.class.getName())
                        .child(Nested1.factory())
                        .child(Nested2.factory())
                        .build());
    }

    public static class Nested1 {

        private static final List<String> callOrder = new ArrayList<>();

        public static void main(final String[] args) {
            Runner.defaultRunner().runAndExit(factory());
        }

        @Paramixel.Factory
        public static Action factory() {
            return Static.builder(Nested1.class.getName())
                    .before(Step.of("staticSetUp()", context -> Nested1.staticSetUp()))
                    .body(Instance.builder("Nested1", Nested1::new)
                            .body(Scope.<Nested1>builder("lifecycle")
                                    .before(Step.of("setUp()", withInstance(Nested1.class, Nested1::setUp)))
                                    .body(Sequence.builder("tests")
                                            .child(Scope.<Nested1>builder("testOne")
                                                    .before(Step.of(
                                                            "beforeEach()",
                                                            withInstance(Nested1.class, Nested1::beforeEach)))
                                                    .body(Step.of(
                                                            "testOne()", withInstance(Nested1.class, Nested1::testOne)))
                                                    .after(Step.of(
                                                            "afterEach()",
                                                            withInstance(Nested1.class, Nested1::afterEach)))
                                                    .build())
                                            .child(Scope.<Nested1>builder("testTwo")
                                                    .before(Step.of(
                                                            "beforeEach()",
                                                            withInstance(Nested1.class, Nested1::beforeEach)))
                                                    .body(Step.of(
                                                            "testTwo()", withInstance(Nested1.class, Nested1::testTwo)))
                                                    .after(Step.of(
                                                            "afterEach()",
                                                            withInstance(Nested1.class, Nested1::afterEach)))
                                                    .build())
                                            .build())
                                    .after(Step.of("tearDown()", withInstance(Nested1.class, Nested1::tearDown)))
                                    .build())
                            .build())
                    .after(Step.of("staticTearDown()", context -> Nested1.staticTearDown()))
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
            Runner.defaultRunner().runAndExit(factory());
        }

        @Paramixel.Factory
        public static Action factory() {
            return Static.builder(Nested2.class.getName())
                    .before(Step.of("staticSetUp()", context -> Nested2.staticSetUp()))
                    .body(Instance.builder("Nested2", Nested2::new)
                            .body(Scope.<Nested2>builder("lifecycle")
                                    .before(Step.of("setUp()", withInstance(Nested2.class, Nested2::setUp)))
                                    .body(Sequence.builder("tests")
                                            .child(Scope.<Nested2>builder("testOne")
                                                    .before(Step.of(
                                                            "beforeEach()",
                                                            withInstance(Nested2.class, Nested2::beforeEach)))
                                                    .body(Step.of(
                                                            "testOne()", withInstance(Nested2.class, Nested2::testOne)))
                                                    .after(Step.of(
                                                            "afterEach()",
                                                            withInstance(Nested2.class, Nested2::afterEach)))
                                                    .build())
                                            .child(Scope.<Nested2>builder("testTwo")
                                                    .before(Step.of(
                                                            "beforeEach()",
                                                            withInstance(Nested2.class, Nested2::beforeEach)))
                                                    .body(Step.of(
                                                            "testTwo()", withInstance(Nested2.class, Nested2::testTwo)))
                                                    .after(Step.of(
                                                            "afterEach()",
                                                            withInstance(Nested2.class, Nested2::afterEach)))
                                                    .build())
                                            .build())
                                    .after(Step.of("tearDown()", withInstance(Nested2.class, Nested2::tearDown)))
                                    .build())
                            .build())
                    .after(Step.of("staticTearDown()", context -> Nested2.staticTearDown()))
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

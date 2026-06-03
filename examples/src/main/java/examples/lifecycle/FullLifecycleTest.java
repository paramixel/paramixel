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
import static org.paramixel.api.Context.withInstance;

import java.util.ArrayList;
import java.util.List;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Static;
import org.paramixel.api.action.Step;

public class FullLifecycleTest {

    private static final List<String> callOrder = new ArrayList<>();

    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Action factory() {
        return Static.builder(FullLifecycleTest.class.getName())
                .before(Step.of("staticSetUp()", context -> staticSetUp()))
                .body(Instance.builder("FullLifecycleTest", FullLifecycleTest::new)
                        .body(Scope.<FullLifecycleTest>builder("lifecycle")
                                .before(Step.of(
                                        "setUp()", withInstance(FullLifecycleTest.class, FullLifecycleTest::setUp)))
                                .body(Sequence.builder("tests")
                                        .child(Scope.<FullLifecycleTest>builder("testOne")
                                                .before(Step.of(
                                                        "beforeEach()",
                                                        withInstance(
                                                                FullLifecycleTest.class,
                                                                FullLifecycleTest::beforeEach)))
                                                .body(Step.of(
                                                        "testOne()",
                                                        withInstance(
                                                                FullLifecycleTest.class, FullLifecycleTest::testOne)))
                                                .after(Step.of(
                                                        "afterEach()",
                                                        withInstance(
                                                                FullLifecycleTest.class, FullLifecycleTest::afterEach)))
                                                .build())
                                        .child(Scope.<FullLifecycleTest>builder("testTwo")
                                                .before(Step.of(
                                                        "beforeEach()",
                                                        withInstance(
                                                                FullLifecycleTest.class,
                                                                FullLifecycleTest::beforeEach)))
                                                .body(Step.of(
                                                        "testTwo()",
                                                        withInstance(
                                                                FullLifecycleTest.class, FullLifecycleTest::testTwo)))
                                                .after(Step.of(
                                                        "afterEach()",
                                                        withInstance(
                                                                FullLifecycleTest.class, FullLifecycleTest::afterEach)))
                                                .build())
                                        .build())
                                .after(Step.of(
                                        "tearDown()",
                                        withInstance(FullLifecycleTest.class, FullLifecycleTest::tearDown)))
                                .build())
                        .build())
                .after(Step.of("staticTearDown()", context -> staticTearDown()))
                .build();
    }

    public FullLifecycleTest() {
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

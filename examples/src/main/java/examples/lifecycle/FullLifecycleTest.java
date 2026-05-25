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

import java.util.ArrayList;
import java.util.List;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Spec;
import org.paramixel.api.action.Static;

public class FullLifecycleTest {

    private static final List<String> callOrder = new ArrayList<>();

    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Spec<?> factory() {
        return Static.of(FullLifecycleTest.class.getName())
                .before("staticSetUp()", FullLifecycleTest::staticSetUp)
                .child(Instance.of("FullLifecycleTest", FullLifecycleTest::new)
                        .child(Lifecycle.<FullLifecycleTest>of("lifecycle")
                                .before("setUp()", FullLifecycleTest::setUp)
                                .child(Lifecycle.<FullLifecycleTest>of("testOne")
                                        .before("beforeEach()", FullLifecycleTest::beforeEach)
                                        .child("testOne()", FullLifecycleTest::testOne)
                                        .after("afterEach()", FullLifecycleTest::afterEach))
                                .child(Lifecycle.<FullLifecycleTest>of("testTwo")
                                        .before("beforeEach()", FullLifecycleTest::beforeEach)
                                        .child("testTwo()", FullLifecycleTest::testTwo)
                                        .after("afterEach()", FullLifecycleTest::afterEach))
                                .after("tearDown()", FullLifecycleTest::tearDown)))
                .after("staticTearDown()", FullLifecycleTest::staticTearDown);
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

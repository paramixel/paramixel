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
import static org.paramixel.api.action.Instance.instance;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

public class SetUpTearDownLifecycleTest {

    private static final int EXPECTED_TEST_COUNT = 2;

    private static final AtomicInteger setUpCount = new AtomicInteger();
    private static final AtomicInteger testCount = new AtomicInteger();
    private static final AtomicInteger tearDownCount = new AtomicInteger();

    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Action factory() {
        resetCounts();

        return instance("FullLifecycleTest", SetUpTearDownLifecycleTest::new)
                .body(scope("lifecycle")
                        .before(step(
                                "setUp()",
                                withInstance(SetUpTearDownLifecycleTest.class, SetUpTearDownLifecycleTest::setUp)))
                        .body(sequential("tests")
                                .child(step(
                                        "testOne()",
                                        withInstance(
                                                SetUpTearDownLifecycleTest.class, SetUpTearDownLifecycleTest::testOne)))
                                .child(step(
                                        "testTwo()",
                                        withInstance(
                                                SetUpTearDownLifecycleTest.class,
                                                SetUpTearDownLifecycleTest::testTwo))))
                        .after(step(
                                "tearDown()",
                                withInstance(SetUpTearDownLifecycleTest.class, SetUpTearDownLifecycleTest::tearDown))))
                .build();
    }

    public SetUpTearDownLifecycleTest() {
        // Intentionally empty
    }

    public void setUp() {
        setUpCount.incrementAndGet();
    }

    public void testOne() {
        testCount.incrementAndGet();
    }

    public void testTwo() {
        testCount.incrementAndGet();
    }

    public void tearDown() {
        tearDownCount.incrementAndGet();

        assertThat(setUpCount.get()).isEqualTo(1);
        assertThat(testCount.get()).isEqualTo(EXPECTED_TEST_COUNT);
        assertThat(tearDownCount.get()).isEqualTo(1);
    }

    private static void resetCounts() {
        setUpCount.set(0);
        testCount.set(0);
        tearDownCount.set(0);
    }
}

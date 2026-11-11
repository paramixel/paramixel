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

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates one owned instance shared by several steps, with setup before and teardown after.
 */
public class SetUpTearDownLifecycleTest {

    private static final int EXPECTED_TEST_COUNT = 2;

    private static final AtomicInteger setUpCount = new AtomicInteger();
    private static final AtomicInteger testCount = new AtomicInteger();
    private static final AtomicInteger tearDownCount = new AtomicInteger();

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.Factory
    public static Task factory() {
        resetCounts();

        return forInstance("FullLifecycleTest", SetUpTearDownLifecycleTest::new)
                .before(SetUpTearDownLifecycleTest::setUp)
                .step("testOne", SetUpTearDownLifecycleTest::testOne)
                .step("testTwo", SetUpTearDownLifecycleTest::testTwo)
                .after(SetUpTearDownLifecycleTest::tearDown)
                .build();
    }

    /**
     * No-argument constructor used by the framework.
     */
    public SetUpTearDownLifecycleTest() {
        // Intentionally empty
    }

    /**
     * Records setup.
     */
    public void setUp() {
        setUpCount.incrementAndGet();
    }

    /**
     * First test body.
     */
    public void testOne() {
        testCount.incrementAndGet();
    }

    /**
     * Second test body.
     */
    public void testTwo() {
        testCount.incrementAndGet();
    }

    /**
     * Verifies the lifecycle counts.
     */
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

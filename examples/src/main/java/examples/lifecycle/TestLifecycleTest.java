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

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Spec;

public class TestLifecycleTest implements AutoCloseable {

    private static final int EXPECTED_TEST_COUNT = 2;

    private static final AtomicInteger testCount = new AtomicInteger();

    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Spec<?> factory() {
        resetCounts();

        return Instance.of("FullLifecycleTest", TestLifecycleTest::new)
                .child("testOne()", TestLifecycleTest::testOne)
                .child("testTwo()", TestLifecycleTest::testTwo);
    }

    public TestLifecycleTest() {
        // Intentionally empty
    }

    public void testOne() {
        testCount.incrementAndGet();
    }

    public void testTwo() {
        testCount.incrementAndGet();
    }

    private static void resetCounts() {
        testCount.set(0);
    }

    @Override
    public void close() {
        assertThat(testCount.get()).isEqualTo(EXPECTED_TEST_COUNT);
    }
}

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
 * Demonstrates that the framework does not implicitly close an owned instance.
 *
 * <p>{@code SimpleTest} implements {@link AutoCloseable}, yet {@code close()} runs only because an explicit
 * {@code after} callback calls it. Implicit closing was deliberately removed in 7.0: it makes ownership ambiguous when
 * a fixture is shared with an enclosing scope.
 */
public class SimpleTest implements AutoCloseable {

    private static final AtomicInteger testCount = new AtomicInteger();

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
        testCount.set(0);

        return forInstance("SimpleTest", SimpleTest::new)
                .step("testOne", SimpleTest::testOne)
                .after(SimpleTest::close)
                .build();
    }

    /**
     * No-argument constructor used by the framework.
     */
    public SimpleTest() {
        // Intentionally empty
    }

    /**
     * Test body.
     */
    public void testOne() {
        testCount.incrementAndGet();
    }

    @Override
    public void close() {
        assertThat(testCount.get()).isEqualTo(1);
    }
}

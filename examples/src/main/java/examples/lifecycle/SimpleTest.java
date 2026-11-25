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
import static org.paramixel.api.Tasks.sequential;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates that the framework does not implicitly close a fixture held in the context.
 *
 * <p>The fixture implements {@link AutoCloseable}, yet {@code close()} runs only because an explicit {@code after}
 * hook calls it. The framework never probes for {@link AutoCloseable}: it drops the reference when the scope ends and
 * nothing more. The {@code after} hook uses {@code find}, so a partially completed {@code before} cannot mask the
 * original failure.
 */
public class SimpleTest implements AutoCloseable {

    private static final String ID_CREATE = "create";
    private static final String ID_TEST_ONE = "testOne";
    private static final String ID_CLOSE = "close";

    private static final String FIXTURE = "fixture";

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

        return sequential(SimpleTest.class, "SimpleTest")
                .before(ID_CREATE)
                .step(ID_TEST_ONE)
                .after(ID_CLOSE)
                .build();
    }

    /**
     * Creates the fixture for this attempt.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_CREATE)
    public static void create(final Context context) {
        context.put(FIXTURE, new SimpleTest());
    }

    /**
     * Test body.
     */
    public void testOne() {
        testCount.incrementAndGet();
    }

    /**
     * Test step.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_ONE)
    public static void testOne(final Context context) {
        context.require(FIXTURE, SimpleTest.class).testOne();
    }

    /**
     * Releases the fixture, if the attempt managed to create one.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_CLOSE)
    public static void releaseFixture(final Context context) {
        context.find(FIXTURE, SimpleTest.class).ifPresent(SimpleTest::close);
    }

    @Override
    public void close() {
        assertThat(testCount.get()).isEqualTo(1);
    }
}

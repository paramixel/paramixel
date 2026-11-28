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

package examples.isolated;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Context;
import org.paramixel.api.Parallel;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Sequential;
import org.paramixel.api.SequentialBuilder;
import org.paramixel.api.Task;

/**
 * Demonstrates an exclusive logical lock as a serialization boundary inside parallel work.
 *
 * <p>Two {@code isolated} groups share the lock name {@code db-lock}, so their subtrees never overlap, while unrelated
 * groups run concurrently. A lock is not a pool permit: a waiter occupies no worker thread and consumes no capacity of
 * the surrounding pool.
 *
 * <p>Each scenario is a direct child of a parallel group and therefore owns a context scope, so its fixture counter is
 * per scenario and no shared mutable state is needed.
 */
public class IsolatedTest {

    private static final String ID_SET_UP = "setUp";
    private static final String ID_RUN = "run";
    private static final String ID_TEAR_DOWN = "tearDown";
    private static final String ID_PEAK = "peak";

    private static final String FIXTURE = "fixture";

    private static final AtomicInteger insideLock = new AtomicInteger();
    private static final AtomicInteger peakInsideLock = new AtomicInteger();

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds a definition that exercises exclusive serialization.
     *
     * @return the definition for this test
     */
    @Paramixel.TaskFactory
    public static Task factory() {
        insideLock.set(0);
        peakInsideLock.set(0);

        final var groupOne = Parallel.builder("group-1-tests", Pool.named("group-tests"));
        for (final var name : List.of("test-1a", "test-1b")) {
            groupOne.step(scenario(name));
        }
        final var groupTwo = Parallel.builder("group-2-tests", Pool.named("group-tests"));
        for (final var name : List.of("test-2a", "test-2b")) {
            groupTwo.step(scenario(name));
        }

        return Parallel.builder(IsolatedTest.class, "IsolatedTest", Pool.named("groups"))
                .step(Sequential.builder("serialized-group-1")
                        .isolated("db-lock")
                        .step(groupOne))
                .step(Sequential.builder("serialized-group-2")
                        .isolated("db-lock")
                        .step(groupTwo))
                .after(ID_PEAK)
                .build();
    }

    private static SequentialBuilder scenario(final String name) {
        return Sequential.builder(name).before(ID_SET_UP).step(ID_RUN).after(ID_TEAR_DOWN);
    }

    /**
     * Creates this scenario's fixture.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_SET_UP)
    public static void setUp(final Context context) {
        final var scenario = new Scenario();
        context.put(FIXTURE, scenario);
        scenario.setUp();
    }

    /**
     * Runs the scenario body while the enclosing group's exclusive lock is held.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_RUN)
    public static void run(final Context context) throws InterruptedException {
        context.require(FIXTURE, Scenario.class).run();
    }

    /**
     * Verifies the scenario's fixture state.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEAR_DOWN)
    public static void tearDown(final Context context) {
        context.find(FIXTURE, Scenario.class).ifPresent(Scenario::tearDown);
    }

    /**
     * Confirms the run actually serialized.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_PEAK)
    public static void peak(final Context context) {
        assertThat(peakInsideLock.get()).isPositive();
    }

    /**
     * One scenario's fixture.
     */
    public static final class Scenario {

        private final AtomicInteger counter = new AtomicInteger();

        /**
         * Creates a scenario.
         */
        public Scenario() {
            // Intentionally empty
        }

        /**
         * Resets the fixture state.
         */
        public void setUp() {
            counter.set(0);
        }

        /**
         * Runs while holding the exclusive lock of the enclosing group.
         *
         * @throws InterruptedException if interrupted
         */
        public void run() throws InterruptedException {
            final var active = insideLock.incrementAndGet();
            peakInsideLock.accumulateAndGet(active, Math::max);
            try {
                counter.incrementAndGet();
                Thread.sleep(10L);
            } finally {
                insideLock.decrementAndGet();
            }
        }

        /**
         * Verifies the fixture state.
         */
        public void tearDown() {
            assertThat(counter.get()).isEqualTo(1);
        }
    }
}

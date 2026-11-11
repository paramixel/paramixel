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
import static org.paramixel.api.Tasks.forInstance;
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.sequential;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates an exclusive logical lock as a serialization boundary inside parallel work.
 *
 * <p>Two {@code isolated} groups share the lock name {@code db-lock}, so their subtrees never overlap, while unrelated
 * groups run concurrently. A lock is not a pool permit: a waiter occupies no worker thread and consumes no capacity of
 * the surrounding pool.
 *
 * <p>Each scenario owns its own fixture, so the counter is per scenario and no shared mutable state is needed.
 */
public class IsolatedTest {

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
    @Paramixel.Factory
    public static Task factory() {
        insideLock.set(0);
        peakInsideLock.set(0);

        return parallel("IsolatedTest", Pool.named("groups"))
                .child(sequential("serialized-group-1")
                        .isolated("db-lock")
                        .parallel(
                                "group-1-tests",
                                Pool.named("group-tests"),
                                tests -> tests.each(java.util.List.of("test-1a", "test-1b"), name -> scenario(name))))
                .child(sequential("serialized-group-2")
                        .isolated("db-lock")
                        .parallel(
                                "group-2-tests",
                                Pool.named("group-tests"),
                                tests -> tests.each(java.util.List.of("test-2a", "test-2b"), name -> scenario(name))))
                .after(() -> {
                    assertThat(peakInsideLock.get()).isPositive();
                })
                .build();
    }

    private static org.paramixel.api.TaskBuilder<?, ?> scenario(final String name) {
        return forInstance(name, Scenario::new)
                .before(Scenario::setUp)
                .step("run", Scenario::run)
                .after(Scenario::tearDown);
    }

    /**
     * One owned fixture per scenario.
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

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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * Coverage for waking threads parked in {@code managedJoin}.
 *
 * <p>Every coordination descriptor waits for its children inside {@code managedJoin}. Without an
 * explicit wakeup on enqueue and completion, such a thread only notices progress when its backoff
 * park expires (up to one second). These tests pin that the wakeup path is exercised and that a
 * tree with waiters still completes correctly.
 */
@DisplayName("Scheduler waiter wakeup")
@Timeout(60)
class SchedulerWaiterWakeupTest {

    @Test
    @DisplayName("coordination nodes wake parked waiters as children complete")
    void coordinationNodesWakeParkedWaiters() {
        var executed = new AtomicInteger();
        var inner = Sequential.builder("inner");
        for (var i = 0; i < 8; i++) {
            var name = "inner-" + i;
            inner.child(Step.of(name, context -> executed.incrementAndGet()));
        }
        var root = Parallel.builder("root")
                .parallelism(2)
                .child(inner.build())
                .child(Sequential.builder("other")
                        .child(Step.of("other-step", context -> executed.incrementAndGet()))
                        .build())
                .build();

        var descriptor = new DescriptorBuilder().discover(root);
        var scheduler = new Scheduler(2, 32);
        try {
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(),
                    new Listener() {},
                    descriptor,
                    scheduler,
                    new InstanceHolder());
            scheduler.executeDescriptor(descriptor, context, ExecutionMode.RUN);

            assertThat(executed.get()).isEqualTo(9);
            assertThat(scheduler.parkedWaiterWakeups())
                    .as("waiters must be woken rather than waiting for their park to expire")
                    .isPositive();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("a tree with waiters completes through the public runner")
    void treeWithWaitersCompletes() {
        var executed = new AtomicInteger();
        var sequential = Sequential.builder("chain");
        for (var i = 0; i < 200; i++) {
            var name = "step-" + i;
            sequential.child(Step.of(name, context -> executed.incrementAndGet()));
        }
        var configuration = Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM, "4",
                Configuration.SCHEDULER_QUEUE_CAPACITY, "256",
                Configuration.ANSI, "false"));

        var result = Runner.builder()
                .configuration(configuration)
                .listener(new Listener() {})
                .build()
                .run(sequential.build());

        assertThat(result.isPassed()).isTrue();
        assertThat(executed.get()).isEqualTo(200);
    }
}

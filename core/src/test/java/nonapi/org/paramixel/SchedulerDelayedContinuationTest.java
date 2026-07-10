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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler delayed continuations")
@SuppressWarnings("removal")
class SchedulerDelayedContinuationTest {

    private static final Duration DELAY = Duration.ofMillis(500);

    @Test
    @Timeout(5)
    @DisplayName("delayed Loop continuation leaves worker available for independent descriptor")
    void delayedLoopContinuationLeavesWorkerAvailableForIndependentDescriptor() throws Exception {
        var firstIterationStarted = new CountDownLatch(1);
        var iterations = new AtomicInteger();
        var loop = Loop.builder("loop")
                .body(Step.of("body", context -> {
                    iterations.incrementAndGet();
                    firstIterationStarted.countDown();
                }))
                .maxIterations(2)
                .delay(DELAY)
                .build();
        var independent = Step.of("independent", context -> {});
        var root = new DescriptorBuilder()
                .discover(
                        Sequence.builder("root").child(loop).child(independent).build());
        root.freeze();
        var loopDescriptor = (MutableDescriptor) root.children().get(0);
        var independentDescriptor = (MutableDescriptor) root.children().get(1);
        var scheduler = new Scheduler(1);
        try {
            var context = context(root, scheduler);
            var loopFuture = scheduler.schedule(loopDescriptor, ExecutionMode.RUN, context);

            assertThat(firstIterationStarted.await(1, TimeUnit.SECONDS)).isTrue();

            var independentFuture = scheduler.schedule(independentDescriptor, ExecutionMode.RUN, context);
            assertThat(independentFuture.get(250, TimeUnit.MILLISECONDS).isPassed())
                    .isTrue();
            assertThat(iterations).hasValue(1);
            assertThat(loopFuture.get(2, TimeUnit.SECONDS).isPassed()).isTrue();
            assertThat(iterations).hasValue(2);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("close finalizes Loop waiting for delayed continuation")
    void closeFinalizesLoopWaitingForDelayedContinuation() throws Exception {
        var firstIterationStarted = new CountDownLatch(1);
        var iterations = new AtomicInteger();
        var loop = Loop.builder("loop")
                .body(Step.of("body", context -> {
                    iterations.incrementAndGet();
                    firstIterationStarted.countDown();
                }))
                .maxIterations(2)
                .delay(DELAY)
                .build();
        var root = new DescriptorBuilder().discover(loop);
        root.freeze();
        var scheduler = new Scheduler(1);
        try {
            var future = scheduler.schedule(root, ExecutionMode.RUN, context(root, scheduler));

            assertThat(firstIterationStarted.await(1, TimeUnit.SECONDS)).isTrue();
            scheduler.close();

            assertThat(future.isDone()).isTrue();
            assertThat(iterations).hasValue(1);
        } finally {
            scheduler.close();
        }
    }

    private static ConcreteContext context(final MutableDescriptor root, final Scheduler scheduler) {
        return new ConcreteContext(
                Configuration.defaultConfiguration(), new Listener() {}, root, scheduler, new InstanceHolder());
    }
}

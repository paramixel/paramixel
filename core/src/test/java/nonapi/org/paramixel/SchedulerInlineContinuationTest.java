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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.FailException;

/**
 * Behavioural coverage for continuations executed inline on the completing thread.
 *
 * <p>Inline continuation removes a queue submission and a hand-off per child completion. These
 * tests pin the two properties that change could break: sequential ordering and per-child status
 * propagation at a tree size large enough that a lost or mis-ordered continuation is visible.
 */
@DisplayName("Scheduler inline continuation")
@Timeout(60)
class SchedulerInlineContinuationTest {

    @Test
    @DisplayName("a long sequential chain runs every child exactly once, in order")
    void longSequentialChainRunsEveryChildInOrder() {
        var childCount = 1_000;
        var order = new CopyOnWriteArrayList<Integer>();
        var sequential = Sequential.builder("chain");
        for (var i = 0; i < childCount; i++) {
            var index = i;
            sequential.child(Step.of("step-" + index, context -> order.add(index)));
        }

        var result = runner().run(sequential.build());

        assertThat(result.isPassed()).isTrue();
        assertThat(order).hasSize(childCount);
        assertThat(order).isSorted();
        assertThat(result.descriptor().orElseThrow().children()).hasSize(childCount);
    }

    @Test
    @DisplayName("a failure in a long sequential chain skips the remaining children")
    void failureInLongSequentialChainSkipsRemaining() {
        var childCount = 1_000;
        var sequential = Sequential.builder("chain");
        sequential.child(Step.of("fail", context -> FailException.fail("expected")));
        for (var i = 1; i < childCount; i++) {
            sequential.child(Step.of("step-" + i, context -> {}));
        }

        var result = runner().run(sequential.build());

        assertThat(result.isFailed()).isTrue();
        var children = result.descriptor().orElseThrow().children();
        assertThat(children).hasSize(childCount);
        assertThat(children.get(0).isFailed()).isTrue();
        assertThat(children.subList(1, childCount)).allMatch(Descriptor::isSkipped, "every remaining child is skipped");
    }

    @Test
    @DisplayName("a wide parallel tree runs every child exactly once")
    void wideParallelTreeRunsEveryChildExactlyOnce() {
        var childCount = 500;
        var executed = new AtomicInteger();
        var parallel = Parallel.builder("wide").parallelism(Integer.MAX_VALUE);
        for (var i = 0; i < childCount; i++) {
            var name = "step-" + i;
            parallel.child(Step.of(name, context -> executed.incrementAndGet()));
        }

        var result = runner().run(parallel.build());

        assertThat(result.isPassed()).isTrue();
        assertThat(executed.get()).isEqualTo(childCount);
        assertThat(result.descriptor().orElseThrow().children()).hasSize(childCount);
    }

    @Test
    @DisplayName("child completions advance their parent inline rather than via the ready queue")
    void childCompletionsAdvanceParentInline() {
        var childCount = 64;
        var executed = new AtomicInteger();
        var sequential = Sequential.builder("chain");
        for (var i = 0; i < childCount; i++) {
            var name = "step-" + i;
            sequential.child(Step.of(name, context -> executed.incrementAndGet()));
        }

        var root = new DescriptorBuilder().discover(sequential.build());
        var scheduler = new Scheduler(2, 64);
        try {
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(), new Listener() {}, root, scheduler, new InstanceHolder());
            scheduler.executeDescriptor(root, context, ExecutionMode.RUN);

            assertThat(executed.get()).isEqualTo(childCount);
            assertThat(scheduler.inlineContinuations())
                    .as("the completing thread advances its parent without a queue round-trip")
                    .isPositive();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("leaf children run inline in a sequential chain instead of crossing threads")
    void leafChildrenRunInlineInSequentialChain() {
        var childCount = 200;
        var executed = new AtomicInteger();
        var sequential = Sequential.builder("chain");
        for (var i = 0; i < childCount; i++) {
            var name = "step-" + i;
            sequential.child(Step.of(name, context -> executed.incrementAndGet()));
        }

        var root = new DescriptorBuilder().discover(sequential.build());
        var scheduler = new Scheduler(2, 64);
        try {
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(), new Listener() {}, root, scheduler, new InstanceHolder());
            scheduler.executeDescriptor(root, context, ExecutionMode.RUN);

            assertThat(executed.get()).isEqualTo(childCount);
            assertThat(scheduler.inlineChildren())
                    .as("leaf children must run on the completing thread when nothing outranks them")
                    .isPositive();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(20)
    @DisplayName("a parallel window is not serialized by inline child execution")
    void parallelWindowIsNotSerialized() throws Exception {
        var parallelism = 4;
        var allStarted = new CountDownLatch(parallelism);
        var release = new CountDownLatch(1);
        var parallel = Parallel.builder("wide").parallelism(parallelism);
        for (var i = 0; i < parallelism; i++) {
            var name = "leaf-" + i;
            parallel.child(Step.of(name, context -> {
                allStarted.countDown();
                release.await();
            }));
        }

        var resultFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> runner(parallelism).run(parallel.build()));

        try {
            // If admission inlined each child, the first child would block the admission loop and
            // the rest would never start.
            assertThat(allStarted.await(5, TimeUnit.SECONDS))
                    .as("all parallel children must start before any completes")
                    .isTrue();
        } finally {
            release.countDown();
        }

        assertThat(resultFuture.get(10, TimeUnit.SECONDS).isPassed()).isTrue();
    }

    private static Runner runner() {
        return runner(4);
    }

    private static Runner runner(final int parallelism) {
        var configuration = Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM, String.valueOf(parallelism),
                Configuration.SCHEDULER_QUEUE_CAPACITY, "1024",
                Configuration.ANSI, "false"));
        return Runner.builder()
                .configuration(configuration)
                .listener(new Listener() {})
                .build();
    }
}

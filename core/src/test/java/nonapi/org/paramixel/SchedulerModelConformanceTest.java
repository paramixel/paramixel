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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Step;

/**
 * Verifies that the scheduler behaves as stated in {@code SCHEDULER.md}.
 *
 * <p>Covers the rules whose implementation is non-obvious and the two deviations that the document
 * calls out in §5. Each test names the paragraph it pins; a change in behaviour here means either
 * the document or the scheduler is wrong, and both need to move together.
 */
@DisplayName("Scheduler model conformance")
class SchedulerModelConformanceTest {

    @Test
    @Timeout(30)
    @DisplayName("R1: leaf bodies stay at N while the root's caller thread participates")
    void leafBodiesStayAtParallelismWithCallerThreadPresent() {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();

        var root = Parallel.builder("root").parallelism(Integer.MAX_VALUE);
        for (var i = 0; i < 8; i++) {
            var name = "leaf-" + i;
            root.child(Step.of(name, context -> {
                var current = active.incrementAndGet();
                maxActive.accumulateAndGet(current, Math::max);
                try {
                    Thread.sleep(25L);
                } finally {
                    active.decrementAndGet();
                }
            }));
        }

        var result = runner(2, 32).run(root.build());

        assertThat(result.isPassed()).isTrue();
        // The root runs on this test thread and steals work while it waits, so two pool threads
        // plus the caller could otherwise produce three concurrent leaf bodies.
        assertThat(maxActive.get()).isEqualTo(2);
    }

    @Test
    @Timeout(30)
    @DisplayName("R2: a Parallel node never exceeds the global bound")
    void declaredCapAboveParallelismIsClamped() {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();

        var root = Parallel.builder("root").parallelism(6);
        for (var i = 0; i < 6; i++) {
            var name = "leaf-" + i;
            root.child(Step.of(name, context -> {
                var current = active.incrementAndGet();
                maxActive.accumulateAndGet(current, Math::max);
                try {
                    Thread.sleep(25L);
                } finally {
                    active.decrementAndGet();
                }
            }));
        }

        var result = runner(2, 32).run(root.build());

        assertThat(result.isPassed()).isTrue();
        assertThat(maxActive.get())
                .as("declared cap 6 is clamped to the global parallelism 2")
                .isEqualTo(2);
    }

    @Test
    @Timeout(30)
    @DisplayName("R2: queue capacity does not reduce a node's effective cap")
    void queueCapacityDoesNotReduceEffectiveCap() {
        // Same tree and global parallelism; only the admission/backpressure bound changes.
        var cases = Map.of(2, 4, 32, 4);

        cases.forEach((queueCapacity, expectedMaxConcurrency) -> {
            var active = new AtomicInteger();
            var maxActive = new AtomicInteger();

            var root = Parallel.builder("root").parallelism(4);
            for (var i = 0; i < 4; i++) {
                var name = "leaf-" + i;
                root.child(Step.of(name, context -> {
                    var current = active.incrementAndGet();
                    maxActive.accumulateAndGet(current, Math::max);
                    try {
                        Thread.sleep(25L);
                    } finally {
                        active.decrementAndGet();
                    }
                }));
            }

            var result = runner(4, queueCapacity).run(root.build());

            assertThat(result.isPassed()).isTrue();
            assertThat(maxActive.get())
                    .as(
                            "queue capacity %d must not change concurrency (expected %d)",
                            queueCapacity, expectedMaxConcurrency)
                    .isEqualTo(expectedMaxConcurrency);
        });
    }

    @Test
    @Timeout(30)
    @DisplayName("R3: isolation defers work and dispatches it in key order")
    void isolationDefersInKeyOrderNotArrivalOrder() throws Exception {
        var order = new CopyOnWriteArrayList<String>();
        var holderStarted = new CountDownLatch(1);
        var releaseHolder = new CountDownLatch(1);

        var holder = Isolated.builder("holder", "L")
                .body(Step.of("holder-step", context -> {
                    holderStarted.countDown();
                    releaseHolder.await();
                    order.add("holder");
                }))
                .build();
        var earlier = Isolated.builder("earlier", "L")
                .body(Step.of("earlier-step", context -> order.add("earlier")))
                .build();
        var later = Isolated.builder("later", "L")
                .body(Step.of("later-step", context -> order.add("later")))
                .build();

        // Declaration order gives "earlier" key [2] and "later" key [3].
        var root = new DescriptorBuilder()
                .discover(Parallel.builder("root")
                        .parallelism(Integer.MAX_VALUE)
                        .child(holder)
                        .child(earlier)
                        .child(later)
                        .build());
        var holderDescriptor = (MutableDescriptor) root.children().get(0);
        var earlierDescriptor = (MutableDescriptor) root.children().get(1);
        var laterDescriptor = (MutableDescriptor) root.children().get(2);

        var scheduler = new Scheduler(2, 16);
        try {
            var context = newContext(scheduler, root);

            var holderFuture = scheduler.schedule(holderDescriptor, ExecutionMode.RUN, context);
            assertThat(holderStarted.await(10, TimeUnit.SECONDS)).isTrue();

            // Make the later-key sibling reach the lock first: schedule it, wait until it has been
            // dequeued and deferred, and only then schedule the earlier-key sibling. Key order must
            // still win when the lock is released.
            var laterFuture = scheduler.schedule(laterDescriptor, ExecutionMode.RUN, context);
            awaitReadyQueueDrained(scheduler);
            var earlierFuture = scheduler.schedule(earlierDescriptor, ExecutionMode.RUN, context);
            awaitReadyQueueDrained(scheduler);

            releaseHolder.countDown();
            awaitAll(holderFuture, laterFuture, earlierFuture);
        } finally {
            scheduler.close();
        }

        assertThat(order)
                .as("deferred work is dispatched by structural key, not arrival order")
                .containsExactly("holder", "earlier", "later");
    }

    /**
     * Waits until the scheduler's ready queue is empty, which means the most recently submitted
     * task has been dequeued (and, in this test, deferred behind the held lock).
     *
     * @param scheduler the scheduler to observe
     */
    private static void awaitReadyQueueDrained(final Scheduler scheduler) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (scheduler.readyQueueSize() > 0) {
            assertThat(System.nanoTime())
                    .as("ready queue did not drain within 10 seconds")
                    .isLessThan(deadline);
            Thread.onSpinWait();
        }
    }

    private static void awaitAll(final java.util.concurrent.CompletableFuture<?>... futures)
            throws InterruptedException, ExecutionException, TimeoutException {
        for (var future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
    }

    private static ConcreteContext newContext(final Scheduler scheduler, final MutableDescriptor root) {
        return new ConcreteContext(
                Configuration.defaultConfiguration(), new Listener() {}, root, scheduler, new InstanceHolder());
    }

    private static Runner runner(final int parallelism, final int queueCapacity) {
        var configuration = Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM, String.valueOf(parallelism),
                Configuration.SCHEDULER_QUEUE_CAPACITY, String.valueOf(queueCapacity),
                Configuration.ANSI, "false"));
        return Runner.builder()
                .configuration(configuration)
                .listener(new Listener() {})
                .build();
    }
}

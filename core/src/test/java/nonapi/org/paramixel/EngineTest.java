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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.action.DescriptorBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;
import org.paramixel.api.exception.FailException;

/**
 * Executable specification for the depth-first execution engine.
 *
 * <p>These tests pin the algorithm's invariants rather than implementation details: the thread
 * ceiling, permit conservation, bounded parallel admission, depth-first listener ordering, fail-fast
 * skipping, and isolation mutual exclusion.
 */
@DisplayName("Engine")
@org.junit.jupiter.api.Timeout(60)
class EngineTest {

    private static final String ENGINE_THREAD_PREFIX = "paramixel-engine-";
    private static final String TIMER_SUFFIX = "-timer";

    private static Configuration configuration(final int parallelism) {
        return Configuration.of(
                Map.of(Configuration.RUNNER_PARALLELISM, String.valueOf(parallelism), Configuration.ANSI, "false"));
    }

    private static int awaitPermits(final Engine engine, final int expected) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (engine.availablePermits() == expected) {
                return expected;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return engine.availablePermits();
    }

    private static Runner runner(final int parallelism) {
        return Runner.builder().configuration(configuration(parallelism)).build();
    }

    private static Set<Thread> engineThreads() {
        var threads = ConcurrentHashMap.<Thread>newKeySet();
        Thread.getAllStackTraces().keySet().forEach(thread -> {
            var name = thread.getName();
            if (name.startsWith(ENGINE_THREAD_PREFIX) && !name.endsWith(TIMER_SUFFIX)) {
                threads.add(thread);
            }
        });
        return threads;
    }

    @Nested
    @DisplayName("concurrency ceiling")
    class ConcurrencyCeiling {

        @Test
        @DisplayName("never creates more engine threads than parallelism")
        void neverExceedsThreadCeiling() throws Exception {
            var parallelism = 4;
            var release = new CountDownLatch(1);
            var started = new CountDownLatch(parallelism);
            var maxConcurrent = new AtomicInteger();
            var running = new AtomicInteger();

            var parallel = Parallel.builder("root");
            for (var i = 0; i < parallelism * 4; i++) {
                parallel.child(Step.of("child-" + i, context -> {
                    maxConcurrent.accumulateAndGet(running.incrementAndGet(), Math::max);
                    started.countDown();
                    release.await(30, TimeUnit.SECONDS);
                    running.decrementAndGet();
                }));
            }

            var result = new java.util.concurrent.CompletableFuture<org.paramixel.api.Result>();
            var runnerThread =
                    new Thread(() -> result.complete(runner(parallelism).run(parallel.build())), "test-runner");
            runnerThread.start();

            try {
                assertThat(started.await(30, TimeUnit.SECONDS))
                        .as("parallelism children started")
                        .isTrue();
                assertThat(engineThreads()).hasSizeLessThanOrEqualTo(parallelism);
                assertThat(maxConcurrent.get()).isLessThanOrEqualTo(parallelism);
            } finally {
                release.countDown();
            }

            assertThat(result.get(30, TimeUnit.SECONDS).isPassed()).isTrue();
            runnerThread.join(TimeUnit.SECONDS.toMillis(30));
            assertThat(engineThreads()).as("engine threads after close").isEmpty();
        }

        @Test
        @DisplayName("per-node cap bounds concurrent direct children")
        void perNodeCapBoundsChildren() throws Exception {
            var cap = 2;
            var release = new CountDownLatch(1);
            var started = new CountDownLatch(cap);
            var running = new AtomicInteger();
            var maxConcurrent = new AtomicInteger();

            var parallel = Parallel.builder("capped").parallelism(cap);
            for (var i = 0; i < 6; i++) {
                parallel.child(Step.of("child-" + i, context -> {
                    maxConcurrent.accumulateAndGet(running.incrementAndGet(), Math::max);
                    started.countDown();
                    release.await(30, TimeUnit.SECONDS);
                    running.decrementAndGet();
                }));
            }

            var result = new java.util.concurrent.CompletableFuture<org.paramixel.api.Result>();
            var runnerThread = new Thread(() -> result.complete(runner(8).run(parallel.build())), "test-runner");
            runnerThread.start();
            try {
                assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();
                assertThat(maxConcurrent.get()).isEqualTo(cap);
            } finally {
                release.countDown();
            }

            assertThat(result.get(30, TimeUnit.SECONDS).isPassed()).isTrue();
            runnerThread.join(TimeUnit.SECONDS.toMillis(30));
        }

        @Test
        @DisplayName("nested parallels never run more leaves than parallelism, including the caller")
        void nestedParallelsNeverExceedParallelism() {
            var parallelism = 3;
            var running = new AtomicInteger();
            var peak = new AtomicInteger();
            var outer = Parallel.builder("outer");
            for (var group = 0; group < 4; group++) {
                var inner = Parallel.builder("inner-" + group);
                for (var leaf = 0; leaf < 4; leaf++) {
                    inner.child(Step.of("leaf-" + leaf, context -> {
                        peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                        Thread.sleep(20);
                        running.decrementAndGet();
                    }));
                }
                outer.child(inner.build());
            }

            var result = runner(parallelism).run(outer.build());

            assertThat(result.isPassed()).isTrue();
            assertThat(peak.get()).isLessThanOrEqualTo(parallelism);
        }

        @Test
        @DisplayName("nested parallels complete every leaf")
        void nestedParallelsCompleteEveryLeaf() {
            var executions = new AtomicInteger();
            var inner = Parallel.builder("inner");
            for (var i = 0; i < 4; i++) {
                inner.child(Step.of("inner-" + i, context -> executions.incrementAndGet()));
            }
            var outer = Parallel.builder("outer").parallelism(2);
            for (var i = 0; i < 3; i++) {
                outer.child(inner.build());
            }

            var result = runner(3).run(outer.build());

            assertThat(result.isPassed()).isTrue();
            assertThat(executions.get()).isEqualTo(12);
        }
    }

    @Nested
    @DisplayName("permit conservation")
    class PermitConservation {

        @Test
        @DisplayName("all permits are returned after a successful run")
        void permitsReturnedAfterSuccess() {
            var engine = new Engine(4);
            try {
                var root = new DescriptorBuilder()
                        .discover(Sequence.builder("root")
                                .child(Step.of("a", context -> {}))
                                .child(Step.of("b", context -> {}))
                                .build());
                root.markScheduled();

                engine.execute(root, configuration(4), new Listener() {});

                assertThat(root.isPassed()).isTrue();
                assertThat(engine.availablePermits()).isEqualTo(4);
            } finally {
                engine.close();
            }
        }

        @Test
        @DisplayName("all permits are returned after a failing parallel run")
        void permitsReturnedAfterFailure() {
            var engine = new Engine(3);
            try {
                var parallel = Parallel.builder("root");
                for (var i = 0; i < 9; i++) {
                    var name = "child-" + i;
                    parallel.child(Step.of(name, context -> FailException.fail("boom")));
                }
                var root = new DescriptorBuilder().discover(parallel.build());
                root.markScheduled();

                engine.execute(root, configuration(3), new Listener() {});

                assertThat(root.isFailed()).isTrue();
                assertThat(engine.availablePermits()).isEqualTo(3);
            } finally {
                engine.close();
            }
        }

        @Test
        @DisplayName("all permits are returned after a timeout aborts a blocking parallel")
        void permitsReturnedAfterTimeout() {
            var engine = new Engine(4);
            try {
                var inner = Parallel.builder("inner");
                for (var i = 0; i < 4; i++) {
                    inner.child(Step.of("blocking-" + i, context -> Thread.sleep(60_000)));
                }
                var root = new DescriptorBuilder()
                        .discover(Timeout.builder("timeout")
                                .body(inner.build())
                                .timeout(Duration.ofMillis(250))
                                .build());
                root.markScheduled();

                engine.execute(root, configuration(4), new Listener() {});

                assertThat(root.isFailed()).isTrue();
                assertThat(awaitPermits(engine, 4)).isEqualTo(4);
            } finally {
                engine.close();
            }
        }
    }

    @Nested
    @DisplayName("saturated pool")
    class SaturatedPool {

        @Test
        @DisplayName("siblings that must run together start once other branches free their permits")
        void rendezvousCompletesAfterPermitsAreFreed() throws Exception {
            var engine = new Engine(4);
            try {
                var blockers = new CountDownLatch(1);
                var rendezvous = new CountDownLatch(3);
                var inner = Parallel.builder("rendezvous");
                for (var i = 0; i < 3; i++) {
                    inner.child(Step.of("meet-" + i, context -> {
                        rendezvous.countDown();
                        if (!rendezvous.await(30, TimeUnit.SECONDS)) {
                            FailException.fail("siblings never ran together");
                        }
                    }));
                }
                // Three blockers hold every permit and the coordinator, so the rendezvous group starts
                // with no free permit and can only complete when the blockers finish.
                var root = Parallel.builder("root");
                for (var i = 0; i < 3; i++) {
                    root.child(Step.of("blocker-" + i, context -> blockers.await(30, TimeUnit.SECONDS)));
                }
                root.child(inner.build());
                var tree = new DescriptorBuilder().discover(root.build());
                tree.markScheduled();

                var releaser = new Thread(
                        () -> {
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                blockers.countDown();
                            }
                        },
                        "test-releaser");
                releaser.start();

                engine.execute(tree, configuration(4), new Listener() {});

                assertThat(tree.isPassed()).isTrue();
                assertThat(awaitPermits(engine, 4)).isEqualTo(4);
            } finally {
                engine.close();
            }
        }

        @Test
        @DisplayName("a long-running child does not delay later siblings once a permit is free")
        void longRunningChildDoesNotDelaySiblings() throws Exception {
            var engine = new Engine(2);
            try {
                var laterStarted = new CountDownLatch(1);
                var root = Parallel.builder("root")
                        .child(Step.of("long", context -> {
                            if (!laterStarted.await(30, TimeUnit.SECONDS)) {
                                FailException.fail("later sibling never started");
                            }
                        }))
                        .child(Step.of("later", context -> laterStarted.countDown()))
                        .build();
                var tree = new DescriptorBuilder().discover(root);
                tree.markScheduled();

                engine.execute(tree, configuration(2), new Listener() {});

                assertThat(tree.isPassed()).isTrue();
                assertThat(awaitPermits(engine, 2)).isEqualTo(2);
            } finally {
                engine.close();
            }
        }
    }

    @Nested
    @DisplayName("timeout preemption")
    class TimeoutPreemption {

        private static void ignoreInterruptsFor(final Duration duration) {
            var end = System.nanoTime() + duration.toNanos();
            while (System.nanoTime() < end) {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException ignored) {
                    // Deliberately non-cooperative
                }
            }
        }

        @Test
        @DisplayName("preempts a non-cooperative child even when every permit is held")
        void preemptsWhenNoPermitIsFree() {
            var engine = new Engine(2);
            try {
                var root = Parallel.builder("root")
                        .child(Step.of("blocker-a", context -> Thread.sleep(2_000)))
                        .child(Step.of("blocker-b", context -> Thread.sleep(2_000)))
                        .child(Timeout.builder("timeout")
                                .body(Step.of("stubborn", context -> ignoreInterruptsFor(Duration.ofSeconds(6))))
                                .timeout(Duration.ofMillis(200))
                                .build())
                        .build();
                var tree = new DescriptorBuilder().discover(root);
                tree.markScheduled();

                var start = System.nanoTime();
                engine.execute(tree, configuration(2), new Listener() {});
                var elapsed = Duration.ofNanos(System.nanoTime() - start);

                assertThat(tree.isFailed()).isTrue();
                assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
            } finally {
                engine.close();
            }
        }

        @Test
        @DisplayName("nested timeouts deeper than parallelism complete")
        void nestedTimeoutsDeeperThanParallelismComplete() {
            var engine = new Engine(2);
            try {
                org.paramixel.api.action.Action action = Step.of("leaf", context -> {});
                for (var i = 0; i < 8; i++) {
                    action = Timeout.builder("timeout-" + i)
                            .body(action)
                            .timeout(Duration.ofSeconds(30))
                            .build();
                }
                var tree = new DescriptorBuilder().discover(action);
                tree.markScheduled();

                engine.execute(tree, configuration(2), new Listener() {});

                assertThat(tree.isPassed()).isTrue();
                assertThat(awaitPermits(engine, 2)).isEqualTo(2);
            } finally {
                engine.close();
            }
        }

        @Test
        @DisplayName("a timed branch keeps the isolation of its parent")
        void timedBranchKeepsIsolation() {
            var engine = new Engine(4);
            try {
                var running = new AtomicInteger();
                var maxConcurrent = new AtomicInteger();
                var inner = Parallel.builder("inner");
                for (var i = 0; i < 4; i++) {
                    inner.child(Step.of("child-" + i, context -> {
                        maxConcurrent.accumulateAndGet(running.incrementAndGet(), Math::max);
                        Thread.sleep(50);
                        running.decrementAndGet();
                    }));
                }
                var root = Isolated.builder("isolated", "lock")
                        .body(Timeout.builder("timeout")
                                .body(inner.build())
                                .timeout(Duration.ofSeconds(30))
                                .build())
                        .build();
                var tree = new DescriptorBuilder().discover(root);
                tree.markScheduled();

                engine.execute(tree, configuration(4), new Listener() {});

                assertThat(tree.isPassed()).isTrue();
                assertThat(maxConcurrent.get()).isEqualTo(1);
            } finally {
                engine.close();
            }
        }
    }

    @Nested
    @DisplayName("depth-first ordering")
    class DepthFirstOrdering {

        @Test
        @DisplayName("listener callbacks nest in depth-first order on one thread per descriptor")
        void listenerCallbacksNestDepthFirst() {
            var events = new CopyOnWriteArrayList<String>();
            var threads = new ConcurrentHashMap<String, Thread>();
            var listener = new Listener() {
                @Override
                public void onBeforeExecution(final Descriptor descriptor) {
                    events.add("before:" + descriptor.action().displayName());
                    threads.put("before:" + descriptor.action().displayName(), Thread.currentThread());
                }

                @Override
                public void onAfterExecution(final Descriptor descriptor) {
                    events.add("after:" + descriptor.action().displayName());
                    threads.put("after:" + descriptor.action().displayName(), Thread.currentThread());
                }
            };

            var action = Sequence.builder("root")
                    .child(Step.of("a", context -> {}))
                    .child(Step.of("b", context -> {}))
                    .build();

            var result = Runner.builder()
                    .configuration(configuration(1))
                    .listener(listener)
                    .build()
                    .run(action);

            assertThat(result.isPassed()).isTrue();
            assertThat(events)
                    .containsExactly("before:root", "before:a", "after:a", "before:b", "after:b", "after:root");
            assertThat(threads.get("before:root")).isSameAs(threads.get("after:root"));
            assertThat(threads.get("before:a")).isSameAs(threads.get("after:a"));
            assertThat(threads.get("before:b")).isSameAs(threads.get("after:b"));
        }
    }

    @Nested
    @DisplayName("fail fast")
    class FailFast {

        @Test
        @DisplayName("remaining root children are skipped after the first failure")
        void remainingRootChildrenSkipped() {
            var secondRan = new AtomicInteger();
            var configuration = Configuration.of(Map.of(
                    Configuration.RUNNER_PARALLELISM,
                    "2",
                    Configuration.FAIL_FAST,
                    "true",
                    Configuration.ANSI,
                    "false"));

            var action = Sequence.builder("root")
                    .child(Step.of("failing", context -> FailException.fail("boom")))
                    .child(Step.of("second", context -> secondRan.incrementAndGet()))
                    .build();

            var result = Runner.builder().configuration(configuration).build().run(action);

            assertThat(result.isFailed()).isTrue();
            assertThat(secondRan.get()).isZero();
            var children = result.descriptor().orElseThrow().children();
            assertThat(children.get(1).isSkipped()).isTrue();
            assertThat(children.get(1).message()).contains("fail fast");
        }
    }

    @Nested
    @DisplayName("isolation")
    class Isolation {

        @Test
        @DisplayName("same-lock isolated bodies never overlap")
        void isolatedBodiesNeverOverlap() {
            var inside = new AtomicInteger();
            var maxInside = new AtomicInteger();
            var bodies = Parallel.builder("root").parallelism(4);
            for (var i = 0; i < 4; i++) {
                bodies.child(Isolated.builder("isolated-" + i, "shared-lock")
                        .body(Step.of("body-" + i, context -> {
                            maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                            Thread.sleep(25);
                            inside.decrementAndGet();
                        }))
                        .build());
            }

            var result = runner(4).run(bodies.build());

            assertThat(result.isPassed()).isTrue();
            assertThat(maxInside.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("listener warnings")
    class Warnings {

        @Test
        @DisplayName("warnings reach the listener chain during initialization")
        void warningsReachListener() {
            var warnings = new CopyOnWriteArrayList<String>();
            var listener = new Listener() {
                @Override
                public void onWarning(final String message) {
                    warnings.add(message);
                }
            };

            RemovedConfigurationKeys.resetForTesting();
            var configuration =
                    Configuration.of(Map.of("paramixel.scheduler.queue.capacity", "64", Configuration.ANSI, "false"));

            Runner.builder()
                    .configuration(configuration)
                    .listener(listener)
                    .build()
                    .run(Step.of("step", context -> {}));

            assertThat(warnings).hasSize(1);
        }
    }

    @Test
    @DisplayName("descriptors are terminal after execution")
    void descriptorsAreTerminalAfterExecution() {
        var result = runner(2)
                .run(Parallel.builder("root")
                        .child(Step.of("a", context -> {}))
                        .child(Step.of("b", context -> {}))
                        .build());

        var root = result.descriptor().orElseThrow();
        assertThat(root.isCompleted()).isTrue();
        assertThat(List.of(root).get(0).children()).allMatch(Descriptor::isCompleted);
    }
}

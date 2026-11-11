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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
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
        return TestPools.configuration(Map.of(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(parallelism),
                "paramixel.parallelism.test",
                String.valueOf(parallelism),
                "paramixel.parallelism.p2",
                "2",
                "paramixel.parallelism.p3",
                "3",
                "paramixel.parallelism.pcap",
                "2",
                Configuration.ANSI,
                "false"));
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
    @DisplayName("pool admission")
    class PoolAdmission {

        private Parallel.Builder fanOut(
                final String pool, final int children, final AtomicInteger running, final AtomicInteger max) {
            var parallel = Parallel.parallel("fan-out", pool);
            for (var i = 0; i < children; i++) {
                parallel.child(Step.step("child-" + i, context -> {
                    max.accumulateAndGet(running.incrementAndGet(), Math::max);
                    Thread.sleep(40);
                    running.decrementAndGet();
                }));
            }
            return parallel;
        }

        @Test
        @DisplayName("a pool bounds the live children of a Parallel")
        void poolBoundsLiveChildren() {
            var running = new AtomicInteger();
            var max = new AtomicInteger();

            var result = runner(8).run(fanOut("p2", 8, running, max).build());

            assertThat(result.status().isPassed()).isTrue();
            assertThat(max.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("a pool is shared by Parallel nodes in different branches")
        void poolSharedAcrossBranches() {
            var running = new AtomicInteger();
            var max = new AtomicInteger();
            var action = Parallel.parallel("outer", "test")
                    .child(fanOut("p3", 6, running, max))
                    .child(fanOut("p3", 6, running, max))
                    .build();

            var result = runner(8).run(action);

            assertThat(result.status().isPassed()).isTrue();
            assertThat(max.get()).isLessThanOrEqualTo(3);
        }

        @Test
        @DisplayName("a pool of size one runs children serially in declaration order")
        void sizeOneIsSerialInDeclarationOrder() {
            var events = new CopyOnWriteArrayList<String>();
            var parallel = Parallel.parallel("serial", "p1");
            for (var name : List.of("a", "b", "c")) {
                parallel.child(Step.step(name, context -> events.add(name)));
            }

            var result = Runner.builder()
                    .configuration(TestPools.configuration(
                            Map.of("paramixel.parallelism.p1", "1", Configuration.ANSI, "false")))
                    .build()
                    .run(parallel.build());

            assertThat(result.status().isPassed()).isTrue();
            assertThat(events).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("a long-running child does not delay later siblings once a permit is free")
        void longRunningChildDoesNotDelayLaterSiblings() {
            var release = new java.util.concurrent.CountDownLatch(1);
            var action = Parallel.parallel("siblings", "p2")
                    .child(Step.step("blocked", context -> release.await()))
                    .child(Step.step("releases", context -> release.countDown()))
                    .build();

            var result = runner(8).run(action);

            assertThat(result.status().isPassed()).isTrue();
        }

        @Test
        @DisplayName("all permits are returned after a run")
        void permitsRestored() throws Exception {
            var pools = new Pools(configuration(8));
            var engine = new Engine(pools);
            try {
                var running = new AtomicInteger();
                var max = new AtomicInteger();
                var plan = CompiledPlan.compile(fanOut("p2", 5, running, max).build());

                engine.execute(plan, configuration(8), new Listener() {});

                assertThat(plan.root().descriptor.status().isPassed()).isTrue();
                assertThat(engine.availablePermits("p2")).isEqualTo(2);
            } finally {
                engine.close();
            }
        }

        @Test
        @DisplayName("a pool that is not defined fails the run before any step runs")
        void undefinedPoolFailsBeforeRunning() {
            var ran = new AtomicInteger();
            var action = Parallel.parallel("p", "nope")
                    .child(Step.step("child", context -> ran.incrementAndGet()))
                    .build();

            var result = runner(8).run(action);

            assertThat(result.status().isFailed()).isTrue();
            assertThat(ran.get()).isZero();
            assertThat(result.descriptor().orElseThrow().status().message().orElse(""))
                    .contains("paramixel.parallelism.nope");
        }

        @Test
        @DisplayName("a pool nested under itself is rejected before any step runs")
        void selfNestingRejected() {
            var ran = new AtomicInteger();
            var action = Parallel.parallel("outer", "p2")
                    .child(Parallel.parallel("inner", "p2").child(Step.step("leaf", context -> ran.incrementAndGet())))
                    .build();

            var result = runner(8).run(action);

            assertThat(result.status().isFailed()).isTrue();
            assertThat(ran.get()).isZero();
            assertThat(result.descriptor().orElseThrow().status().message().orElse(""))
                    .contains("cannot be nested under itself");
        }

        @Test
        @DisplayName("resources taken in conflicting order are rejected before any step runs")
        void conflictingOrderRejected() {
            var ran = new AtomicInteger();
            var step = Step.step("leaf", context -> ran.incrementAndGet());
            var action = Parallel.parallel("root-of-test", "test")
                    .child(Isolated.isolated("lock-first", "L")
                            .body(Parallel.parallel("then-pool", "p2").child(step)))
                    .child(Parallel.parallel("pool-first", "p2")
                            .child(Isolated.isolated("then-lock", "L").body(step)))
                    .build();

            var result = runner(8).run(action);

            assertThat(result.status().isFailed()).isTrue();
            assertThat(ran.get()).isZero();
            assertThat(result.descriptor().orElseThrow().status().message().orElse(""))
                    .contains("conflicting order");
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
            var engine = new Engine(new Pools(configuration(2)));
            try {
                var root = Parallel.parallel("root", "t8")
                        .child(Step.step("blocker-a", context -> Thread.sleep(2_000)))
                        .child(Step.step("blocker-b", context -> Thread.sleep(2_000)))
                        .child(Timeout.timeout("timeout")
                                .body(Step.step("stubborn", context -> ignoreInterruptsFor(Duration.ofSeconds(20))))
                                .timeout(Duration.ofMillis(200))
                                .build())
                        .build();
                var plan = CompiledPlan.compile(root);

                var start = System.nanoTime();
                engine.execute(plan, configuration(2), new Listener() {});
                var elapsed = Duration.ofNanos(System.nanoTime() - start);

                assertThat(plan.root().descriptor.status().isFailed()).isTrue();
                // The blockers hold both permits for 2 s; the timeout then fires after 200 ms and,
                // because the child ignores interruption, is abandoned after the 5 s cancellation
                // grace (about 7.2 s in total) — long before the stubborn child itself would finish.
                assertThat(elapsed).isLessThan(Duration.ofSeconds(12));
            } finally {
                engine.close();
            }
        }

        @Test
        @DisplayName("nested timeouts deeper than parallelism complete")
        void nestedTimeoutsDeeperThanParallelismComplete() {
            var engine = new Engine(new Pools(configuration(2)));
            try {
                org.paramixel.api.action.Action action = Step.step("leaf", context -> {});
                for (var i = 0; i < 8; i++) {
                    action = Timeout.timeout("timeout-" + i)
                            .body(action)
                            .timeout(Duration.ofSeconds(30))
                            .build();
                }
                var plan = CompiledPlan.compile(action);

                engine.execute(plan, configuration(2), new Listener() {});

                assertThat(plan.root().descriptor.status().isPassed()).isTrue();
            } finally {
                engine.close();
            }
        }

        @Test
        @DisplayName("a Parallel under an Isolated runs at its pool width, even through a Timeout")
        void timedBranchKeepsIsolation() {
            var engine = new Engine(new Pools(configuration(4)));
            try {
                var running = new AtomicInteger();
                var maxConcurrent = new AtomicInteger();
                var inner = Parallel.parallel("inner", "t9");
                for (var i = 0; i < 4; i++) {
                    inner.child(Step.step("child-" + i, context -> {
                        maxConcurrent.accumulateAndGet(running.incrementAndGet(), Math::max);
                        Thread.sleep(50);
                        running.decrementAndGet();
                    }));
                }
                var root = Isolated.isolated("isolated", "lock")
                        .body(Timeout.timeout("timeout")
                                .body(inner.build())
                                .timeout(Duration.ofSeconds(30))
                                .build())
                        .build();
                var plan = CompiledPlan.compile(root);

                engine.execute(plan, configuration(4), new Listener() {});

                assertThat(plan.root().descriptor.status().isPassed()).isTrue();
                assertThat(maxConcurrent.get()).isGreaterThan(1);
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

            var action = Sequential.sequential("root")
                    .child(Step.step("a", context -> {}))
                    .child(Step.step("b", context -> {}))
                    .build();

            var result = Runner.builder()
                    .configuration(configuration(1))
                    .listener(listener)
                    .build()
                    .run(action);

            assertThat(result.status().isPassed()).isTrue();
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
            var configuration = TestPools.configuration(Map.of(
                    Configuration.RUNNER_PARALLELISM,
                    "2",
                    Configuration.FAIL_FAST,
                    "true",
                    Configuration.ANSI,
                    "false"));

            var action = Sequential.sequential("root")
                    .child(Step.step("failing", context -> {
                        throw FailException.fail("boom");
                    }))
                    .child(Step.step("second", context -> secondRan.incrementAndGet()))
                    .build();

            var result = Runner.builder().configuration(configuration).build().run(action);

            assertThat(result.status().isFailed()).isTrue();
            assertThat(secondRan.get()).isZero();
            var children = result.descriptor().orElseThrow().children();
            assertThat(children.get(1).status().isSkipped()).isTrue();
            assertThat(children.get(1).status().message()).contains("fail fast");
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
            var bodies = Parallel.parallel("root", "p4x1");
            for (var i = 0; i < 4; i++) {
                bodies.child(Isolated.isolated("isolated-" + i, "shared-lock")
                        .body(Step.step("body-" + i, context -> {
                            maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                            Thread.sleep(25);
                            inside.decrementAndGet();
                        }))
                        .build());
            }

            var result = runner(4).run(bodies.build());

            assertThat(result.status().isPassed()).isTrue();
            assertThat(maxInside.get()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("descriptors are terminal after execution")
    void descriptorsAreTerminalAfterExecution() {
        var result = runner(2)
                .run(Parallel.parallel("root", "t11")
                        .child(Step.step("a", context -> {}))
                        .child(Step.step("b", context -> {}))
                        .build());

        var root = result.descriptor().orElseThrow();
        assertThat(root.isCompleted()).isTrue();
        assertThat(List.of(root).get(0).children()).allMatch(Descriptor::isCompleted);
    }
}

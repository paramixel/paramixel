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

package org.paramixel.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.AsyncScheduler;
import org.paramixel.core.Configuration;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Parallel;

@DisplayName("DefaultAsyncScheduler")
class DefaultAsyncSchedulerTest {

    @Test
    @DisplayName("enforces global parallelism limit")
    void enforcesGlobalParallelismLimit() {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        var builder = Parallel.builder("parallel");
        for (int i = 0; i < 10; i++) {
            builder.child(tracked("child-" + i, active, maxActive));
        }

        Result result = runner(2).run(builder.build());

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(maxActive.get()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("enforces Parallel direct child limit")
    void enforcesParallelDirectChildLimit() {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        var builder = Parallel.builder("parallel").parallelism(2);
        for (int i = 0; i < 10; i++) {
            builder.child(tracked("child-" + i, active, maxActive));
        }

        Result result = runner(10).run(builder.build());

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(maxActive.get()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("continues sibling branch when one Parallel has queued children")
    void continuesSiblingBranchWhenOneParallelHasQueuedChildren() throws InterruptedException {
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch siblingRan = new CountDownLatch(1);
        AtomicBoolean queuedChildRanBeforeSiblingReleased = new AtomicBoolean();
        Action blockingChild = Direct.builder("blocking")
                .execute(context -> {
                    blockerStarted.countDown();
                    releaseBlocker.await();
                })
                .build();
        Action queuedChild = Direct.builder("queued")
                .execute(context -> queuedChildRanBeforeSiblingReleased.set(siblingRan.getCount() > 0))
                .build();
        Action constrainedBranch = Parallel.builder("constrained")
                .parallelism(1)
                .child(blockingChild)
                .child(queuedChild)
                .build();
        Action sibling = Direct.builder("sibling")
                .execute(context -> {
                    assertThat(blockerStarted.await(5, TimeUnit.SECONDS)).isTrue();
                    siblingRan.countDown();
                    releaseBlocker.countDown();
                })
                .build();
        Action root =
                Parallel.builder("root").child(constrainedBranch).child(sibling).build();

        Result result = runner(2).run(root);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(siblingRan.getCount()).isZero();
        assertThat(queuedChildRanBeforeSiblingReleased).isFalse();
    }

    @Test
    @DisplayName("runs Container after after child completion")
    void runsContainerAfterAfterChildCompletion() {
        AtomicBoolean childComplete = new AtomicBoolean();
        AtomicBoolean afterSawChildComplete = new AtomicBoolean();
        Action child = Direct.builder("child")
                .execute(context -> childComplete.set(true))
                .build();
        Action after = Direct.builder("after")
                .execute(context -> afterSawChildComplete.set(childComplete.get()))
                .build();

        Result result = runner(2)
                .run(Container.builder("container").child(child).after(after).build());

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(afterSawChildComplete).isTrue();
    }

    @Test
    @DisplayName("supports Context.runAsync from inside an action")
    void supportsContextRunAsyncFromInsideAnAction() {
        AtomicBoolean asyncRan = new AtomicBoolean();
        Action async =
                Direct.builder("async").execute(context -> asyncRan.set(true)).build();
        Action action = Direct.builder("scheduler")
                .execute(context -> assertThat(
                                context.runAsync(async).join().getStatus().isPass())
                        .isTrue())
                .build();

        Result result = runner(2).run(action);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(asyncRan).isTrue();
    }

    @Test
    @DisplayName("runs nested parallel deeper than global parallelism")
    void runsNestedParallelDeeperThanGlobalParallelism() {
        Result result = runner(1).run(nestedParallel(5));

        assertThat(result.getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("uses custom Parallel scheduler for direct children")
    void usesCustomParallelSchedulerForDirectChildren() {
        try (var scheduler = new RecordingScheduler()) {
            Action parallel = Parallel.builder("parallel")
                    .scheduler(scheduler)
                    .child(Noop.of("first"))
                    .child(Noop.of("second"))
                    .build();

            Result result = runner(2).run(parallel);

            assertThat(result.getStatus().isPass()).isTrue();
            assertThat(scheduler.scheduledNames()).containsExactlyInAnyOrder("first", "second");
        }
    }

    @Test
    @DisplayName("custom Parallel scheduler is inherited by Context.runAsync")
    void customParallelSchedulerIsInheritedByContextRunAsync() {
        try (var scheduler = new RecordingScheduler()) {
            Action nested = Noop.of("nested");
            Action child = Direct.builder("child")
                    .execute(context -> assertThat(
                                    context.runAsync(nested).join().getStatus().isPass())
                            .isTrue())
                    .build();
            Action parallel = Parallel.builder("parallel")
                    .scheduler(scheduler)
                    .child(child)
                    .build();

            Result result = runner(3).run(parallel);

            assertThat(result.getStatus().isPass()).isTrue();
            assertThat(scheduler.scheduledNames()).containsExactlyInAnyOrder("child", "nested");
        }
    }

    @Test
    @DisplayName("enforces Parallel direct child limit with custom scheduler")
    void enforcesParallelDirectChildLimitWithCustomScheduler() {
        try (var scheduler = new RecordingScheduler()) {
            AtomicInteger active = new AtomicInteger();
            AtomicInteger maxActive = new AtomicInteger();
            var builder = Parallel.builder("parallel").scheduler(scheduler).parallelism(2);
            for (int i = 0; i < 8; i++) {
                builder.child(tracked("child-" + i, active, maxActive));
            }

            Result result = runner(10).run(builder.build());

            assertThat(result.getStatus().isPass()).isTrue();
            assertThat(maxActive.get()).isLessThanOrEqualTo(2);
            assertThat(scheduler.scheduledNames()).hasSize(8);
        }
    }

    private static Action tracked(String name, AtomicInteger active, AtomicInteger maxActive) {
        return Direct.builder(name)
                .execute(context -> {
                    int current = active.incrementAndGet();
                    maxActive.accumulateAndGet(current, Math::max);
                    try {
                        Thread.sleep(20L);
                    } finally {
                        active.decrementAndGet();
                    }
                })
                .build();
    }

    private static Action nestedParallel(int depth) {
        if (depth == 0) {
            return Noop.of("leaf");
        }
        return Parallel.builder("parallel-" + depth)
                .child(nestedParallel(depth - 1))
                .build();
    }

    private static DefaultRunner runner(int parallelism) {
        return new DefaultRunner(
                Map.of(Configuration.RUNNER_PARALLELISM, Integer.toString(parallelism)), new Listener() {});
    }

    private static final class RecordingScheduler implements AsyncScheduler, AutoCloseable {

        private final ExecutorService executor = Executors.newFixedThreadPool(4);
        private final java.util.List<String> scheduledNames =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        @Override
        public CompletableFuture<Result> runAsync(Action action, Context context) {
            scheduledNames.add(action.getName());
            return CompletableFuture.supplyAsync(() -> action.execute(context), executor);
        }

        private List<String> scheduledNames() {
            synchronized (scheduledNames) {
                return List.copyOf(scheduledNames);
            }
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }
}

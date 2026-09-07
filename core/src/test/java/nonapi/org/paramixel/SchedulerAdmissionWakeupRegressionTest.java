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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * Regression test: an internal child admission that succeeds via the queued-task relinquish path
 * must not leave the coordination node registered as a capacity waiter. A stale registration
 * turns the next capacity signal into a spurious admission retry, which dispatches the next
 * sequential child while the current child is still running.
 */
@DisplayName("Scheduler admission wakeup")
class SchedulerAdmissionWakeupRegressionTest {

    @Test
    @Timeout(30)
    @DisplayName("successful relinquish-based admission leaves no stale admission wakeup behind")
    void successfulRelinquishBasedAdmissionLeavesNoStaleWakeup() throws Exception {
        // parallelism=3 workers, queue capacity=2 permits so the ready queue can be fully
        // exhausted by two queued tasks while all workers are busy.
        var scheduler = new Scheduler(3, 2);
        var lockName = "iso-L";
        var lock = scheduler.getLock(lockName);
        var dummy = new ConcreteDescriptor(Step.of("dummy", context -> {}));
        try {
            var configuration = Configuration.of(Map.of(Configuration.ANSI, "false"));
            var listener = new Listener() {};
            var root = new DescriptorBuilder().discover(Step.of("root", context -> {}));
            var context = new ConcreteContext(configuration, listener, root, scheduler, new InstanceHolder());

            var t0Started = new AtomicBoolean();
            var e1Started = new AtomicBoolean();
            var e2Started = new AtomicBoolean();
            var s1Started = new AtomicBoolean();
            var s2Started = new AtomicBoolean();
            var lT0 = new CountDownLatch(1);
            var lE1 = new CountDownLatch(1);
            var lE2 = new CountDownLatch(1);
            var lS1 = new CountDownLatch(1);

            // Occupy all three workers with latched steps.
            var t0 = newChild(root, Step.of("t0", ctx -> {
                t0Started.set(true);
                await(lT0);
            }));
            var e1 = newChild(root, Step.of("e1", ctx -> {
                e1Started.set(true);
                await(lE1);
            }));
            var e2 = newChild(root, Step.of("e2", ctx -> {
                e2Started.set(true);
                await(lE2);
            }));
            // Two queued tasks exhaust both queue permits (workers are all busy, so they stay queued).
            var e3 = newChild(root, Step.of("e3", ctx -> {}));
            var e4 = newChild(root, Step.of("e4", ctx -> {}));

            var t0Future = scheduleAndAwaitStart(scheduler, t0, context, t0Started);
            var e1Future = scheduleAndAwaitStart(scheduler, e1, context, e1Started);
            var e2Future = scheduleAndAwaitStart(scheduler, e2, context, e2Started);
            var e3Future = scheduler.schedule(e3, ExecutionMode.RUN, context);
            var e4Future = scheduler.schedule(e4, ExecutionMode.RUN, context);

            // Sequential subtree whose first-child admission happens on this thread while it
            // holds an isolation frame: the admission finds zero permits, parks an ineligible
            // queued task, and admits the child with the freed permit.
            var sequentialAction = Sequential.builder("seq")
                    .child(Step.of("s1", ctx -> {
                        s1Started.set(true);
                        await(lS1);
                    }))
                    .child(Step.of("s2", ctx -> s2Started.set(true)))
                    .build();
            var seq = new DescriptorBuilder().discover(sequentialAction);
            root.addChild(seq);
            var s1Desc = (MutableDescriptor) seq.children().get(0);
            var seqContext = new ConcreteContext(configuration, listener, seq, scheduler, new InstanceHolder());

            scheduler.enterIsolation(lockName, lock, dummy);
            try {
                var status = ActionExecutionStrategies.execute(sequentialAction, seqContext);
                assertThat(status.isRunning())
                        .as("first child must be admitted")
                        .isTrue();

                // Free two workers: one picks up s1 (deepest priority), the other drains the
                // remaining queued work. With a stale capacity-waiter registration, the second
                // worker also runs a spurious admission retry that dispatches s2 early.
                lT0.countDown();
                lE1.countDown();
                assertThat(await(s1Started)).as("s1 must start").isTrue();

                // Observation window: s2 must not start while s1 is still running.
                Thread.sleep(1_000);
                assertThat(s2Started.get())
                        .as("s2 must not start before s1 completes (sequential ordering)")
                        .isFalse();

                lS1.countDown();
                assertThat(await(s2Started))
                        .as("s2 must start after s1 completes")
                        .isTrue();
                lE2.countDown();

                joinWithin(s1Desc.scheduledFuture(), "s1");
                joinWithin(t0Future, "t0");
                joinWithin(e1Future, "e1");
                joinWithin(e2Future, "e2");
                joinWithin(e3Future, "e3");
                joinWithin(e4Future, "e4");
            } finally {
                lS1.countDown();
                lT0.countDown();
                lE1.countDown();
                lE2.countDown();
                scheduler.exitIsolation(lockName, lock, dummy);
            }
        } finally {
            scheduler.close();
        }
    }

    private static CompletableFuture<org.paramixel.api.Descriptor> scheduleAndAwaitStart(
            final Scheduler scheduler,
            final MutableDescriptor child,
            final ConcreteContext context,
            final AtomicBoolean started)
            throws InterruptedException {
        var future = scheduler.schedule(child, ExecutionMode.RUN, context);
        assertThat(await(started)).as("task must start").isTrue();
        return future;
    }

    private static MutableDescriptor newChild(final MutableDescriptor parent, final Step step) {
        var child = new ConcreteDescriptor(parent, step);
        parent.addChild(child);
        return child;
    }

    private static boolean await(final AtomicBoolean flag) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!flag.get()) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }

    private static boolean await(final CountDownLatch latch) throws InterruptedException {
        return latch.await(10, TimeUnit.SECONDS);
    }

    private static void joinWithin(final CompletableFuture<org.paramixel.api.Descriptor> future, final String name) {
        try {
            future.join();
        } catch (Exception e) {
            throw new AssertionError(name + " did not complete successfully: " + e, e);
        }
    }
}

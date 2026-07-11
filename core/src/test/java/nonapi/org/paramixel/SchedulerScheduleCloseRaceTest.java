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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler schedule-close race")
@SuppressWarnings("removal")
class SchedulerScheduleCloseRaceTest {

    private static final int ITERATIONS = 50;

    @Test
    @Timeout(30)
    @DisplayName("concurrent schedule and close leave every submitted descriptor terminal and published once")
    void concurrentScheduleAndCloseLeaveEverySubmittedDescriptorTerminalAndPublishedOnce() throws Exception {
        for (var trial = 0; trial < ITERATIONS; trial++) {
            var scheduler = new Scheduler(1, 16);
            try {
                var startLatch = new CountDownLatch(1);
                var descriptors = new ConcurrentLinkedQueue<MutableDescriptor>();
                var futures = new ConcurrentLinkedQueue<CompletableFuture<Descriptor>>();
                var publishedCounts = new ConcurrentLinkedQueue<AtomicInteger>();
                var submitterDone = new CountDownLatch(3);

                // 3 submitter threads racing with 1 closer thread.
                for (var i = 0; i < 3; i++) {
                    new Thread(() -> {
                                try {
                                    startLatch.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                                for (var j = 0; j < 5; j++) {
                                    var leaf = Step.of("leaf", context -> {});
                                    var root = new DescriptorBuilder().discover(leaf);
                                    root.freeze();
                                    var publishedRef = new AtomicInteger();
                                    var callback = new Scheduler.ExecutionCallback() {
                                        @Override
                                        public void onExecutionStart() {}

                                        @Override
                                        public void onExecutionComplete(final Throwable error) {
                                            publishedRef.incrementAndGet();
                                        }
                                    };
                                    var context = newContext(scheduler, root);
                                    try {
                                        var future = scheduler.schedule(root, ExecutionMode.RUN, context, callback);
                                        descriptors.add(root);
                                        futures.add(future);
                                        publishedCounts.add(publishedRef);
                                    } catch (Exception ignored) {
                                        // Schedule may fail during close race; that's valid.
                                    }
                                }
                                submitterDone.countDown();
                            })
                            .start();
                }

                // Closer thread.
                var closerThread = new Thread(() -> {
                    try {
                        startLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    scheduler.close();
                });
                closerThread.start();

                startLatch.countDown();

                // Wait for all submitters and closer.
                submitterDone.await(10, TimeUnit.SECONDS);
                closerThread.join(5_000);

                // Idempotent close after all threads complete.
                scheduler.close();

                // Assertions: every returned future is done.
                for (var future : futures) {
                    assertThat(future.isDone())
                            .as("trial " + trial + ": every returned future must be done")
                            .isTrue();
                }

                // Every submitted descriptor is terminal.
                for (var descriptor : descriptors) {
                    assertThat(descriptor.isCompleted())
                            .as("trial " + trial + ": every submitted descriptor must be terminal")
                            .isTrue();
                }

                // No descriptor is published more than once.
                for (var count : publishedCounts) {
                    assertThat(count.get())
                            .as("trial " + trial + ": no descriptor should be published more than once")
                            .isLessThanOrEqualTo(1);
                }

                // queuePermits invariant: available permits never exceed queueCapacity.
                var queuePermitsField = Scheduler.class.getDeclaredField("queuePermits");
                queuePermitsField.setAccessible(true);
                var queuePermits = (Semaphore) queuePermitsField.get(scheduler);
                assertThat(queuePermits.availablePermits())
                        .as("trial " + trial + ": available permits must not exceed queue capacity")
                        .isLessThanOrEqualTo(scheduler.queueCapacity());
            } finally {
                scheduler.close();
            }
        }
    }

    private static ConcreteContext newContext(final Scheduler scheduler, final MutableDescriptor root) {
        return new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());
    }
}

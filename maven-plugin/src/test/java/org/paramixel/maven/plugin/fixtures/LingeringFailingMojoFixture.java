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

package org.paramixel.maven.plugin.fixtures;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * Fails the run while leaving a non-daemon thread behind that retains the test classloader, so
 * {@code strictThreadLifecycle} reporting is triggered at the same time as a test failure.
 */
public final class LingeringFailingMojoFixture {

    /**
     * Released by tests to let the lingering thread terminate.
     */
    public static final CountDownLatch THREAD_RELEASE = new CountDownLatch(1);

    private static final AtomicReference<Thread> LINGERING_THREAD = new AtomicReference<>();

    private LingeringFailingMojoFixture() {}

    /**
     * Returns the lingering thread spawned by the last execution, if any.
     *
     * @return the lingering thread, or {@code null}
     */
    public static Thread lingeringThread() {
        return LINGERING_THREAD.get();
    }

    @Paramixel.Factory
    public static Action action() {
        return Sequential.builder("lingering-fail")
                .child(Step.of("spawn-lingering-thread", context -> {
                    var thread = new Thread(
                            () -> {
                                try {
                                    THREAD_RELEASE.await(60, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            "lingering-fixture-thread");
                    thread.setDaemon(false);
                    thread.setContextClassLoader(Thread.currentThread().getContextClassLoader());
                    LINGERING_THREAD.set(thread);
                    thread.start();
                }))
                .child(Step.of("fail", context -> {
                    throw new RuntimeException("boom");
                }))
                .build();
    }
}

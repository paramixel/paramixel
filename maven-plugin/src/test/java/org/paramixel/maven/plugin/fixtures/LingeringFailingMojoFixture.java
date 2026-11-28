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
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Sequential;
import org.paramixel.api.Task;

/**
 * Fixture that spawns a non-daemon thread and then fails, used to verify that the Maven mojo does not hang on worker
 * threads that outlive the run.
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

    /**
     * Builds a definition that spawns a lingering thread and then fails.
     *
     * @return the definition
     */
    @Paramixel.TaskFactory
    public static Task task() {
        return Sequential.builder(LingeringFailingMojoFixture.class, "lingering-fail")
                .step("spawn-lingering-thread")
                .step("fail")
                .build();
    }

    /**
     * Spawns a non-daemon thread that waits for the test to release it.
     *
     * @param context the execution context
     */
    @Paramixel.Id("spawn-lingering-thread")
    public static void spawnLingeringThread(final Context context) {
        final var thread = new Thread(
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
    }

    /**
     * Always fails.
     *
     * @param context the execution context
     */
    @Paramixel.Id("fail")
    public static void fail(final Context context) {
        throw new RuntimeException("boom");
    }
}

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

package examples;

import static org.paramixel.api.Tasks.sequential;

import java.util.concurrent.ThreadLocalRandom;
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates pausing a branch with a step.
 *
 * <p>A delay is ordinary user code: the step sleeps on the worker that owns the branch, so it holds whatever permit
 * and lock the branch holds, exactly like any other callback. A hook may declare checked exceptions, so
 * {@code Thread.sleep} needs no wrapping.
 */
public class SleepStepTest {

    private static final String ID_FIXED_PAUSE = "fixed pause";
    private static final String ID_RANDOM_PAUSE = "random pause";
    private static final String ID_NO_PAUSE = "no pause";

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.Factory
    public static Task factory() {
        return sequential(SleepStepTest.class, "sleep-steps")
                .step(ID_FIXED_PAUSE)
                .step(ID_RANDOM_PAUSE)
                .step(ID_NO_PAUSE)
                .build();
    }

    /**
     * Sleeps for a fixed interval.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_FIXED_PAUSE)
    public static void fixedPause(final Context context) throws InterruptedException {
        Thread.sleep(50L);
    }

    /**
     * Sleeps for a random interval.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_RANDOM_PAUSE)
    public static void randomPause(final Context context) throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextLong(10L, 50L));
    }

    /**
     * Yields without a meaningful pause.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_NO_PAUSE)
    public static void noPause(final Context context) throws InterruptedException {
        Thread.sleep(0L);
    }
}

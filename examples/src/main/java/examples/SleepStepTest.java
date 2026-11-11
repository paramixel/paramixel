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
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates pausing a branch with a step.
 *
 * <p>A delay is ordinary user code: the step sleeps on the worker that owns the branch, so it holds whatever permit and
 * lock the branch holds, exactly like any other callback.
 */
public class SleepStepTest {

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
        return sequential("sleep-steps")
                .step("fixed pause", () -> Thread.sleep(50L))
                .step(
                        "random pause",
                        () -> Thread.sleep(ThreadLocalRandom.current().nextLong(10L, 50L)))
                .step("no pause", () -> Thread.sleep(0L))
                .build();
    }
}

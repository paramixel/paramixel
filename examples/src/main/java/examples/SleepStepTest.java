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

import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

import java.util.concurrent.ThreadLocalRandom;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Demonstrates pausing a branch with a {@link org.paramixel.api.action.Step}.
 *
 * <p>A delay is ordinary user code: the step sleeps on the thread that owns the branch, so it holds
 * whatever pool permit and lock that branch holds, exactly like any other step.
 */
public class SleepStepTest {

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds a tree of fixed and random pauses.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        return sequential("sleep-steps")
                .child(step("fixed pause", context -> Thread.sleep(100L)))
                .child(step(
                        "random pause",
                        context -> Thread.sleep(ThreadLocalRandom.current().nextLong(50L, 150L))))
                .child(step("no pause", context -> Thread.sleep(0L)))
                .build();
    }
}

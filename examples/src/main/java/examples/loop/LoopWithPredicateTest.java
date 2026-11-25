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

package examples.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.Tasks.repeat;
import static org.paramixel.api.Tasks.sequential;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.Task;

/**
 * Demonstrates repetition with an explicit stop predicate.
 *
 * <p>The predicate is evaluated after each iteration with the iteration status, and the iteration bound still applies,
 * so a predicate that never becomes true cannot create unbounded work. The predicate is the one hook that takes a
 * second parameter.
 */
public class LoopWithPredicateTest {

    private static final String ID_CHECK = "check";
    private static final String ID_POLLED = "polled";
    private static final String ID_VALIDATE = "validate";

    private static final AtomicInteger pollCount = new AtomicInteger();

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        resetCounts();
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.Factory
    public static Task factory() {
        resetCounts();
        return sequential(LoopWithPredicateTest.class, "loop-predicate-example")
                .child(repeat("poll").maxIterations(10).until(ID_POLLED).step(ID_CHECK))
                .step(ID_VALIDATE)
                .build();
    }

    /**
     * Counts one poll.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_CHECK)
    public static void check(final Context context) {
        pollCount.incrementAndGet();
    }

    /**
     * Stops iterating once enough polls have run.
     *
     * @param context the execution context
     * @param status the status of the iteration that just finished
     * @return {@code true} when the loop should stop
     */
    @Paramixel.Id(ID_POLLED)
    public static boolean polled(final Context context, final Status status) {
        return pollCount.get() >= 4;
    }

    /**
     * Confirms the predicate stopped the loop at the expected count.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_VALIDATE)
    public static void validate(final Context context) {
        assertThat(pollCount.get()).isEqualTo(4);
    }

    private static void resetCounts() {
        pollCount.set(0);
    }
}

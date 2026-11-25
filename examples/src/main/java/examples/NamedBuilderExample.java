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

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.repeat;
import static org.paramixel.api.Tasks.sequential;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;
import org.paramixel.api.exception.FailException;

/**
 * Exercises the authoring vocabulary in one definition: named steps, a parallel group, repetition, a timeout, a
 * condition, an exclusive lock, lifecycle hooks, and assertion steps.
 *
 * <p>Every behaviour is a named static method, and every reference to it is a string. A group that needs a body and its
 * own children is written as {@code child(sequential(name).body(id)…)}; a group that is only a condition is written
 * {@code child(sequential(name).onlyIf(id)…)}.
 */
public class NamedBuilderExample {

    private static final String ID_STEP_DEMO = "step-demo";
    private static final String ID_BRANCH_1 = "branch-1";
    private static final String ID_BRANCH_2 = "branch-2";
    private static final String ID_BRANCH_3 = "branch-3";
    private static final String ID_REPEATED = "repeated";
    private static final String ID_SLEEP_DEMO = "sleep-demo";
    private static final String ID_ALWAYS = "always";
    private static final String ID_RUNS_WHEN_TRUE = "runs-when-true";
    private static final String ID_SERIALIZED = "serialized";
    private static final String ID_BEFORE_DEMO = "before-demo";
    private static final String ID_AFTER_DEMO = "after-demo";
    private static final String ID_TRUE_IS_TRUE = "true-is-true";
    private static final String ID_VALIDATE = "validate";

    private static final AtomicInteger stepCounter = new AtomicInteger();
    private static final AtomicInteger parallelCounter = new AtomicInteger();
    private static final AtomicInteger repeatCounter = new AtomicInteger();

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        reset();
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.Factory
    public static Task factory() {
        reset();

        return sequential(NamedBuilderExample.class, "NamedBuilderExample")
                .child(sequential("suite")
                        .step(ID_STEP_DEMO)
                        .child(parallel("parallel-demo", Pool.named("examples"))
                                .step(ID_BRANCH_1)
                                .step(ID_BRANCH_2)
                                .step(ID_BRANCH_3))
                        .child(repeat("repeat-demo").maxIterations(3).step(ID_REPEATED))
                        .step(ID_SLEEP_DEMO)
                        .timeout(Duration.ofSeconds(5))
                        .child(sequential("conditional-demo").onlyIf(ID_ALWAYS).step(ID_RUNS_WHEN_TRUE))
                        .isolated("test-lock")
                        .step(ID_SERIALIZED)
                        .before(ID_BEFORE_DEMO)
                        .after(ID_AFTER_DEMO)
                        .step(ID_TRUE_IS_TRUE))
                .step(ID_VALIDATE)
                .build();
    }

    /**
     * Counts one step.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_STEP_DEMO)
    public static void stepDemo(final Context context) {
        stepCounter.incrementAndGet();
    }

    /**
     * Counts one parallel branch.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_BRANCH_1)
    public static void branchOne(final Context context) {
        parallelCounter.incrementAndGet();
    }

    /**
     * Counts one parallel branch.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_BRANCH_2)
    public static void branchTwo(final Context context) {
        parallelCounter.incrementAndGet();
    }

    /**
     * Counts one parallel branch.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_BRANCH_3)
    public static void branchThree(final Context context) {
        parallelCounter.incrementAndGet();
    }

    /**
     * Counts one repetition.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_REPEATED)
    public static void repeated(final Context context) {
        repeatCounter.incrementAndGet();
    }

    /**
     * Sleeps briefly on the worker that owns the branch.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_SLEEP_DEMO)
    public static void sleepDemo(final Context context) throws InterruptedException {
        Thread.sleep(10L);
    }

    /**
     * The group condition; always true.
     *
     * @param context the execution context
     * @return {@code true}
     */
    @Paramixel.Id(ID_ALWAYS)
    public static boolean always(final Context context) {
        return true;
    }

    /**
     * Runs only when the enclosing group's condition holds.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_RUNS_WHEN_TRUE)
    public static void runsWhenTrue(final Context context) {
        // Intentionally empty
    }

    /**
     * Runs while the exclusive lock is held.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_SERIALIZED)
    public static void serialized(final Context context) {
        // Intentionally empty
    }

    /**
     * Runs before the enclosing group's children.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_BEFORE_DEMO)
    public static void beforeDemo(final Context context) {
        // Intentionally empty
    }

    /**
     * Runs after the enclosing group's children.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_AFTER_DEMO)
    public static void afterDemo(final Context context) {
        // Intentionally empty
    }

    /**
     * An assertion step.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TRUE_IS_TRUE)
    public static void trueIsTrue(final Context context) {
        final var actual = 1 == 1;
        if (!actual) {
            throw FailException.fail("expected true");
        }
    }

    /**
     * Verifies every counter.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_VALIDATE)
    public static void validate(final Context context) {
        assertThat(stepCounter.get()).isGreaterThanOrEqualTo(1);
        assertThat(parallelCounter.get()).isEqualTo(3);
        assertThat(repeatCounter.get()).isEqualTo(3);
    }

    private static void reset() {
        stepCounter.set(0);
        parallelCounter.set(0);
        repeatCounter.set(0);
    }
}

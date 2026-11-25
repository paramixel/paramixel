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

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Repeat;
import org.paramixel.api.Runner;
import org.paramixel.api.Sequential;
import org.paramixel.api.Task;

/**
 * Demonstrates bounded repetition: each subtree runs exactly the configured number of iterations.
 *
 * <p>Repeat is not retry. Replay is intentional, the bound is mandatory, and every iteration is a distinct attempt in
 * the result tree. A repeated task owns its context scope, so each iteration starts with an empty scope.
 */
public class LoopWithoutPredicateTest {

    private static final String ID_STEP_DEPENDENT = "step-dependent";
    private static final String ID_STEP_INDEPENDENT = "step-independent";
    private static final String ID_VALIDATE = "validate";

    private static final AtomicInteger dependentCount = new AtomicInteger();
    private static final AtomicInteger independentCount = new AtomicInteger();

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
    @Paramixel.TaskFactory
    public static Task factory() {
        resetCounts();
        return Sequential.builder(LoopWithoutPredicateTest.class, "loop-example")
                .step(Repeat.builder("dependent-loop").maxIterations(3).step(ID_STEP_DEPENDENT))
                .step(Repeat.builder("independent-loop").maxIterations(3).step(ID_STEP_INDEPENDENT))
                .step(ID_VALIDATE)
                .build();
    }

    /**
     * Counts one iteration of the dependent loop.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_STEP_DEPENDENT)
    public static void stepDependent(final Context context) {
        dependentCount.incrementAndGet();
    }

    /**
     * Counts one iteration of the independent loop.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_STEP_INDEPENDENT)
    public static void stepIndependent(final Context context) {
        independentCount.incrementAndGet();
    }

    /**
     * Confirms both loops ran their full bound.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_VALIDATE)
    public static void validate(final Context context) {
        assertThat(dependentCount.get()).isEqualTo(3);
        assertThat(independentCount.get()).isEqualTo(3);
    }

    private static void resetCounts() {
        dependentCount.set(0);
        independentCount.set(0);
    }
}

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

package examples.repeat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.Context.withInstance;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Repeat;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

/**
 * Demonstrates the {@link Repeat} action with dependent and independent
 * repetitions. Verifies that the child action executes exactly the
 * configured number of times.
 */
public class RepeatTest {

    private static final AtomicInteger dependentCount = new AtomicInteger();
    private static final AtomicInteger independentCount = new AtomicInteger();

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        resetCounts();
        Runner.defaultRunner().runAndExit(factory());
    }

    /**
     * Builds an action tree that exercises dependent and independent repeat.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        resetCounts();
        return Instance.builder("repeat-example", RepeatTest::new)
                .body(Sequence.builder("body")
                        .child(Step.of(
                                "dependent-repeat()", withInstance(RepeatTest.class, RepeatTest::dependentRepeat)))
                        .child(Step.of(
                                "independent-repeat()", withInstance(RepeatTest.class, RepeatTest::independentRepeat)))
                        .build())
                .build();
    }

    public RepeatTest() {
        // Intentionally empty
    }

    public void dependentRepeat() {
        var spec = Repeat.builder("dependent-repeat")
                .body(Step.of("step", context -> dependentCount.incrementAndGet()))
                .iterations(3)
                .build();
        var result = Runner.builder().build().run(spec);
        assertThat(result.descriptor().orElseThrow().isPassed()).isTrue();
        assertThat(dependentCount.get()).isEqualTo(3);
    }

    public void independentRepeat() {
        var spec = Repeat.builder("independent-repeat")
                .body(Step.of("step", context -> independentCount.incrementAndGet()))
                .iterations(3)
                .build();
        var result = Runner.builder().build().run(spec);
        assertThat(result.descriptor().orElseThrow().isPassed()).isTrue();
        assertThat(independentCount.get()).isEqualTo(3);
    }

    private static void resetCounts() {
        dependentCount.set(0);
        independentCount.set(0);
    }
}

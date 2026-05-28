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

import java.time.Duration;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Context;
import org.paramixel.api.action.Delay;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Spec;
import org.paramixel.api.action.Step;

/**
 * Demonstrates the {@link Delay} action with fixed and random durations.
 * Verifies that each delay action completes with PASSED status and that
 * random delays respect their configured bounds.
 */
public class DelayTest {

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    /**
     * Builds an action tree that exercises fixed and random delay variants.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Spec<?> factory() {
        return Sequential.of("delay-example")
                .child(Delay.of("fixed-milliseconds", 100))
                .child(Delay.of("fixed-duration", Duration.ofMillis(100)))
                .child(Delay.random("random-duration", 50, 150))
                .child(Delay.of("zero-duration", 0L))
                .child(Step.of("verify", ctx -> {
                    var context = (Context) ctx;
                    var children = context.descriptor().parent().orElseThrow().children();
                    assertThat(children).hasSize(5);
                    assertThat(children.subList(0, 4))
                            .allMatch(child -> child.metadata().status().isPassed());
                }));
    }
}

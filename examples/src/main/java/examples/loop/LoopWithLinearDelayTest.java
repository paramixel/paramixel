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
import static org.paramixel.api.Tasks.sequential;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;
import org.paramixel.api.support.Backoff;

/**
 * Demonstrates a fixed delay between iterations.
 *
 * <p>Delays are scheduled on a timer, not by blocking a worker thread, and they are cancellable: a run cancellation
 * stops a pending delay instead of waiting it out.
 */
public class LoopWithLinearDelayTest {

    private static final AtomicInteger iterationCount = new AtomicInteger();

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
        return sequential("loop-fixed-delay-example")
                .repeat(
                        "poll",
                        repeat -> repeat.maxIterations(3)
                                .delay(Backoff.fixed(Duration.ofMillis(50)))
                                .step("check", iterationCount::incrementAndGet))
                .step("validate", () -> assertThat(iterationCount.get()).isEqualTo(3))
                .build();
    }

    private static void resetCounts() {
        iterationCount.set(0);
    }
}

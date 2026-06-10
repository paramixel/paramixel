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

package examples.until;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.action.Until.until;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Demonstrates the {@link org.paramixel.api.action.Until} action with retry and polling patterns.
 * Verifies that the loop stops when satisfied or when maxIterations is exhausted.
 */
public class UntilTest {

    private static final AtomicInteger retryCount = new AtomicInteger();
    private static final AtomicInteger pollCount = new AtomicInteger();

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
     * Builds an action tree that exercises retry and poll.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        resetCounts();
        return sequential("until-example")
                .child(until("retry")
                        .body(step("attempt", context -> {
                            int attempt = retryCount.incrementAndGet();
                            if (attempt < 3) {
                                throw new RuntimeException("not yet (attempt " + attempt + ")");
                            }
                        }))
                        .maxIterations(5))
                .child(until("poll")
                        .body(step("check", context -> pollCount.incrementAndGet()))
                        .until(context -> pollCount.get() >= 4)
                        .maxIterations(10))
                .child(step("validate", context -> {
                    assertThat(retryCount.get()).isEqualTo(3);
                    assertThat(pollCount.get()).isEqualTo(4);
                }))
                .build();
    }

    private static void resetCounts() {
        retryCount.set(0);
        pollCount.set(0);
    }
}

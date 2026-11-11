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

package examples.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.Tasks.sequential;
import static org.paramixel.api.Tasks.task;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.support.Backoff;
import org.paramixel.api.support.RetryPolicy;

/**
 * Demonstrates retry with exponential backoff on a nested subtree.
 *
 * <p>The retry policy belongs to the {@code flaky} task, so the whole subtree is re-attempted. A later attempt succeeds,
 * the final aggregate is {@code PASSED}, and the earlier failed attempts stay visible as attempt nodes in the result.
 * The retried subtree has no owned instance, so it inherits nothing and reconstructs nothing.
 */
public class RetryTest2 {

    private static final AtomicInteger attempts = new AtomicInteger();

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        attempts.set(0);
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.Factory
    public static Task factory() {
        attempts.set(0);

        return task("retry-example")
                .independent()
                .child(sequential("flaky")
                        .retry(RetryPolicy.builder()
                                .maxAttempts(4)
                                .backoff(Backoff.exponential(Duration.ofMillis(10), Duration.ofMillis(80)))
                                .build())
                        .step("probe", () -> {
                            if (attempts.incrementAndGet() < 4) {
                                throw FailException.fail("flaky failure on attempt " + attempts.get());
                            }
                        }))
                .step("validate", () -> assertThat(attempts.get()).isEqualTo(4))
                .build();
    }
}

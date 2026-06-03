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
import static org.paramixel.api.Context.withInstance;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.support.Retry;
import org.paramixel.api.support.Retry.Policy;

/**
 * Demonstrates the {@link Retry} support with a fixed-delay policy. The test step
 * fails with {@link FailException} on the first two attempts and succeeds on the
 * third, verifying that the retry result reports pass with an attempt count of 3.
 */
public class RetryTest {

    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    /**
     * Builds a single-step action that retries a flaky operation using
     * {@link FailException} on early attempts.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        return Instance.builder("retry-example", RetryTest::new)
                .body(Step.of("retry()", withInstance(RetryTest.class, RetryTest::retry)))
                .build();
    }

    public RetryTest() {
        // Intentionally empty
    }

    public void retry() {
        Retry.Result result = Retry.of(Policy.fixed(Duration.ofMillis(100), Duration.ofSeconds(1)))
                .onRetry((attempt, cause) ->
                        System.out.println("Retry attempt " + attempt + " after: " + cause.getMessage()))
                .run(() -> {
                    int attempt = counter.incrementAndGet();
                    if (attempt < 3) {
                        FailException.fail("flaky failure on attempt " + attempt);
                    }
                });

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.attemptCount()).isEqualTo(3);
    }
}

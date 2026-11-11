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
import static org.paramixel.api.Tasks.forInstance;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.support.Backoff;
import org.paramixel.api.support.RetryPolicy;

/**
 * Demonstrates a bounded retry policy on an owned fixture.
 *
 * <p>Retry is not repeat: only a status the policy names triggers another attempt, attempts never overlap, and an owned
 * fixture is recreated for each attempt with its {@code after} callback running in between. Historical failures stay
 * visible in the result as attempt nodes.
 */
public class RetryTest {

    private static final AtomicInteger attempts = new AtomicInteger();
    private static final AtomicInteger instancesCreated = new AtomicInteger();

    private final int attemptNumber;

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        attempts.set(0);
        instancesCreated.set(0);
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
        instancesCreated.set(0);

        return forInstance("retry-example", RetryTest::new)
                .retry(RetryPolicy.builder()
                        .maxAttempts(3)
                        .backoff(Backoff.linear(Duration.ofMillis(25)))
                        .build())
                .step("retry", RetryTest::retry)
                .after(RetryTest::tearDown)
                .build();
    }

    private RetryTest() {
        attemptNumber = attempts.incrementAndGet();
        instancesCreated.incrementAndGet();
    }

    private void retry() {
        if (attemptNumber < 3) {
            throw FailException.fail("flaky failure on attempt " + attemptNumber);
        }
    }

    private void tearDown() {
        assertThat(instancesCreated.get()).isEqualTo(Math.min(3, attempts.get()));
    }
}

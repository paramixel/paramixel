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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Sequential;
import org.paramixel.api.Task;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.support.Backoff;
import org.paramixel.api.support.RetryPolicy;

/**
 * Demonstrates retry with exponential backoff on a nested subtree.
 *
 * <p>The retry policy belongs to the {@code flaky} task, so the whole subtree is re-attempted. Because a retried task
 * owns its context scope, every attempt starts with an empty scope: a value stored by a failed attempt never reaches
 * the next one. A later attempt succeeds, the final aggregate is {@code PASSED}, and the earlier failed attempts stay
 * visible as attempt nodes in the result.
 */
public class RetryTest2 {

    private static final String ID_PROBE = "probe";
    private static final String ID_VALIDATE = "validate";

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
    @Paramixel.TaskFactory
    public static Task factory() {
        attempts.set(0);

        return Sequential.builder(RetryTest2.class, "retry-example")
                .independent()
                .step(Sequential.builder("flaky")
                        .retry(RetryPolicy.builder()
                                .maxAttempts(4)
                                .backoff(Backoff.exponential(Duration.ofMillis(10), Duration.ofMillis(80)))
                                .build())
                        .step(ID_PROBE))
                .step(ID_VALIDATE)
                .build();
    }

    /**
     * Fails three times and passes on the fourth attempt.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_PROBE)
    public static void probe(final Context context) {
        final var attempt = attempts.incrementAndGet();
        if (attempt < 4) {
            throw FailException.fail("flaky failure on attempt " + attempt);
        }
        context.put("probe", "passed on attempt " + attempt);
    }

    /**
     * Confirms the retry ran exactly as configured.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_VALIDATE)
    public static void validate(final Context context) {
        assertThat(attempts.get()).isEqualTo(4);
    }
}

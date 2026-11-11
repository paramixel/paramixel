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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.Tasks.forInstance;
import static org.paramixel.api.Tasks.sequential;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.support.Backoff;
import org.paramixel.api.support.RetryPolicy;

/**
 * Retry and repeat semantics: distinct policies, attempt identity, and ownership rules.
 */
class RetryRepeatTest {

    private static Result run(final Task definition) {
        return Runner.builder().listener(Listener.NOOP).build().run(definition);
    }

    @Test
    @DisplayName("FAIL-12: a retry that eventually passes reports the task passed and keeps attempts")
    void retryPassesAfterFailures() {
        final AtomicInteger attempts = new AtomicInteger();
        final Task definition = sequential("root")
                .child(sequential("retried")
                        .retry(RetryPolicy.builder()
                                .maxAttempts(3)
                                .backoff(Backoff.fixed(Duration.ofMillis(5)))
                                .build())
                        .step("probe", () -> {
                            if (attempts.incrementAndGet() < 3) {
                                throw new IllegalStateException("not yet");
                            }
                        }))
                .build();

        final var result = run(definition);
        final var retried = result.root().orElseThrow().children().get(0);

        assertThat(attempts).hasValue(3);
        assertThat(retried.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(retried.children()).hasSize(3);
        assertThat(retried.children())
                .allSatisfy(attempt -> assertThat(attempt.kind()).isEqualTo(TaskResult.Kind.ATTEMPT));
        assertThat(retried.children().get(0).status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(retried.children().get(2).status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("FAIL-13: retry exhaustion keeps every attempt and reports failure")
    void retryExhaustion() {
        final AtomicInteger attempts = new AtomicInteger();
        final Task definition = sequential("root")
                .child(sequential("retried").retry(RetryPolicy.attempts(3)).step("probe", () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("always fails");
                }))
                .build();

        final var result = run(definition);
        final var retried = result.root().orElseThrow().children().get(0);

        assertThat(attempts).hasValue(3);
        assertThat(retried.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(retried.children()).hasSize(3);
        assertThat(result.exitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("retrying an instance task creates a fresh instance and finalizes each attempt")
    void retryRecreatesOwnedInstance() {
        final AtomicInteger instances = new AtomicInteger();
        final List<Integer> finalized = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger attempts = new AtomicInteger();

        final Task definition = forInstance("instance", () -> {
                    final var id = instances.incrementAndGet();
                    return new Fixture(id);
                })
                .retry(RetryPolicy.builder()
                        .maxAttempts(3)
                        .backoff(Backoff.fixed(Duration.ZERO))
                        .build())
                .step("probe", (Fixture fixture) -> {
                    if (attempts.incrementAndGet() < 2) {
                        throw new IllegalStateException("not yet");
                    }
                })
                .after((Fixture fixture) -> finalized.add(fixture.id()))
                .build();

        final var result = run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(instances).hasValue(2);
        assertThat(finalized).containsExactly(1, 2);
    }

    @Test
    @DisplayName("retry never overlaps attempts")
    void attemptsDoNotOverlap() {
        final AtomicInteger running = new AtomicInteger();
        final AtomicInteger maxRunning = new AtomicInteger();
        final Task definition = sequential("retried")
                .retry(RetryPolicy.attempts(3))
                .step("probe", () -> {
                    final var current = running.incrementAndGet();
                    maxRunning.accumulateAndGet(current, Math::max);
                    try {
                        Thread.sleep(5L);
                    } finally {
                        running.decrementAndGet();
                    }
                    throw new IllegalStateException("always fails");
                })
                .build();

        run(definition);

        assertThat(maxRunning).hasValue(1);
    }

    @Test
    @DisplayName("a business failure is retried but cancellation is never retried")
    void cancellationIsNotRetried() {
        final AtomicInteger attempts = new AtomicInteger();
        final Task definition = sequential("root")
                .child(sequential("canceled")
                        .retry(RetryPolicy.builder()
                                .maxAttempts(3)
                                .retryOn(Status.Kind.FAILED, Status.Kind.SKIPPED)
                                .build())
                        .step("probe", attempts::incrementAndGet))
                .build();

        final var result = run(definition);

        assertThat(attempts).hasValue(1);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("FAIL-14: repeat untilPassed stops on the first passing iteration")
    void repeatUntilPassed() {
        final AtomicInteger iterations = new AtomicInteger();
        final Task definition = sequential("root")
                .repeat(
                        "poll",
                        repeat -> repeat.maxIterations(5)
                                .untilPassed()
                                .delay(Backoff.fixed(Duration.ofMillis(1)))
                                .step("probe", () -> {
                                    if (iterations.incrementAndGet() < 3) {
                                        throw new IllegalStateException("not ready");
                                    }
                                }))
                .build();

        final var result = run(definition);
        final var repeated = result.root().orElseThrow().children().get(0);

        assertThat(iterations).hasValue(3);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(repeated.children()).hasSize(3);
        assertThat(repeated.children().get(0).status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(repeated.children().get(2).status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("repeat without a stop policy runs every iteration and aggregates severity")
    void repeatRunsAllIterations() {
        final AtomicInteger iterations = new AtomicInteger();
        final Task definition = sequential("root")
                .repeat(
                        "replay",
                        repeat -> repeat.maxIterations(3).step("probe", () -> {
                            if (iterations.incrementAndGet() == 2) {
                                throw new IllegalStateException("second iteration fails");
                            }
                        }))
                .build();

        final var result = run(definition);

        assertThat(iterations).hasValue(3);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.root().orElseThrow().children().get(0).children()).hasSize(3);
    }

    /**
     * A fixture that records its identity.
     */
    static final class Fixture {

        private final int id;

        Fixture(final int id) {
            this.id = id;
        }

        int id() {
            return id;
        }
    }
}

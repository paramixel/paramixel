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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.paramixel.api.Tasks.instance;
import static org.paramixel.api.Tasks.sequential;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.support.Backoff;
import org.paramixel.api.support.RepeatPolicy;
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

        final Task definition = instance("instance", () -> {
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
    @DisplayName("FAIL-15: a throwing repeat until-predicate fails the task instead of escaping the run")
    void throwingUntilPredicateFailsTask() {
        final AtomicInteger iterations = new AtomicInteger();
        final Task definition = sequential("root")
                .repeat(
                        "repeated",
                        repeat -> repeat.maxIterations(3)
                                .until((context, status) -> {
                                    throw new IllegalStateException("predicate boom");
                                })
                                .step("work", iterations::incrementAndGet))
                .build();

        final var result = run(definition);
        final var repeated = result.root().orElseThrow().children().get(0);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(repeated.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(iterations).hasValue(1);
        assertThat(repeated.children()).hasSize(1);
        assertThat(repeated.children().get(0).failures())
                .anySatisfy(failure -> assertThat(failure.message()).contains("predicate boom"));
    }

    @Test
    @DisplayName("FAIL-16: a repeat until-predicate runs on a worker thread and stops the repetition")
    void untilPredicateRunsOnWorkerThread() {
        final List<String> threads = Collections.synchronizedList(new ArrayList<>());
        final Task definition = sequential("root")
                .repeat(
                        "repeated",
                        repeat -> repeat.maxIterations(5)
                                .until((context, status) -> {
                                    threads.add(Thread.currentThread().getName());
                                    return context.attempt() >= 2;
                                })
                                .step("work", () -> {}))
                .build();

        final var result = run(definition);
        final var repeated = result.root().orElseThrow().children().get(0);

        assertThat(repeated.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(repeated.children()).hasSize(2);
        assertThat(threads).hasSize(2).allMatch(name -> name.startsWith("paramixel-worker-"));
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

    @Test
    @DisplayName("retry triggers are limited to FAILED and SKIPPED; a passing outcome is never retried")
    void retryTriggersAreLimitedToNonSuccessOutcomes() {
        assertThat(RetryPolicy.RETRYABLE_KINDS).containsExactlyInAnyOrder(Status.Kind.FAILED, Status.Kind.SKIPPED);
        assertThatThrownBy(() -> RetryPolicy.builder().retryOn(Status.Kind.PASSED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PASSED");
        assertThatThrownBy(() -> RetryPolicy.builder().retryOn(Status.Kind.FAILED, Status.Kind.PASSED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PASSED");
        assertThatThrownBy(() -> RetryPolicy.builder().retryOn(Status.Kind.RUNNING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RUNNING");
        assertThatThrownBy(() -> RetryPolicy.builder().retryOn(Status.Kind.ABORTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ABORTED");

        final var policy = RetryPolicy.builder()
                .maxAttempts(2)
                .retryOn(Status.Kind.FAILED, Status.Kind.SKIPPED)
                .build();

        assertThat(policy.retryOn()).containsExactlyInAnyOrder(Status.Kind.FAILED, Status.Kind.SKIPPED);
        assertThat(policy.retries(Status.Kind.PASSED)).isFalse();
    }

    @Test
    @DisplayName("delay helpers reject attempt and iteration numbers below two")
    void delayHelpersRejectInvalidInput() {
        final var retry = RetryPolicy.builder()
                .maxAttempts(3)
                .backoff(Backoff.fixed(Duration.ofMillis(7)))
                .build();
        final var repeat = RepeatPolicy.builder()
                .maxIterations(3)
                .delay(Backoff.fixed(Duration.ofMillis(9)))
                .build();

        assertThatThrownBy(() -> retry.delayBefore(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retry.delayBefore(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repeat.delayBefore(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repeat.delayBefore(1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(retry.delayBefore(2)).isEqualTo(Duration.ofMillis(7));
        assertThat(repeat.delayBefore(2)).isEqualTo(Duration.ofMillis(9));
    }

    @Test
    @DisplayName("an absent repeat stop predicate is an empty Optional, never null")
    void repeatUntilIsOptional() {
        final var withoutPredicate = RepeatPolicy.builder().maxIterations(2).build();
        final var withPredicate = RepeatPolicy.builder()
                .maxIterations(2)
                .until((context, status) -> status.isPassed())
                .build();

        assertThat(withoutPredicate.until()).isEmpty();
        assertThat(withPredicate.until()).isPresent();
    }

    @Test
    @DisplayName("a false condition on one attempt does not leave its skip reason on a later passing attempt")
    void retryDoesNotKeepStaleConditionReason() {
        final Task definition = sequential("root")
                .child(sequential("retried")
                        .retry(RetryPolicy.builder()
                                .maxAttempts(2)
                                .retryOn(Status.Kind.SKIPPED)
                                .build())
                        .onlyIf(context -> context.attempt() > 1)
                        .step("probe", () -> {}))
                .build();

        final var result = run(definition);
        final var retried = result.root().orElseThrow().children().get(0);

        assertThat(retried.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(retried.children()).hasSize(2);
        assertThat(retried.children().get(0).status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(retried.children().get(0).reason()).hasValue("condition was false");
        assertThat(retried.children().get(1).status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(retried.children().get(1).reason()).isEmpty();
        assertThat(retried.reason()).isEmpty();
    }

    @Test
    @DisplayName("a retry that fails after a false condition reports no skip reason")
    void retryFailureAfterFalseConditionHasNoSkipReason() {
        final Task definition = sequential("root")
                .child(sequential("retried")
                        .retry(RetryPolicy.builder()
                                .maxAttempts(2)
                                .retryOn(Status.Kind.FAILED, Status.Kind.SKIPPED)
                                .build())
                        .onlyIf(context -> context.attempt() > 1)
                        .step("probe", () -> {
                            throw new IllegalStateException("boom");
                        }))
                .build();

        final var result = run(definition);
        final var retried = result.root().orElseThrow().children().get(0);

        assertThat(retried.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(retried.reason()).isEmpty();
    }

    @Test
    @DisplayName("a repeat whose earlier iteration decides the outcome keeps that iteration's skip reason")
    void repeatKeepsReasonOfDecidingIteration() {
        final Task definition = sequential("root")
                .repeat(
                        "repeated",
                        repeat -> repeat.maxIterations(2)
                                .onlyIf(context -> context.attempt() > 1)
                                .step("probe", () -> {}))
                .build();

        final var result = run(definition);
        final var repeated = result.root().orElseThrow().children().get(0);

        assertThat(repeated.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(repeated.reason()).hasValue("condition was false");
        assertThat(repeated.children().get(1).reason()).isEmpty();
    }

    @Test
    @DisplayName("an exponential backoff with a zero base answers a high attempt number without iterating")
    void exponentialZeroBaseIsImmediate() {
        final var backoff = Backoff.exponential(Duration.ZERO, Duration.ofSeconds(1));

        final var delay = CompletableFuture.supplyAsync(() -> backoff.delayFor(Integer.MAX_VALUE));

        assertThat(delay).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(Duration.ZERO);
        assertThat(backoff.delayFor(1)).isEqualTo(Duration.ZERO);
        assertThat(Backoff.exponential(Duration.ofMillis(1), Duration.ofSeconds(1))
                        .delayFor(Integer.MAX_VALUE))
                .isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void exponentialOverflowSaturatesAtTheConfiguredMaximumIncludingNanos() {
        final var base = Duration.ofSeconds(Long.MAX_VALUE / 2 + 1);
        final var maximum = Duration.ofSeconds(Long.MAX_VALUE, 500);
        final var backoff = Backoff.exponential(base, maximum);

        assertThat(backoff.delayFor(1)).isEqualTo(base);
        assertThat(backoff.delayFor(2)).isEqualTo(maximum);
        assertThat(backoff.delayFor(Integer.MAX_VALUE)).isEqualTo(maximum);
    }

    @Test
    void linearOverflowSaturatesAtTheDefaultMaximum() {
        final var backoff = Backoff.linear(Duration.ofSeconds(Long.MAX_VALUE / 2 + 1));

        assertThat(backoff.delayFor(2)).isEqualTo(backoff.maximum());
        assertThat(backoff.delayFor(Integer.MAX_VALUE)).isEqualTo(backoff.maximum());
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

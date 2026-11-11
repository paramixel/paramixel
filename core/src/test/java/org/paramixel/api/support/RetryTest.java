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

package org.paramixel.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Retry")
class RetryTest {

    @Test
    @DisplayName("run succeeds on first attempt")
    void runSucceedsOnFirstAttempt() {
        var retry = Retry.of(Backoff.linear(Duration.ofMillis(10)), Duration.ofSeconds(1));

        var result = retry.run(() -> {});

        assertThat(result.successful()).isTrue();
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.hasExceptions()).isFalse();
        assertThat(result.exceptions()).isEmpty();
    }

    @Test
    @DisplayName("run retries and succeeds on second attempt")
    void runRetriesAndSucceedsOnSecondAttempt() {
        var attempt = new AtomicInteger(0);
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ofSeconds(1));

        var result = retry.run(() -> {
            if (attempt.incrementAndGet() == 1) {
                throw new RuntimeException("transient");
            }
        });

        assertThat(result.successful()).isTrue();
        assertThat(result.attemptCount()).isEqualTo(2);
        assertThat(result.hasExceptions()).isFalse();
    }

    @Test
    @DisplayName("run returns failure when retry predicate returns false")
    void runReturnsFailureWhenRetryPredicateReturnsFalse() {
        var retry =
                Retry.of(Backoff.linear(Duration.ZERO), Duration.ofSeconds(10)).retryOn(t -> false);

        var result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.successful()).isFalse();
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.hasExceptions()).isTrue();
        assertThat(result.exceptions()).hasSize(1);
        assertThat(result.exceptions().get(0)).hasMessage("fail");
    }

    @Test
    @DisplayName("run exhausts duration budget")
    void runExhaustsDurationBudget() {
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ofMillis(1));

        var result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.successful()).isFalse();
        assertThat(result.hasExceptions()).isTrue();
        assertThat(result.maximumDuration()).isEqualTo(Duration.ofMillis(1));
    }

    @Test
    @DisplayName("run with zero maximum duration fails immediately on exception")
    void runWithZeroMaximumDurationFailsOnException() {
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ZERO);

        var result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.successful()).isFalse();
        assertThat(result.attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("onRetry callbacks are invoked before each retry")
    void onRetryCallbacksAreInvoked() {
        var callbacks = new ArrayList<String>();
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ofSeconds(1))
                .onRetry((attempt, cause) -> callbacks.add("attempt-" + attempt + "-" + cause.getMessage()));

        var attempt = new AtomicInteger(0);
        retry.run(() -> {
            if (attempt.incrementAndGet() <= 2) {
                throw new RuntimeException("transient-" + attempt.get());
            }
        });

        assertThat(callbacks).containsExactly("attempt-2-transient-1", "attempt-3-transient-2");
    }

    @Test
    @DisplayName("multiple onRetry callbacks are invoked in order")
    void multipleOnRetryCallbacksAreInvokedInOrder() {
        var first = new ArrayList<String>();
        var second = new ArrayList<String>();
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ofSeconds(1))
                .onRetry((attempt, cause) -> first.add("a-" + attempt))
                .onRetry((attempt, cause) -> second.add("b-" + attempt));

        var attempt = new AtomicInteger(0);
        retry.run(() -> {
            if (attempt.incrementAndGet() == 1) {
                throw new RuntimeException("fail");
            }
        });

        assertThat(first).containsExactly("a-2");
        assertThat(second).containsExactly("b-2");
    }

    @Test
    @DisplayName("runAndThrow returns normally on success")
    void runAndThrowReturnsNormallyOnSuccess() throws Throwable {
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ZERO);

        retry.runAndThrow(() -> {});
    }

    @Test
    @DisplayName("runAndThrow throws last exception with earlier suppressed")
    void runAndThrowThrowsLastExceptionWithEarlierSuppressed() {
        var attempt = new AtomicInteger(0);
        var retry =
                Retry.of(Backoff.linear(Duration.ZERO), Duration.ofSeconds(1)).retryOn(t -> attempt.get() < 2);

        assertThatThrownBy(() -> retry.runAndThrow(() -> {
                    int n = attempt.incrementAndGet();
                    throw new RuntimeException("fail-" + n);
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("fail-2")
                .satisfies(t -> {
                    assertThat(t.getSuppressed()).hasSize(1);
                    assertThat(t.getSuppressed()[0]).hasMessage("fail-1");
                });
    }

    @Test
    @DisplayName("runAndThrow does not throw when result is successful")
    void runAndThrowDoesNotThrowWhenSuccessful() throws Throwable {
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ZERO);

        retry.runAndThrow(() -> {});
    }

    @Test
    @DisplayName("runAndThrow does not throw when result has no exceptions")
    void runAndThrowDoesNotThrowWhenNoExceptions() throws Throwable {
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ZERO);

        retry.runAndThrow(() -> {});
    }

    @Test
    @DisplayName("run rethrows OutOfMemoryError")
    void runRethrowsOutOfMemoryError() {
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ofSeconds(10));

        assertThatThrownBy(() -> retry.run(() -> {
                    throw new OutOfMemoryError("simulated oom");
                }))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated oom");
    }

    @Test
    @DisplayName("run rethrows InternalError")
    void runRethrowsInternalError() {
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ofSeconds(10));

        assertThatThrownBy(() -> retry.run(() -> {
                    throw new InternalError("simulated ie");
                }))
                .isInstanceOf(InternalError.class)
                .hasMessage("simulated ie");
    }

    @Test
    @DisplayName("run captures StackOverflowError and may retry")
    void runCapturesStackOverflowError() {
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ZERO).retryOn(t -> false);

        var result = retry.run(() -> {
            throw new StackOverflowError("soe");
        });

        assertThat(result.successful()).isFalse();
        assertThat(result.hasExceptions()).isTrue();
        assertThat(result.exceptions().get(0)).isInstanceOf(StackOverflowError.class);
    }

    @Test
    @DisplayName("Result getMaximumDuration returns configured budget")
    void resultGetMaximumDurationReturnsConfiguredBudget() {
        var budget = Duration.ofSeconds(5);
        var retry = Retry.of(Backoff.linear(Duration.ofMillis(10)), budget);

        var result = retry.run(() -> {});

        assertThat(result.maximumDuration()).isEqualTo(budget);
    }

    @Test
    @DisplayName("Result getElapsedDuration returns positive duration")
    void resultGetElapsedDurationReturnsPositiveDuration() {
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ofSeconds(1));

        var result = retry.run(() -> {});

        assertThat(result.elapsedDuration()).isNotNull();
        assertThat(result.elapsedDuration().isNegative()).isFalse();
    }

    @Test
    @DisplayName("Result getException returns exception at index")
    void resultGetExceptionReturnsExceptionAtIndex() {
        var attempt = new AtomicInteger(0);
        var retry =
                Retry.of(Backoff.linear(Duration.ZERO), Duration.ofSeconds(1)).retryOn(t -> attempt.get() < 2);

        var result = retry.run(() -> {
            int n = attempt.incrementAndGet();
            throw new RuntimeException("fail-" + n);
        });

        assertThat(result.successful()).isFalse();
        assertThat(result.exception(0)).isPresent();
        assertThat(result.exception(0).get()).hasMessage("fail-1");
    }

    @Test
    @DisplayName("Result getException returns empty for negative index")
    void resultGetExceptionReturnsEmptyForNegativeIndex() {
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ZERO).retryOn(t -> false);

        var result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.exception(-1)).isEmpty();
    }

    @Test
    @DisplayName("Result getException returns empty for out-of-range index")
    void resultGetExceptionReturnsEmptyForOutOfRangeIndex() {
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ZERO).retryOn(t -> false);

        var result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.exception(99)).isEmpty();
    }

    @Test
    @DisplayName("Result getExceptions returns immutable list")
    void resultGetExceptionsReturnsImmutableList() {
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ZERO).retryOn(t -> false);

        var result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        var exceptions = result.exceptions();
        assertThatThrownBy(() -> exceptions.add(new RuntimeException("extra")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("retryOn custom predicate filters retryable exceptions")
    void retryOnCustomPredicateFiltersRetryableExceptions() {
        var attempt = new AtomicInteger(0);
        var retry =
                Retry.of(Backoff.linear(Duration.ZERO), Duration.ofSeconds(1)).retryOn(t -> t instanceof IOException);

        var result = retry.run(() -> {
            int n = attempt.incrementAndGet();
            if (n == 1) {
                throw new IOException("io fail");
            }
        });

        assertThat(result.successful()).isTrue();
        assertThat(result.attemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("retryOn custom predicate stops on non-matching exception")
    void retryOnCustomPredicateStopsOnNonMatchingException() {
        var attempt = new AtomicInteger(0);
        var retry =
                Retry.of(Backoff.linear(Duration.ZERO), Duration.ofSeconds(1)).retryOn(t -> t instanceof IOException);

        var result = retry.run(() -> {
            attempt.incrementAndGet();
            throw new RuntimeException("not io");
        });

        assertThat(result.successful()).isFalse();
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.exceptions().get(0)).hasMessage("not io");
    }

    @Test
    @DisplayName("run uses default predicate to stop retrying on StackOverflowError")
    void runUsesDefaultPredicateToStopRetryingOnStackOverflowError() {
        // No retryOn(...) override: exercises DEFAULT_RETRY_ON (t -> !(t instanceof Error))
        // against a StackOverflowError, which evaluates to false and stops the sequence.
        var retry = Retry.of(Backoff.linear(Duration.ZERO), Duration.ofSeconds(1));

        var result = retry.run(() -> {
            throw new StackOverflowError("soe");
        });

        assertThat(result.successful()).isFalse();
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.hasExceptions()).isTrue();
        assertThat(result.exceptions().get(0)).isInstanceOf(StackOverflowError.class);
    }

    @Test
    @DisplayName("run fails when elapsed wall-clock time exceeds the duration budget")
    void runFailsWhenElapsedExceedsDurationBudget() {
        // A 1-nanosecond budget is always exceeded by real elapsed time after a failed
        // attempt, so the remaining budget is strictly negative.
        var retry = Retry.of(Backoff.fixed(Duration.ZERO), Duration.ofNanos(1));

        var result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.successful()).isFalse();
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.hasExceptions()).isTrue();
    }

    @Test
    @DisplayName("run caps wait duration to remaining budget and sleeps before retry")
    void runCapsWaitDurationToRemainingBudgetAndSleeps() {
        // The wait far exceeds the budget; the retry loop caps it at the remaining
        // budget and performs a real (non-zero) sleep before the successful retry.
        var retry = Retry.of(Backoff.fixed(Duration.ofSeconds(30)), Duration.ofMillis(100));

        var attempt = new AtomicInteger(0);
        var result = retry.run(() -> {
            if (attempt.incrementAndGet() == 1) {
                throw new RuntimeException("transient");
            }
        });

        assertThat(result.successful()).isTrue();
        assertThat(result.attemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("run returns failure when retry sleep is interrupted")
    void runReturnsFailureWhenRetrySleepInterrupted() {
        // The on-retry callback interrupts the running thread before the wait sleep;
        // Thread.sleep observes the interrupt and the retry loop captures it.
        var current = Thread.currentThread();
        var retry = Retry.of(Backoff.fixed(Duration.ofSeconds(30)), Duration.ofSeconds(1))
                .onRetry((nextAttempt, cause) -> current.interrupt());

        try {
            var result = retry.run(() -> {
                throw new RuntimeException("fail");
            });

            assertThat(result.successful()).isFalse();
            assertThat(result.hasExceptions()).isTrue();
            // The original failure plus the InterruptedException captured by the retry loop.
            assertThat(result.exceptions()).hasSize(2);
            assertThat(result.exceptions().get(0)).hasMessage("fail");
            assertThat(result.exceptions().get(1)).isInstanceOf(InterruptedException.class);
        } finally {
            // Clear any lingering interrupt status left by the retry loop's re-interrupt.
            Thread.interrupted();
        }
    }
}

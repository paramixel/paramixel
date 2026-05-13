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

package org.paramixel.core.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.support.Retry.Policy;
import org.paramixel.core.support.Retry.Result;

@DisplayName("Retry")
class RetryTest {

    @Test
    @DisplayName("of rejects null policy")
    void ofRejectsNullPolicy() {
        assertThatThrownBy(() -> Retry.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("policy must not be null");
    }

    @Test
    @DisplayName("retryOn returns this for method chaining")
    void retryOnReturnsThis() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        Retry result = retry.retryOn(t -> true);

        assertThat(result).isSameAs(retry);
    }

    @Test
    @DisplayName("retryOn rejects null predicate")
    void retryOnRejectsNullPredicate() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        assertThatThrownBy(() -> retry.retryOn(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate must not be null");
    }

    @Test
    @DisplayName("onRetry returns this for method chaining")
    void onRetryReturnsThis() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        Retry result = retry.onRetry((attempt, cause) -> {});

        assertThat(result).isSameAs(retry);
    }

    @Test
    @DisplayName("onRetry rejects null callback")
    void onRetryRejectsNullCallback() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        assertThatThrownBy(() -> retry.onRetry(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("callback must not be null");
    }

    @Test
    @DisplayName("run rejects null executable")
    void runRejectsNullThrowableRunnable() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        assertThatThrownBy(() -> retry.run(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("throwableRunnable must not be null");
    }

    @Test
    @DisplayName("run succeeds on first attempt")
    void runSucceedsOnFirstAttempt() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        Result result = retry.run(() -> {});

        assertThat(result.isPass()).isTrue();
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(result.getMaximumDuration()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("run returns succeeded result with no exceptions")
    void runReturnsSucceededResultWithNoExceptions() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        Result result = retry.run(() -> {});

        assertThat(result.isPass()).isTrue();
        assertThat(result.hasExceptions()).isFalse();
        assertThat(result.getExceptions()).isEmpty();
    }

    @Test
    @DisplayName("run returns elapsed duration")
    void runReturnsElapsedDuration() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        Result result = retry.run(() -> {});

        assertThat(result.getElapsedDuration()).isNotNull();
        assertThat(result.getElapsedDuration().toNanos()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("run retries and succeeds on second attempt")
    void runRetriesAndSucceedsOnSecondAttempt() {
        var counter = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        Result result = retry.run(() -> {
            if (counter.incrementAndGet() < 2) {
                throw new RuntimeException("transient failure");
            }
        });

        assertThat(result.isPass()).isTrue();
        assertThat(result.getAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("run retries and succeeds on third attempt")
    void runRetriesAndSucceedsOnThirdAttempt() {
        var counter = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        Result result = retry.run(() -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException("transient failure");
            }
        });

        assertThat(result.isPass()).isTrue();
        assertThat(result.getAttemptCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("run returns failed result when budget exhausted")
    void runReturnsFailedResultWhenBudgetExhausted() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(100)));

        Result result = retry.run(() -> {
            throw new RuntimeException("persistent failure");
        });

        assertThat(result.isPass()).isFalse();
        assertThat(result.hasExceptions()).isTrue();
        assertThat(result.getAttemptCount()).isGreaterThan(1);
    }

    @Test
    @DisplayName("run captures all exceptions from failed attempts")
    void runCapturesAllExceptionsFromFailedAttempts() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(100)));

        Result result = retry.run(() -> {
            throw new RuntimeException("persistent failure");
        });

        assertThat(result.getExceptions()).isNotEmpty();
        assertThat(result.getExceptions().get(0).getMessage()).isEqualTo("persistent failure");
        assertThat(result.getExceptions().get(result.getExceptions().size() - 1).getMessage())
                .isEqualTo("persistent failure");
    }

    @Test
    @DisplayName("run rethrows Error immediately")
    void runRethrowsErrorImmediately() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        assertThatThrownBy(() -> retry.run(() -> {
                    throw new OutOfMemoryError("simulated oom");
                }))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated oom");
    }

    @Test
    @DisplayName("run does not retry on Error")
    void runDoesNotRetryOnError() {
        var counter = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        assertThatThrownBy(() -> retry.run(() -> {
                    counter.incrementAndGet();
                    throw new OutOfMemoryError("simulated oom");
                }))
                .isInstanceOf(OutOfMemoryError.class);

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("run does not retry when predicate returns false")
    void runDoesNotRetryWhenPredicateReturnsFalse() {
        var counter = new AtomicInteger(0);
        Retry retry =
                Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10))).retryOn(t -> false);

        Result result = retry.run(() -> {
            counter.incrementAndGet();
            throw new RuntimeException("non-retryable");
        });

        assertThat(result.isPass()).isFalse();
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("run retries when predicate returns true")
    void runRetriesWhenPredicateReturnsTrue() {
        var counter = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(200)))
                .retryOn(t -> t instanceof RuntimeException);

        Result result = retry.run(() -> {
            counter.incrementAndGet();
            throw new RuntimeException("retryable");
        });

        assertThat(result.isPass()).isFalse();
        assertThat(result.getAttemptCount()).isGreaterThan(1);
        assertThat(counter.get()).isGreaterThan(1);
    }

    @Test
    @DisplayName("onRetry callback is invoked before each retry")
    void onRetryCallbackInvokedBeforeEachRetry() {
        var attempts = new ArrayList<Integer>();
        var counter = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(200)))
                .onRetry((attempt, cause) -> attempts.add(attempt));

        retry.run(() -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException("transient failure");
            }
        });

        assertThat(attempts).containsExactly(2, 3);
    }

    @Test
    @DisplayName("onRetry callback receives attempt number and cause")
    void onRetryCallbackReceivesAttemptAndCause() {
        var messages = new ArrayList<String>();
        var counter = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(200)))
                .onRetry((attempt, cause) -> messages.add(attempt + ":" + cause.getMessage()));

        retry.run(() -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException("transient failure");
            }
        });

        assertThat(messages).containsExactly("2:transient failure", "3:transient failure");
    }

    @Test
    @DisplayName("onRetry callback is not invoked on success")
    void onRetryCallbackNotInvokedOnSuccess() {
        var attempts = new ArrayList<Integer>();
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)))
                .onRetry((attempt, cause) -> attempts.add(attempt));

        retry.run(() -> {});

        assertThat(attempts).isEmpty();
    }

    @Test
    @DisplayName("multiple onRetry callbacks are invoked in registration order")
    void multipleOnRetryCallbacksInvokedInOrder() {
        var log = new ArrayList<String>();
        var counter = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(200)))
                .onRetry((attempt, cause) -> log.add("first:" + attempt))
                .onRetry((attempt, cause) -> log.add("second:" + attempt));

        retry.run(() -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException("transient failure");
            }
        });

        assertThat(log).containsExactly("first:2", "second:2", "first:3", "second:3");
    }

    @Test
    @DisplayName("fixed policy escalates linearly")
    void fixedPolicyEscalatesLinearly() {
        Policy policy = Policy.fixed(Duration.ofMillis(100), Duration.ofSeconds(10));

        assertThat(policy.waitDuration(1, null)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.waitDuration(2, null)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.waitDuration(3, null)).isEqualTo(Duration.ofMillis(300));
        assertThat(policy.waitDuration(4, null)).isEqualTo(Duration.ofMillis(400));
        assertThat(policy.waitDuration(5, null)).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    @DisplayName("exponential policy escalates exponentially")
    void exponentialPolicyEscalatesExponentially() {
        Policy policy = Policy.exponential(Duration.ofMillis(100), Duration.ofSeconds(10));

        assertThat(policy.waitDuration(1, null)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.waitDuration(2, null)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.waitDuration(3, null)).isEqualTo(Duration.ofMillis(400));
        assertThat(policy.waitDuration(4, null)).isEqualTo(Duration.ofMillis(800));
        assertThat(policy.waitDuration(5, null)).isEqualTo(Duration.ofMillis(1600));
        assertThat(policy.waitDuration(6, null)).isEqualTo(Duration.ofMillis(3200));
    }

    @Test
    @DisplayName("policy getMaximumDuration returns configured budget")
    void policyGetMaximumDurationReturnsConfiguredBudget() {
        Policy fixed = Policy.fixed(Duration.ofMillis(100), Duration.ofSeconds(30));
        Policy exponential = Policy.exponential(Duration.ofMillis(100), Duration.ofSeconds(5));

        assertThat(fixed.getMaximumDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(exponential.getMaximumDuration()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Policy.fixed rejects null initialDelay")
    void policyFixedRejectsNullInitialDelay() {
        assertThatThrownBy(() -> Policy.fixed(null, Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("initialDelay must not be null");
    }

    @Test
    @DisplayName("Policy.fixed rejects null maximumDuration")
    void policyFixedRejectsNullMaximumDuration() {
        assertThatThrownBy(() -> Policy.fixed(Duration.ofMillis(100), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("maximumDuration must not be null");
    }

    @Test
    @DisplayName("Policy.fixed rejects negative initialDelay")
    void policyFixedRejectsNegativeInitialDelay() {
        assertThatThrownBy(() -> Policy.fixed(Duration.ofMillis(-1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("initialDelay must not be negative");
    }

    @Test
    @DisplayName("Policy.fixed rejects negative maximumDuration")
    void policyFixedRejectsNegativeMaximumDuration() {
        assertThatThrownBy(() -> Policy.fixed(Duration.ZERO, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maximumDuration must not be negative");
    }

    @Test
    @DisplayName("Policy.fixed rejects initialDelay greater than maximumDuration")
    void policyFixedRejectsInitialGreaterThanMax() {
        assertThatThrownBy(() -> Policy.fixed(Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("initialDelay must not be greater than maximumDuration");
    }

    @Test
    @DisplayName("Policy.exponential rejects null initialDelay")
    void policyExponentialRejectsNullInitialDelay() {
        assertThatThrownBy(() -> Policy.exponential(null, Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("initialDelay must not be null");
    }

    @Test
    @DisplayName("Policy.exponential rejects null maximumDuration")
    void policyExponentialRejectsNullMaximumDuration() {
        assertThatThrownBy(() -> Policy.exponential(Duration.ofMillis(100), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("maximumDuration must not be null");
    }

    @Test
    @DisplayName("Policy.exponential rejects negative initialDelay")
    void policyExponentialRejectsNegativeInitialDelay() {
        assertThatThrownBy(() -> Policy.exponential(Duration.ofMillis(-1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("initialDelay must not be negative");
    }

    @Test
    @DisplayName("Policy.exponential rejects negative maximumDuration")
    void policyExponentialRejectsNegativeMaximumDuration() {
        assertThatThrownBy(() -> Policy.exponential(Duration.ZERO, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maximumDuration must not be negative");
    }

    @Test
    @DisplayName("Policy.exponential rejects initialDelay greater than maximumDuration")
    void policyExponentialRejectsInitialGreaterThanMax() {
        assertThatThrownBy(() -> Policy.exponential(Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("initialDelay must not be greater than maximumDuration");
    }

    @Test
    @DisplayName("Policy.fixed allows zero delays with zero budget")
    void policyFixedAllowsZeroDelaysWithZeroBudget() {
        Policy policy = Policy.fixed(Duration.ZERO, Duration.ZERO);

        assertThat(policy.waitDuration(1, null)).isEqualTo(Duration.ZERO);
        assertThat(policy.getMaximumDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Policy.exponential allows zero delays with zero budget")
    void policyExponentialAllowsZeroDelaysWithZeroBudget() {
        Policy policy = Policy.exponential(Duration.ZERO, Duration.ZERO);

        assertThat(policy.waitDuration(1, null)).isEqualTo(Duration.ZERO);
        assertThat(policy.getMaximumDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("zero budget results in single attempt with no retries")
    void zeroBudgetResultsInSingleAttemptWithNoRetries() {
        var counter = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        Result result = retry.run(() -> {
            counter.incrementAndGet();
            throw new RuntimeException("failure");
        });

        assertThat(result.isPass()).isFalse();
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("zero budget succeeds on first attempt")
    void zeroBudgetSucceedsOnFirstAttempt() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        Result result = retry.run(() -> {});

        assertThat(result.isPass()).isTrue();
        assertThat(result.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("run throws on second call")
    void runThrowsOnSecondCall() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        retry.run(() -> {});

        assertThatThrownBy(() -> retry.run(() -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Retry has already run");
    }

    @Test
    @DisplayName("hasRun returns false before run")
    void hasRunReturnsFalseBeforeRun() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        assertThat(retry.hasRun()).isFalse();
    }

    @Test
    @DisplayName("hasRun returns true after run")
    void hasRunReturnsTrueAfterRun() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        retry.run(() -> {});

        assertThat(retry.hasRun()).isTrue();
    }

    @Test
    @DisplayName("hasRun returns false after reset")
    void hasRunReturnsFalseAfterReset() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        retry.run(() -> {});
        retry.reset();

        assertThat(retry.hasRun()).isFalse();
    }

    @Test
    @DisplayName("reset preserves configuration and allows re-run")
    void resetPreservesConfigurationAndAllowsReRun() {
        var counter = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(100)))
                .onRetry((attempt, cause) -> counter.incrementAndGet());

        retry.run(() -> {
            throw new RuntimeException("failure");
        });
        assertThat(retry.hasRun()).isTrue();

        retry.reset();
        assertThat(retry.hasRun()).isFalse();

        Result result = retry.run(() -> {
            throw new RuntimeException("failure");
        });
        assertThat(result.isPass()).isFalse();
        assertThat(result.getAttemptCount()).isGreaterThan(1);
    }

    @Test
    @DisplayName("reset returns this for method chaining")
    void resetReturnsThis() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        Retry result = retry.reset();

        assertThat(result).isSameAs(retry);
    }

    @Test
    @DisplayName("clear removes callbacks and resets state")
    void clearRemovesCallbacksAndResetsState() {
        var log = new ArrayList<String>();
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(100)))
                .onRetry((attempt, cause) -> log.add("callback"));

        retry.run(() -> {
            throw new RuntimeException("failure");
        });
        assertThat(log).isNotEmpty();

        retry.clear();
        assertThat(retry.hasRun()).isFalse();

        log.clear();
        retry.run(() -> {
            throw new RuntimeException("failure");
        });
        assertThat(log).isEmpty();
    }

    @Test
    @DisplayName("clear returns this for method chaining")
    void clearReturnsThis() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        Retry result = retry.clear();

        assertThat(result).isSameAs(retry);
    }

    @Test
    @DisplayName("runAndThrow does nothing on success")
    void runAndThrowDoesNothingOnSuccess() throws Throwable {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        retry.runAndThrow(() -> {});
    }

    @Test
    @DisplayName("runAndThrow rethrows last exception on exhaustion")
    void runAndThrowRethrowsLastExceptionOnExhaustion() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(100)));

        assertThatThrownBy(() -> retry.runAndThrow(() -> {
                    throw new RuntimeException("persistent failure");
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("persistent failure");
    }

    @Test
    @DisplayName("runAndThrow adds earlier exceptions as suppressed")
    void runAndThrowAddsEarlierExceptionsAsSuppressed() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(100)));

        assertThatThrownBy(() -> retry.runAndThrow(() -> {
                    throw new RuntimeException("failure");
                }))
                .satisfies(t -> assertThat(t.getSuppressed().length).isGreaterThan(0));
    }

    @Test
    @DisplayName("runAndThrow propagates Error")
    void runAndThrowPropagatesError() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        assertThatThrownBy(() -> retry.runAndThrow(() -> {
                    throw new OutOfMemoryError("simulated oom");
                }))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated oom");
    }

    @Test
    @DisplayName("runAndThrow rejects null executable")
    void runAndThrowRejectsNullThrowableRunnable() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        assertThatThrownBy(() -> retry.runAndThrow(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("throwableRunnable must not be null");
    }

    @Test
    @DisplayName("run restores interrupt flag on InterruptedException during backoff")
    void runRestoresInterruptFlagOnInterruptDuringBackoff() throws Exception {
        var counter = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ofSeconds(10), Duration.ofSeconds(30)));

        Thread callerThread = Thread.currentThread();
        var interruptThread = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            callerThread.interrupt();
        });
        interruptThread.start();

        try {
            Result result = retry.run(() -> {
                counter.incrementAndGet();
                throw new RuntimeException("transient failure");
            });

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(result.isPass()).isFalse();
            assertThat(result.getAttemptCount()).isEqualTo(1);
        } finally {
            Thread.interrupted();
            interruptThread.join(1000);
        }
    }

    @Test
    @DisplayName("Result getException returns empty for negative index")
    void resultGetExceptionReturnsEmptyForNegativeIndex() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        Result result = retry.run(() -> {});

        assertThat(result.getException(-1)).isEmpty();
    }

    @Test
    @DisplayName("Result getException returns empty for out-of-range index")
    void resultGetExceptionReturnsEmptyForOutOfRangeIndex() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        Result result = retry.run(() -> {});

        assertThat(result.getException(0)).isEmpty();
    }

    @Test
    @DisplayName("Result getExceptions returns immutable list")
    void resultGetExceptionsReturnsImmutableList() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(100)));

        Result result = retry.run(() -> {
            throw new RuntimeException("failure");
        });

        assertThatThrownBy(() -> result.getExceptions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Result getException returns captured exception by index")
    void resultGetExceptionReturnsCapturedExceptionByIndex() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(100)));

        Result result = retry.run(() -> {
            throw new RuntimeException("failure");
        });

        assertThat(result.getException(0)).isPresent();
    }

    @Test
    @DisplayName("run with backoff delay respects budget")
    void runWithBackoffDelayRespectsBudget() {
        var counter = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ofMillis(50), Duration.ofMillis(200)));

        Result result = retry.run(() -> {
            counter.incrementAndGet();
            throw new RuntimeException("persistent failure");
        });

        assertThat(result.isPass()).isFalse();
        assertThat(counter.get()).isGreaterThan(1);
        assertThat(result.getElapsedDuration()).isGreaterThanOrEqualTo(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("exponential backoff caps delay at remaining budget")
    void exponentialBackoffCapsDelayAtRemainingBudget() {
        var counter = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.exponential(Duration.ofMillis(100), Duration.ofMillis(500)));

        Result result = retry.run(() -> {
            counter.incrementAndGet();
            throw new RuntimeException("persistent failure");
        });

        assertThat(result.isPass()).isFalse();
        assertThat(counter.get()).isGreaterThan(1);
    }

    @Test
    @DisplayName("exponential policy does not overflow for high attempt numbers")
    void exponentialPolicyDoesNotOverflowForHighAttemptNumbers() {
        Policy policy = Policy.exponential(Duration.ofMillis(1), Duration.ofSeconds(30));

        for (int attempt = 1; attempt <= 100; attempt++) {
            Duration waitDuration = policy.waitDuration(attempt, null);
            assertThat(waitDuration).isNotNull();
            assertThat(waitDuration.isNegative()).isFalse();
        }
    }

    @Test
    @DisplayName("exponential policy caps growth at attempt 63 and beyond")
    void exponentialPolicyCapsGrowthAt63AndBeyond() {
        Policy policy = Policy.exponential(Duration.ofMillis(1), Duration.ofSeconds(30));

        Duration atCap = policy.waitDuration(63, null);
        Duration beyondCap = policy.waitDuration(64, null);
        Duration farBeyond = policy.waitDuration(100, null);

        assertThat(atCap).isEqualTo(beyondCap);
        assertThat(atCap).isEqualTo(farBeyond);
        assertThat(atCap.isNegative()).isFalse();
    }

    @Test
    @DisplayName("custom policy implementation works")
    void customPolicyImplementationWorks() {
        var counter = new AtomicInteger(0);
        Policy constantPolicy = new Policy() {
            @Override
            public Duration waitDuration(int attempt, Throwable cause) {
                return Duration.ofMillis(10);
            }

            @Override
            public Duration getMaximumDuration() {
                return Duration.ofMillis(100);
            }
        };

        Retry retry = Retry.of(constantPolicy);

        Result result = retry.run(() -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException("transient failure");
            }
        });

        assertThat(result.isPass()).isTrue();
        assertThat(result.getAttemptCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Policy.fixed allows equal initialDelay and maximumDuration")
    void policyFixedAllowsEqualInitialAndMax() {
        Policy policy = Policy.fixed(Duration.ofSeconds(5), Duration.ofSeconds(5));

        assertThat(policy.waitDuration(1, null)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.getMaximumDuration()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("OutOfMemoryError is rethrown immediately")
    void outOfMemoryErrorIsRethrownImmediately() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1)));

        assertThatThrownBy(() -> retry.run(() -> {
                    throw new OutOfMemoryError("simulated oom");
                }))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated oom");
    }

    @Test
    @DisplayName("StackOverflowError is rethrown immediately")
    void stackOverflowErrorIsRethrownImmediately() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1)));

        assertThatThrownBy(() -> retry.run(() -> {
                    throw new StackOverflowError("simulated soe");
                }))
                .isInstanceOf(StackOverflowError.class)
                .hasMessage("simulated soe");
    }

    @Test
    @DisplayName("AssertionError is captured and not retried by default")
    void assertionErrorIsCapturedAndNotRetriedByDefault() {
        var attempts = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1)));

        Result result = retry.run(() -> {
            attempts.incrementAndGet();
            throw new AssertionError("expected true");
        });

        assertThat(result.isPass()).isFalse();
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(result.hasExceptions()).isTrue();
        assertThat(result.getExceptions()).hasSize(1);
        assertThat(result.getExceptions().get(0)).isInstanceOf(AssertionError.class);
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("AssertionError is retried when retryOn allows it")
    void assertionErrorIsRetriedWhenRetryOnAllowsIt() {
        var attempts = new AtomicInteger(0);
        Retry retry =
                Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1))).retryOn(t -> true);

        Result result = retry.run(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new AssertionError("expected true on attempt " + attempt);
            }
        });

        assertThat(result.isPass()).isTrue();
        assertThat(result.getAttemptCount()).isEqualTo(3);
        assertThat(attempts.get()).isEqualTo(3);
    }
}

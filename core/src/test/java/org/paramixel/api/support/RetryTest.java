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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.PolicyException;
import org.paramixel.api.support.Retry.Policy;
import org.paramixel.api.support.Retry.Result;

@DisplayName("Retry")
class RetryTest {

    @Test
    @DisplayName("run succeeds on first attempt")
    void runSucceedsOnFirstAttempt() {
        Retry retry = Retry.of(Policy.fixed(Duration.ofMillis(10), Duration.ofSeconds(1)));

        Result result = retry.run(() -> {});

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.hasExceptions()).isFalse();
        assertThat(result.exceptions()).isEmpty();
    }

    @Test
    @DisplayName("run retries and succeeds on second attempt")
    void runRetriesAndSucceedsOnSecondAttempt() {
        AtomicInteger attempt = new AtomicInteger(0);
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1)));

        Result result = retry.run(() -> {
            if (attempt.incrementAndGet() == 1) {
                throw new RuntimeException("transient");
            }
        });

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.attemptCount()).isEqualTo(2);
        assertThat(result.hasExceptions()).isFalse();
    }

    @Test
    @DisplayName("run returns failure when retry predicate returns false")
    void runReturnsFailureWhenRetryPredicateReturnsFalse() {
        Retry retry =
                Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10))).retryOn(t -> false);

        Result result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.hasExceptions()).isTrue();
        assertThat(result.exceptions()).hasSize(1);
        assertThat(result.exceptions().get(0)).hasMessage("fail");
    }

    @Test
    @DisplayName("run exhausts duration budget")
    void runExhaustsDurationBudget() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofMillis(1)));

        Result result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.hasExceptions()).isTrue();
        assertThat(result.maximumDuration()).isEqualTo(Duration.ofMillis(1));
    }

    @Test
    @DisplayName("run with zero maximum duration fails immediately on exception")
    void runWithZeroMaximumDurationFailsOnException() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        Result result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("onRetry callbacks are invoked before each retry")
    void onRetryCallbacksAreInvoked() {
        var callbacks = new ArrayList<String>();
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1)))
                .onRetry((attempt, cause) -> callbacks.add("attempt-" + attempt + "-" + cause.getMessage()));

        AtomicInteger attempt = new AtomicInteger(0);
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
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1)))
                .onRetry((attempt, cause) -> first.add("a-" + attempt))
                .onRetry((attempt, cause) -> second.add("b-" + attempt));

        AtomicInteger attempt = new AtomicInteger(0);
        retry.run(() -> {
            if (attempt.incrementAndGet() == 1) {
                throw new RuntimeException("fail");
            }
        });

        assertThat(first).containsExactly("a-2");
        assertThat(second).containsExactly("b-2");
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
    @DisplayName("reset allows retryOn to be called after run")
    void resetAllowsRetryOnToBeCalledAfterRun() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1)));
        retry.run(() -> {});

        Retry same = retry.reset();

        assertThat(same).isSameAs(retry);
        assertThat(retry.hasRun()).isFalse();

        Retry reconfigured = retry.retryOn(t -> t instanceof IOException);
        assertThat(reconfigured).isSameAs(retry);
    }

    @Test
    @DisplayName("reset allows run to be called after run")
    void resetAllowsRunToBeCalledAfterRun() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1)));
        retry.run(() -> {});

        assertThat(retry.hasRun()).isTrue();

        Retry same = retry.reset();

        assertThat(same).isSameAs(retry);
        assertThat(retry.hasRun()).isFalse();

        Result result = retry.run(() -> {});
        assertThat(result.isSuccessful()).isTrue();
    }

    @Test
    @DisplayName("reset preserves onRetry callbacks")
    void resetPreservesOnRetryCallbacks() {
        var callbacks = new ArrayList<String>();
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1)))
                .onRetry((attempt, cause) -> callbacks.add("callback-" + attempt));

        AtomicInteger attempt = new AtomicInteger(0);
        retry.run(() -> {
            if (attempt.incrementAndGet() == 1) {
                throw new RuntimeException("fail");
            }
        });

        retry.reset();
        attempt.set(0);

        retry.run(() -> {
            if (attempt.incrementAndGet() == 1) {
                throw new RuntimeException("fail");
            }
        });

        assertThat(callbacks).hasSize(2);
    }

    @Test
    @DisplayName("clear removes onRetry callbacks and resets state")
    void clearRemovesOnRetryCallbacksAndResetsState() {
        var callbacks = new ArrayList<String>();
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1)))
                .onRetry((attempt, cause) -> callbacks.add("callback"));

        AtomicInteger attempt = new AtomicInteger(0);
        retry.run(() -> {
            if (attempt.incrementAndGet() == 1) {
                throw new RuntimeException("fail");
            }
        });

        assertThat(callbacks).hasSize(1);

        Retry same = retry.clear();

        assertThat(same).isSameAs(retry);
        assertThat(retry.hasRun()).isFalse();

        attempt.set(0);
        retry.run(() -> {
            if (attempt.incrementAndGet() == 1) {
                throw new RuntimeException("fail");
            }
        });

        assertThat(callbacks).hasSize(1);
    }

    @Test
    @DisplayName("runAndThrow returns normally on success")
    void runAndThrowReturnsNormallyOnSuccess() throws Throwable {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        retry.runAndThrow(() -> {});
    }

    @Test
    @DisplayName("runAndThrow throws last exception with earlier suppressed")
    void runAndThrowThrowsLastExceptionWithEarlierSuppressed() {
        AtomicInteger attempt = new AtomicInteger(0);
        Retry retry =
                Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1))).retryOn(t -> attempt.get() < 2);

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
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        retry.runAndThrow(() -> {});
    }

    @Test
    @DisplayName("runAndThrow does not throw when result has no exceptions")
    void runAndThrowDoesNotThrowWhenNoExceptions() throws Throwable {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        retry.runAndThrow(() -> {});
    }

    @Test
    @DisplayName("run rethrows OutOfMemoryError")
    void runRethrowsOutOfMemoryError() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        assertThatThrownBy(() -> retry.run(() -> {
                    throw new OutOfMemoryError("simulated oom");
                }))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated oom");
    }

    @Test
    @DisplayName("run rethrows InternalError")
    void runRethrowsInternalError() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        assertThatThrownBy(() -> retry.run(() -> {
                    throw new InternalError("simulated ie");
                }))
                .isInstanceOf(InternalError.class)
                .hasMessage("simulated ie");
    }

    @Test
    @DisplayName("run captures StackOverflowError and may retry")
    void runCapturesStackOverflowError() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO)).retryOn(t -> false);

        Result result = retry.run(() -> {
            throw new StackOverflowError("soe");
        });

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.hasExceptions()).isTrue();
        assertThat(result.exceptions().get(0)).isInstanceOf(StackOverflowError.class);
    }

    @Test
    @DisplayName("Result getMaximumDuration returns configured budget")
    void resultGetMaximumDurationReturnsConfiguredBudget() {
        Duration budget = Duration.ofSeconds(5);
        Retry retry = Retry.of(Policy.fixed(Duration.ofMillis(10), budget));

        Result result = retry.run(() -> {});

        assertThat(result.maximumDuration()).isEqualTo(budget);
    }

    @Test
    @DisplayName("Result getElapsedDuration returns positive duration")
    void resultGetElapsedDurationReturnsPositiveDuration() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1)));

        Result result = retry.run(() -> {});

        assertThat(result.elapsedDuration()).isNotNull();
        assertThat(result.elapsedDuration().isNegative()).isFalse();
    }

    @Test
    @DisplayName("Result getException returns exception at index")
    void resultGetExceptionReturnsExceptionAtIndex() {
        AtomicInteger attempt = new AtomicInteger(0);
        Retry retry =
                Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1))).retryOn(t -> attempt.get() < 2);

        Result result = retry.run(() -> {
            int n = attempt.incrementAndGet();
            throw new RuntimeException("fail-" + n);
        });

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.exception(0)).isPresent();
        assertThat(result.exception(0).get()).hasMessage("fail-1");
    }

    @Test
    @DisplayName("Result getException returns empty for negative index")
    void resultGetExceptionReturnsEmptyForNegativeIndex() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO)).retryOn(t -> false);

        Result result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.exception(-1)).isEmpty();
    }

    @Test
    @DisplayName("Result getException returns empty for out-of-range index")
    void resultGetExceptionReturnsEmptyForOutOfRangeIndex() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO)).retryOn(t -> false);

        Result result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.exception(99)).isEmpty();
    }

    @Test
    @DisplayName("Result getExceptions returns immutable list")
    void resultGetExceptionsReturnsImmutableList() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO)).retryOn(t -> false);

        Result result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        List<Throwable> exceptions = result.exceptions();
        assertThatThrownBy(() -> exceptions.add(new RuntimeException("extra")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Policy.fixed escalates linearly")
    void policyFixedEscalatesLinearly() {
        Duration initialDelay = Duration.ofMillis(100);
        Policy policy = Policy.fixed(initialDelay, Duration.ofSeconds(10));

        assertThat(policy.waitDuration(1, new RuntimeException())).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.waitDuration(2, new RuntimeException())).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.waitDuration(3, new RuntimeException())).isEqualTo(Duration.ofMillis(300));
        assertThat(policy.maximumDuration()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Policy.exponential escalates exponentially")
    void policyExponentialEscalatesExponentially() {
        Duration initialDelay = Duration.ofMillis(100);
        Policy policy = Policy.exponential(initialDelay, Duration.ofSeconds(10));

        assertThat(policy.waitDuration(1, new RuntimeException())).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.waitDuration(2, new RuntimeException())).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.waitDuration(3, new RuntimeException())).isEqualTo(Duration.ofMillis(400));
        assertThat(policy.waitDuration(4, new RuntimeException())).isEqualTo(Duration.ofMillis(800));
        assertThat(policy.maximumDuration()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Policy.exponential returns zero for negative attempt")
    void policyExponentialReturnsZeroForNegativeAttempt() {
        Policy policy = Policy.exponential(Duration.ofMillis(100), Duration.ofSeconds(10));

        assertThat(policy.waitDuration(-1, new RuntimeException())).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Policy.fixed with zero delays succeeds immediately")
    void policyFixedWithZeroDelays() {
        Policy policy = Policy.fixed(Duration.ZERO, Duration.ofSeconds(1));

        assertThat(policy.waitDuration(1, new RuntimeException())).isEqualTo(Duration.ZERO);
        assertThat(policy.waitDuration(5, new RuntimeException())).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("retryOn custom predicate filters retryable exceptions")
    void retryOnCustomPredicateFiltersRetryableExceptions() {
        AtomicInteger attempt = new AtomicInteger(0);
        Retry retry =
                Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1))).retryOn(t -> t instanceof IOException);

        Result result = retry.run(() -> {
            int n = attempt.incrementAndGet();
            if (n == 1) {
                throw new IOException("io fail");
            }
        });

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.attemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("retryOn custom predicate stops on non-matching exception")
    void retryOnCustomPredicateStopsOnNonMatchingException() {
        AtomicInteger attempt = new AtomicInteger(0);
        Retry retry =
                Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(1))).retryOn(t -> t instanceof IOException);

        Result result = retry.run(() -> {
            attempt.incrementAndGet();
            throw new RuntimeException("not io");
        });

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.exceptions().get(0)).hasMessage("not io");
    }

    @Test
    @DisplayName("Policy.fixed with zero maximum duration creates valid policy")
    void policyFixedWithZeroMaximumDuration() {
        Policy policy = Policy.fixed(Duration.ZERO, Duration.ZERO);

        assertThat(policy.maximumDuration()).isEqualTo(Duration.ZERO);
        assertThat(policy.waitDuration(1, new RuntimeException())).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Policy.exponential with zero initial delay creates valid policy")
    void policyExponentialWithZeroInitialDelay() {
        Policy policy = Policy.exponential(Duration.ZERO, Duration.ZERO);

        assertThat(policy.maximumDuration()).isEqualTo(Duration.ZERO);
        assertThat(policy.waitDuration(1, new RuntimeException())).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("run continues when Policy throws PolicyException from waitDuration")
    void runContinuesWhenPolicyThrowsPolicyExceptionFromWaitDuration() {
        AtomicInteger attempt = new AtomicInteger(0);
        Policy policy = new Policy() {
            @Override
            public Duration waitDuration(int attempt, Throwable cause) {
                if (attempt == 1) {
                    throw new PolicyException("overflow", new ArithmeticException("too big"));
                }
                return Duration.ZERO;
            }

            @Override
            public Duration maximumDuration() {
                return Duration.ofSeconds(10);
            }
        };
        Retry retry = Retry.of(policy);

        Result result = retry.run(() -> {
            if (attempt.incrementAndGet() <= 2) {
                throw new RuntimeException("transient");
            }
        });

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.attemptCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("run continues when Policy throws PolicyException from getMaximumDuration")
    void runContinuesWhenPolicyThrowsPolicyExceptionFromGetMaximumDuration() {
        Policy policy = new Policy() {
            @Override
            public Duration waitDuration(int attempt, Throwable cause) {
                return Duration.ZERO;
            }

            @Override
            public Duration maximumDuration() {
                throw new PolicyException("overflow", new ArithmeticException("too big"));
            }
        };
        Retry retry = Retry.of(policy).retryOn(t -> false);

        Result result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.maximumDuration()).isEqualTo(Duration.ZERO);
        assertThat(result.attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("run propagates RuntimeException from Policy waitDuration")
    void runPropagatesRuntimeExceptionFromPolicyWaitDuration() {
        Policy policy = new Policy() {
            @Override
            public Duration waitDuration(int attempt, Throwable cause) {
                throw new IllegalStateException("unexpected");
            }

            @Override
            public Duration maximumDuration() {
                return Duration.ofSeconds(10);
            }
        };
        Retry retry = Retry.of(policy);

        assertThatThrownBy(() -> retry.run(() -> {
                    throw new RuntimeException("fail");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("unexpected");
    }

    @Test
    @DisplayName("run propagates RuntimeException from Policy getMaximumDuration")
    void runPropagatesRuntimeExceptionFromPolicyGetMaximumDuration() {
        Policy policy = new Policy() {
            @Override
            public Duration waitDuration(int attempt, Throwable cause) {
                return Duration.ZERO;
            }

            @Override
            public Duration maximumDuration() {
                throw new NullPointerException("unexpected");
            }
        };
        Retry retry = Retry.of(policy);

        assertThatThrownBy(() -> retry.run(() -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("unexpected");
    }

    @Test
    @DisplayName("retryOn throws IllegalStateException when called after run")
    void retryOnThrowsIllegalStateExceptionWhenCalledAfterRun() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));
        retry.run(() -> {});

        assertThatThrownBy(() -> retry.retryOn(t -> true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("retryOn is called after run");
    }

    @Test
    @DisplayName("run stops when Policy returns null from getMaximumDuration")
    void runStopsWhenPolicyReturnsNullFromGetMaximumDuration() {
        Policy policy = new Policy() {
            @Override
            public Duration waitDuration(int attempt, Throwable cause) {
                return Duration.ZERO;
            }

            @Override
            public Duration maximumDuration() {
                return null;
            }
        };
        Retry retry = Retry.of(policy).retryOn(t -> false);

        Result result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.maximumDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("run stops when Policy returns negative from getMaximumDuration")
    void runStopsWhenPolicyReturnsNegativeFromGetMaximumDuration() {
        Policy policy = new Policy() {
            @Override
            public Duration waitDuration(int attempt, Throwable cause) {
                return Duration.ZERO;
            }

            @Override
            public Duration maximumDuration() {
                return Duration.ofMillis(-1);
            }
        };
        Retry retry = Retry.of(policy).retryOn(t -> false);

        Result result = retry.run(() -> {
            throw new RuntimeException("fail");
        });

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.maximumDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("run uses zero wait when Policy returns null from waitDuration")
    void runUsesZeroWaitWhenPolicyReturnsNullFromWaitDuration() {
        Policy policy = new Policy() {
            @Override
            public Duration waitDuration(int attempt, Throwable cause) {
                return null;
            }

            @Override
            public Duration maximumDuration() {
                return Duration.ofSeconds(1);
            }
        };
        Retry retry = Retry.of(policy);

        AtomicInteger attempt = new AtomicInteger(0);
        Result result = retry.run(() -> {
            if (attempt.incrementAndGet() == 1) {
                throw new RuntimeException("fail");
            }
        });

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.attemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("run uses zero wait when Policy returns negative from waitDuration")
    void runUsesZeroWaitWhenPolicyReturnsNegativeFromWaitDuration() {
        Policy policy = new Policy() {
            @Override
            public Duration waitDuration(int attempt, Throwable cause) {
                return Duration.ofMillis(-1);
            }

            @Override
            public Duration maximumDuration() {
                return Duration.ofSeconds(1);
            }
        };
        Retry retry = Retry.of(policy);

        AtomicInteger attempt = new AtomicInteger(0);
        Result result = retry.run(() -> {
            if (attempt.incrementAndGet() == 1) {
                throw new RuntimeException("fail");
            }
        });

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.attemptCount()).isEqualTo(2);
    }
}

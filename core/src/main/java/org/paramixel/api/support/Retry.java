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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import nonapi.org.paramixel.support.Arguments;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.ThrowingRunnable;
import org.paramixel.api.exception.PolicyException;

/**
 * Retries a runnable operation with configurable backoff and a wall-clock duration budget.
 *
 * <p>This utility is intended for operations that may fail transiently and succeed on a subsequent attempt.
 * Callers provide a {@link Policy} that defines both the inter-attempt delay schedule and the total
 * wall-clock duration budget. When the elapsed time exceeds the budget, retry attempts stop.
 *
 * <p>This class is <strong>not thread-safe</strong>. Instances must not be shared across threads
 * or accessed concurrently. Typical usage confines a {@code Retry} instance to a single
 * method call or lifecycle scope.
 */
public final class Retry {

    private static final Predicate<Throwable> DEFAULT_RETRY_ON = t -> !(t instanceof Error);

    private static final int STATE_INITIAL = 0;
    private static final int STATE_RUNNING = 1;
    private static final int STATE_COMPLETED = 2;

    private final Policy backoffPolicy;
    private Predicate<Throwable> retryOn;
    private final List<BiConsumer<Integer, Throwable>> onRetryCallbacks;
    private int state;

    /**
     * Creates a retry sequence with the supplied backoff policy.
     *
     * <p>The policy defines both the delay schedule between attempts and the total wall-clock
     * duration budget. The default retry predicate retries on any throwable that is not an
     * {@link Error}.
     *
     * @param policy the backoff strategy defining inter-attempt delays and the duration budget
     * @return a new retry sequence
     * @throws NullPointerException if {@code policy} is {@code null}
     */
    public static Retry of(final Policy policy) {
        Objects.requireNonNull(policy, "policy is null");
        return new Retry(policy);
    }

    private Retry(final Policy policy) {
        this.backoffPolicy = policy;
        this.retryOn = DEFAULT_RETRY_ON;
        this.onRetryCallbacks = new ArrayList<>();
    }

    /**
     * Sets the predicate that determines whether a throwable is retryable.
     *
     * <p>When the predicate returns {@code false} for a given throwable, the retry sequence
     * stops immediately and the result reflects the failure. When the predicate returns
     * {@code true}, the next attempt proceeds (if the duration budget has not been exhausted).
     *
     * @param predicate the predicate that decides whether a throwable warrants a retry attempt
     * @return this retry sequence
     * @throws NullPointerException if {@code predicate} is {@code null}
     */
    public Retry retryOn(final Predicate<Throwable> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        if (state != STATE_INITIAL) {
            throw new IllegalStateException("retryOn is called after run");
        }
        this.retryOn = predicate;
        return this;
    }

    /**
     * Registers a callback invoked before each retry attempt.
     *
     * <p>The callback receives the 1-based attempt number about to be executed and the
     * throwable from the previous failed attempt. Multiple callbacks can be registered;
     * they are invoked in registration order.
     *
     * @param callback the callback invoked with the next attempt number and the previous failure
     * @return this retry sequence
     * @throws NullPointerException if {@code callback} is {@code null}
     * @throws IllegalStateException if this retry sequence has already run
     */
    public Retry onRetry(final BiConsumer<Integer, Throwable> callback) {
        Objects.requireNonNull(callback, "callback is null");
        if (state != STATE_INITIAL) {
            throw new IllegalStateException("onRetry is called after run");
        }
        onRetryCallbacks.add(callback);
        return this;
    }

    /**
     * Runs the supplied throwable runnable with retry behavior and returns the result.
     *
     * <p>On each failed attempt where the {@link #retryOn(Predicate) retry predicate} returns
     * {@code true} and the wall-clock duration budget has not been exhausted, the on-retry callbacks
     * are invoked and the backoff delay is applied before the next attempt. The delay is capped at
     * the remaining budget. A non-{@link StackOverflowError} {@link VirtualMachineError} is rethrown
     * immediately without retry; {@link StackOverflowError} and other {@link Error} subtypes are captured
     * and may be retried depending on the retry predicate.
     *
     * @param throwableRunnable the operation to run
     * @return the retry result describing success, attempt count, elapsed duration, and captured exceptions
     * @throws IllegalStateException if this retry sequence has already run
     * @throws VirtualMachineError if the throwableRunnable throws a non-{@code StackOverflowError}
     *     {@code VirtualMachineError}
     * @throws NullPointerException if {@code throwableRunnable} is {@code null}
     */
    public Result run(final ThrowingRunnable throwableRunnable) {
        Objects.requireNonNull(throwableRunnable, "throwableRunnable is null");
        if (state != STATE_INITIAL) {
            throw new IllegalStateException("Retry has already run");
        }
        state = STATE_RUNNING;

        List<BiConsumer<Integer, Throwable>> callbacks = List.copyOf(onRetryCallbacks);
        Duration maximumDuration = maximumDurationOrZero();
        long startNanos = System.nanoTime();
        var exceptions = new ArrayList<Throwable>();
        int attempt = 0;

        while (true) {
            attempt++;

            try {
                throwableRunnable.run();
                state = STATE_COMPLETED;
                return new Result(maximumDuration, attempt, elapsedDuration(startNanos), true, List.of());
            } catch (Throwable e) {
                UnrecoverableErrors.rethrowIfUnrecoverable(e);
                exceptions.add(e);

                if (!retryOn.test(e)) {
                    state = STATE_COMPLETED;
                    return new Result(
                            maximumDuration, attempt, elapsedDuration(startNanos), false, List.copyOf(exceptions));
                }

                Duration elapsed = elapsedDuration(startNanos);
                Duration remaining = maximumDuration.minus(elapsed);

                if (remaining.isZero() || remaining.isNegative()) {
                    state = STATE_COMPLETED;
                    return new Result(maximumDuration, attempt, elapsed, false, List.copyOf(exceptions));
                }

                for (var callback : callbacks) {
                    callback.accept(attempt + 1, e);
                }

                Duration waitDuration = waitDurationOrZero(attempt, e);
                if (waitDuration.compareTo(remaining) > 0) {
                    waitDuration = remaining;
                }

                if (!waitDuration.isZero()) {
                    try {
                        Thread.sleep(waitDuration.toMillis(), waitDuration.toNanosPart() % 1_000_000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        exceptions.add(ie);
                        state = STATE_COMPLETED;
                        return new Result(
                                maximumDuration, attempt, elapsedDuration(startNanos), false, List.copyOf(exceptions));
                    }
                }
            }
        }
    }

    private Duration maximumDurationOrZero() {
        try {
            Duration maximumDuration = backoffPolicy.maximumDuration();
            if (maximumDuration == null || maximumDuration.isNegative()) {
                return Duration.ZERO;
            }
            return maximumDuration;
        } catch (PolicyException e) {
            return Duration.ZERO;
        }
    }

    private Duration waitDurationOrZero(int attempt, Throwable cause) {
        try {
            Duration waitDuration = backoffPolicy.waitDuration(attempt, cause);
            if (waitDuration == null || waitDuration.isNegative()) {
                return Duration.ZERO;
            }
            return waitDuration;
        } catch (PolicyException e) {
            return Duration.ZERO;
        }
    }

    /**
     * Runs the supplied throwable runnable with retry behavior and rethrows the last captured
     * exception when the duration budget is exhausted or the retry predicate returns {@code false}.
     *
     * <p>Earlier exceptions are added to the last exception as suppressed throwables
     * in attempt order.
     *
     * @param throwableRunnable the operation to run
     * @throws Throwable the last captured exception when all retries are exhausted, with earlier exceptions suppressed
     * @throws IllegalStateException if this retry sequence has already run
     * @throws VirtualMachineError if the throwableRunnable throws a non-{@code StackOverflowError}
     *     {@code VirtualMachineError}
     * @throws NullPointerException if {@code throwableRunnable} is {@code null}
     */
    public void runAndThrow(final ThrowingRunnable throwableRunnable) throws Throwable {
        Result result = run(throwableRunnable);

        if (!result.isSuccessful() && result.hasExceptions()) {
            List<Throwable> exceptions = result.exceptions();
            Throwable lastException = exceptions.get(exceptions.size() - 1);
            for (int i = 0; i < exceptions.size() - 1; i++) {
                lastException.addSuppressed(exceptions.get(i));
            }
            throw lastException;
        }
    }

    /**
     * Returns whether this retry sequence has already been run.
     *
     * @return {@code true} when {@link #run(ThrowingRunnable)} or {@link #runAndThrow(ThrowingRunnable)} has already been called
     */
    public boolean hasRun() {
        return state != STATE_INITIAL;
    }

    /**
     * Marks this retry sequence as not yet run without removing registered on-retry callbacks.
     *
     * @return this retry sequence
     */
    public Retry reset() {
        state = STATE_INITIAL;
        return this;
    }

    /**
     * Removes all registered on-retry callbacks and resets execution state.
     *
     * @return this retry sequence
     */
    public Retry clear() {
        onRetryCallbacks.clear();
        retryOn = DEFAULT_RETRY_ON;
        state = STATE_INITIAL;
        return this;
    }

    private static Duration elapsedDuration(final long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    /**
     * Computes the delay duration before a retry attempt and provides the total wall-clock duration budget.
     *
     * <p>The {@code attempt} parameter is the 1-based number of the just-failed attempt.
     * The returned duration is the time to wait before the next attempt. The {@link Retry}
     * loop caps the wait duration at the remaining budget if it exceeds it.
     *
     * <p>If a policy computation fails — for example, due to {@link ArithmeticException arithmetic
     * overflow} in {@link java.time.Duration Duration} operations — implementations should throw
     * {@link PolicyException} rather than an unrelated {@link RuntimeException}. The {@link Retry}
     * loop catches {@code PolicyException} and treats the failed computation as zero, allowing
     * the retry sequence to continue without hanging. Other {@link RuntimeException} subtypes
     * propagate out of the retry loop, terminating the sequence.
     */
    public interface Policy {

        /**
         * The delay duration before the next retry attempt.
         *
         * @param attempt the 1-based number of the just-failed attempt
         * @param cause the throwable from the failed attempt
         * @return the duration to wait before the next attempt; negative values are treated as zero
         * @throws PolicyException if the duration computation fails
         */
        Duration waitDuration(int attempt, Throwable cause);

        /**
         * The total wall-clock duration budget for the retry sequence.
         *
         * @return the maximum duration
         * @throws PolicyException if the duration computation fails
         */
        Duration maximumDuration();

        /**
         * Creates a backoff policy that escalates linearly from the initial delay with a
         * wall-clock duration budget.
         *
         * <p>Each retry waits {@code initialDelay * attempt}. The {@link Retry} loop caps the
         * wait duration at the remaining budget. For example, {@code fixed(100ms, 1s)} produces
         * 100ms, 200ms, 300ms, 400ms (capped at remaining budget), etc. for up to 1 second total.
         *
         * @param initialDelay the delay after the first failed attempt
         * @param maximumDuration the total wall-clock duration budget
         * @return a fixed-step backoff policy
         * @throws NullPointerException if {@code initialDelay} or {@code maximumDuration} is {@code null}
         * @throws IllegalArgumentException if {@code initialDelay} is negative,
         *     {@code maximumDuration} is negative, or {@code initialDelay} is greater than
         *     {@code maximumDuration}
         */
        static Policy fixed(final Duration initialDelay, final Duration maximumDuration) {
            Objects.requireNonNull(initialDelay, "initialDelay is null");
            Objects.requireNonNull(maximumDuration, "maximumDuration is null");
            Arguments.requireFalse(initialDelay.isNegative(), "initialDelay is negative");
            Arguments.requireFalse(maximumDuration.isNegative(), "maximumDuration is negative");
            Arguments.requireTrue(
                    initialDelay.compareTo(maximumDuration) <= 0, "initialDelay is greater than maximumDuration");
            return new Policy() {
                @Override
                public Duration waitDuration(final int attempt, final Throwable cause) {
                    return multiplyOrZero(initialDelay, attempt);
                }

                @Override
                public Duration maximumDuration() {
                    return maximumDuration;
                }
            };
        }

        /**
         * Creates a backoff policy that escalates exponentially from the initial delay with a
         * wall-clock duration budget.
         *
         * <p>Each retry waits {@code initialDelay * 2^(attempt-1)}. The multiplier is capped at
         * {@code 2^62} to prevent long-overflow for high attempt numbers; in practice the
         * {@link Retry} loop caps the wait duration at the remaining budget long before this
         * limit is reached. For example, {@code exponential(100ms, 5s)} produces 100ms, 200ms,
         * 400ms, 800ms, 1.6s, 3.2s (capped at remaining budget), etc. for up to 5 seconds total.
         *
         * @param initialDelay the delay after the first failed attempt
         * @param maximumDuration the total wall-clock duration budget
         * @return an exponential backoff policy
         * @throws NullPointerException if {@code initialDelay} or {@code maximumDuration} is {@code null}
         * @throws IllegalArgumentException if {@code initialDelay} is negative,
         *     {@code maximumDuration} is negative, or {@code initialDelay} is greater than
         *     {@code maximumDuration}
         */
        static Policy exponential(final Duration initialDelay, final Duration maximumDuration) {
            Objects.requireNonNull(initialDelay, "initialDelay is null");
            Objects.requireNonNull(maximumDuration, "maximumDuration is null");
            Arguments.requireFalse(initialDelay.isNegative(), "initialDelay is negative");
            Arguments.requireFalse(maximumDuration.isNegative(), "maximumDuration is negative");
            Arguments.requireTrue(
                    initialDelay.compareTo(maximumDuration) <= 0, "initialDelay is greater than maximumDuration");
            return new Policy() {
                @Override
                public Duration waitDuration(final int attempt, final Throwable cause) {
                    int exponent = attempt - 1;
                    if (exponent < 0) {
                        return Duration.ZERO;
                    }
                    return multiplyOrZero(initialDelay, 1L << Math.min(exponent, 62));
                }

                @Override
                public Duration maximumDuration() {
                    return maximumDuration;
                }
            };
        }

        private static Duration multiplyOrZero(final Duration duration, final long multiplier) {
            if (multiplier < 0) {
                return Duration.ZERO;
            }
            try {
                Duration result = duration.multipliedBy(multiplier);
                if (result.isNegative()) {
                    return Duration.ZERO;
                }
                return result;
            } catch (ArithmeticException e) {
                throw new PolicyException("duration overflow multiplying by " + multiplier, e);
            }
        }
    }

    /**
     * Describes the outcome of a {@link Retry} run.
     *
     * <p>The result captures whether the operation ultimately succeeded, how many attempts
     * were made, the elapsed wall-clock duration, and the exceptions from each failed attempt.
     */
    public static final class Result {

        private final Duration maximumDuration;
        private final int attemptCount;
        private final Duration elapsedDuration;
        private final boolean successful;
        private final List<Throwable> exceptions;

        Result(
                final Duration maximumDuration,
                final int attemptCount,
                final Duration elapsedDuration,
                final boolean successful,
                final List<Throwable> exceptions) {
            this.maximumDuration = maximumDuration;
            this.attemptCount = attemptCount;
            this.elapsedDuration = elapsedDuration;
            this.successful = successful;
            this.exceptions = exceptions;
        }

        /**
         * Returns the configured wall-clock duration budget.
         *
         * @return the maximum duration
         */
        public Duration maximumDuration() {
            return maximumDuration;
        }

        /**
         * Returns the number of attempts that were executed.
         *
         * @return the actual attempt count
         */
        public int attemptCount() {
            return attemptCount;
        }

        /**
         * Returns the total wall-clock duration from the first attempt to termination.
         *
         * @return the elapsed duration
         */
        public Duration elapsedDuration() {
            return elapsedDuration;
        }

        /**
         * Returns whether the operation succeeded within the duration budget.
         *
         * @return {@code true} when the operation succeeded
         */
        public boolean isSuccessful() {
            return successful;
        }

        /**
         * Returns whether any attempt produced an exception.
         *
         * @return {@code true} when at least one attempt failed
         */
        public boolean hasExceptions() {
            return !exceptions.isEmpty();
        }

        /**
         * Returns the exception captured for the attempt at the supplied index.
         *
         * @param index the 0-based attempt index
         * @return the captured exception, or an empty {@link Optional} when the index is out of range
         */
        public Optional<Throwable> exception(final int index) {
            if (index < 0 || index >= exceptions.size()) {
                return Optional.empty();
            }
            return Optional.ofNullable(exceptions.get(index));
        }

        /**
         * Returns every captured exception in attempt order.
         *
         * @return an immutable list of exceptions from failed attempts
         */
        public List<Throwable> exceptions() {
            return exceptions;
        }
    }
}

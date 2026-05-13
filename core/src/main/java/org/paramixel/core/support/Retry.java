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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.paramixel.core.internal.UnrecoverableErrors;

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
public class Retry {

    private static final Predicate<Throwable> DEFAULT_RETRY_ON = t -> !(t instanceof Error);

    private final Policy backoffPolicy;
    private Predicate<Throwable> retryOn;
    private final List<BiConsumer<Integer, Throwable>> onRetryCallbacks;
    private boolean hasRun;

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
    public static Retry of(Policy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        return new Retry(policy);
    }

    private Retry(Policy policy) {
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
    public Retry retryOn(Predicate<Throwable> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
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
     */
    public Retry onRetry(BiConsumer<Integer, Throwable> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        onRetryCallbacks.add(callback);
        return this;
    }

    /**
     * Runs the supplied throwable runnable with retry behavior and returns the result.
     *
     * <p>On each failed attempt where the {@link #retryOn(Predicate) retry predicate} returns
     * {@code true} and the wall-clock duration budget has not been exhausted, the on-retry callbacks
     * are invoked and the backoff delay is applied before the next attempt. The delay is capped at
     * the remaining budget. An {@link OutOfMemoryError} or {@link StackOverflowError} is rethrown
     * immediately without retry; other {@link Error} subtypes are captured and may be retried
     * depending on the retry predicate.
     *
     * @param throwableRunnable the operation to run
     * @return the retry result describing success, attempt count, elapsed duration, and captured exceptions
     * @throws IllegalStateException if this retry sequence has already run
     * @throws OutOfMemoryError if the throwableRunnable throws an {@code OutOfMemoryError}
     * @throws StackOverflowError if the throwableRunnable throws a {@code StackOverflowError}
     * @throws NullPointerException if {@code throwableRunnable} is {@code null}
     */
    public Result run(ThrowableRunnable throwableRunnable) {
        Objects.requireNonNull(throwableRunnable, "throwableRunnable must not be null");
        if (hasRun) {
            throw new IllegalStateException("Retry has already run");
        }

        hasRun = true;

        Duration maximumDuration = backoffPolicy.getMaximumDuration();
        long startNanos = System.nanoTime();
        var exceptions = new ArrayList<Throwable>();
        int attempt = 0;

        while (true) {
            attempt++;

            try {
                throwableRunnable.run();
                return new Result(maximumDuration, attempt, elapsedDuration(startNanos), true, Collections.emptyList());
            } catch (Throwable e) {
                UnrecoverableErrors.rethrowIfUnrecoverable(e);
                exceptions.add(e);

                if (!retryOn.test(e)) {
                    return new Result(
                            maximumDuration,
                            attempt,
                            elapsedDuration(startNanos),
                            false,
                            Collections.unmodifiableList(new ArrayList<>(exceptions)));
                }

                Duration elapsed = elapsedDuration(startNanos);
                Duration remaining = maximumDuration.minus(elapsed);

                if (remaining.isZero() || remaining.isNegative()) {
                    return new Result(
                            maximumDuration,
                            attempt,
                            elapsed,
                            false,
                            Collections.unmodifiableList(new ArrayList<>(exceptions)));
                }

                for (var callback : onRetryCallbacks) {
                    callback.accept(attempt + 1, e);
                }

                Duration waitDuration = backoffPolicy.waitDuration(attempt, e);
                if (waitDuration.compareTo(remaining) > 0) {
                    waitDuration = remaining;
                }

                if (!waitDuration.isZero()) {
                    try {
                        Thread.sleep(waitDuration.toMillis(), waitDuration.toNanosPart() % 1_000_000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new Result(
                                maximumDuration,
                                attempt,
                                elapsedDuration(startNanos),
                                false,
                                Collections.unmodifiableList(new ArrayList<>(exceptions)));
                    }
                }
            }
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
     * @throws OutOfMemoryError if the throwableRunnable throws an {@code OutOfMemoryError}
     * @throws StackOverflowError if the throwableRunnable throws a {@code StackOverflowError}
     * @throws NullPointerException if {@code throwableRunnable} is {@code null}
     */
    public void runAndThrow(ThrowableRunnable throwableRunnable) throws Throwable {
        Result result = run(throwableRunnable);

        if (!result.isPass() && result.hasExceptions()) {
            List<Throwable> exceptions = result.getExceptions();
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
     * @return {@code true} when {@link #run(ThrowableRunnable)} or {@link #runAndThrow(ThrowableRunnable)} has already been called
     */
    public boolean hasRun() {
        return hasRun;
    }

    /**
     * Marks this retry sequence as not yet run without removing registered on-retry callbacks.
     *
     * @return this retry sequence
     */
    public Retry reset() {
        hasRun = false;
        return this;
    }

    /**
     * Removes all registered on-retry callbacks and resets execution state.
     *
     * @return this retry sequence
     */
    public Retry clear() {
        onRetryCallbacks.clear();
        hasRun = false;
        return this;
    }

    private static Duration elapsedDuration(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    /**
     * Computes the delay duration before a retry attempt and provides the total wall-clock duration budget.
     *
     * <p>The {@code attempt} parameter is the 1-based number of the just-failed attempt.
     * The returned duration is the time to wait before the next attempt. The {@link Retry}
     * loop caps the wait duration at the remaining budget if it exceeds it.
     */
    public interface Policy {

        /**
         * Returns the delay duration before the next retry attempt.
         *
         * @param attempt the 1-based number of the just-failed attempt
         * @param cause the throwable from the failed attempt
         * @return the duration to wait before the next attempt
         */
        Duration waitDuration(int attempt, Throwable cause);

        /**
         * Returns the total wall-clock duration budget for the retry sequence.
         *
         * @return the maximum duration
         */
        Duration getMaximumDuration();

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
        static Policy fixed(Duration initialDelay, Duration maximumDuration) {
            Objects.requireNonNull(initialDelay, "initialDelay must not be null");
            Objects.requireNonNull(maximumDuration, "maximumDuration must not be null");
            Arguments.require(!initialDelay.isNegative(), "initialDelay must not be negative");
            Arguments.require(!maximumDuration.isNegative(), "maximumDuration must not be negative");
            Arguments.require(
                    initialDelay.compareTo(maximumDuration) <= 0,
                    "initialDelay must not be greater than maximumDuration");
            return new Policy() {
                @Override
                public Duration waitDuration(int attempt, Throwable cause) {
                    return initialDelay.multipliedBy(attempt);
                }

                @Override
                public Duration getMaximumDuration() {
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
        static Policy exponential(Duration initialDelay, Duration maximumDuration) {
            Objects.requireNonNull(initialDelay, "initialDelay must not be null");
            Objects.requireNonNull(maximumDuration, "maximumDuration must not be null");
            Arguments.require(!initialDelay.isNegative(), "initialDelay must not be negative");
            Arguments.require(!maximumDuration.isNegative(), "maximumDuration must not be negative");
            Arguments.require(
                    initialDelay.compareTo(maximumDuration) <= 0,
                    "initialDelay must not be greater than maximumDuration");
            return new Policy() {
                @Override
                public Duration waitDuration(int attempt, Throwable cause) {
                    return initialDelay.multipliedBy(1L << Math.min(attempt - 1, 62));
                }

                @Override
                public Duration getMaximumDuration() {
                    return maximumDuration;
                }
            };
        }
    }

    /**
     * Represents a single operation to be retried.
     */
    public interface ThrowableRunnable {

        /**
         * Runs the operation.
         *
         * @throws Throwable any failure raised by the operation
         */
        void run() throws Throwable;
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
        private final boolean pass;
        private final List<Throwable> exceptions;

        Result(
                Duration maximumDuration,
                int attemptCount,
                Duration elapsedDuration,
                boolean pass,
                List<Throwable> exceptions) {
            this.maximumDuration = maximumDuration;
            this.attemptCount = attemptCount;
            this.elapsedDuration = elapsedDuration;
            this.pass = pass;
            this.exceptions = exceptions;
        }

        /**
         * Returns the configured wall-clock duration budget.
         *
         * @return the maximum duration
         */
        public Duration getMaximumDuration() {
            return maximumDuration;
        }

        /**
         * Returns the number of attempts that were executed.
         *
         * @return the actual attempt count
         */
        public int getAttemptCount() {
            return attemptCount;
        }

        /**
         * Returns the total wall-clock duration from the first attempt to termination.
         *
         * @return the elapsed duration
         */
        public Duration getElapsedDuration() {
            return elapsedDuration;
        }

        /**
         * Returns whether the operation passed within the duration budget.
         *
         * @return {@code true} when the operation passed
         */
        public boolean isPass() {
            return pass;
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
        public Optional<Throwable> getException(int index) {
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
        public List<Throwable> getExceptions() {
            return exceptions;
        }
    }
}

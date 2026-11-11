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

/**
 * Retries a runnable operation with a {@link Backoff} schedule and a wall-clock duration budget.
 *
 * <p>The type is an immutable configuration: {@link #retryOn(Predicate)} and
 * {@link #onRetry(BiConsumer)} return a new instance, and {@link #run(ThrowingRunnable)} may be called any number of
 * times, so a single {@code Retry} can be reused across tests without {@code reset()} or {@code clear()}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * var retry = Retry.of(Backoff.exponential(Duration.ofMillis(50), Duration.ofSeconds(1)), Duration.ofSeconds(10))
 *         .retryOn(t -> t instanceof AssertionError);
 * var result = retry.run(this::flakyOperation);
 * }</pre>
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class Retry {

    private static final Predicate<Throwable> DEFAULT_RETRY_ON = t -> !(t instanceof Error);

    private final Backoff backoff;
    private final Duration maximumDuration;
    private final Predicate<Throwable> retryOn;
    private final List<BiConsumer<Integer, Throwable>> onRetryCallbacks;

    private Retry(
            final Backoff backoff,
            final Duration maximumDuration,
            final Predicate<Throwable> retryOn,
            final List<BiConsumer<Integer, Throwable>> onRetryCallbacks) {
        this.backoff = Objects.requireNonNull(backoff, "backoff is null");
        this.maximumDuration = Objects.requireNonNull(maximumDuration, "maximumDuration is null");
        this.retryOn = Objects.requireNonNull(retryOn, "retryOn is null");
        this.onRetryCallbacks = List.copyOf(onRetryCallbacks);
    }

    /**
     * Creates a retry configuration.
     *
     * <p>The default retry predicate retries on any throwable that is not an {@link Error}.
     *
     * @param backoff the delay schedule between attempts; must not be {@code null}
     * @param maximumDuration the total wall-clock duration budget; must not be {@code null} or negative
     * @return a new retry configuration; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code maximumDuration} is negative
     */
    public static Retry of(final Backoff backoff, final Duration maximumDuration) {
        Objects.requireNonNull(maximumDuration, "maximumDuration is null");
        Arguments.requireFalse(maximumDuration.isNegative(), "maximumDuration is negative");
        return new Retry(backoff, maximumDuration, DEFAULT_RETRY_ON, List.of());
    }

    /**
     * Returns a retry configuration that uses the supplied retry predicate.
     *
     * <p>When the predicate returns {@code false} for a throwable, the sequence stops immediately and the result
     * reflects that failure.
     *
     * @param predicate the predicate that decides whether a throwable warrants another attempt; must not be
     *     {@code null}
     * @return a new retry configuration; never {@code null}
     * @throws NullPointerException if {@code predicate} is {@code null}
     */
    public Retry retryOn(final Predicate<Throwable> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return new Retry(backoff, maximumDuration, predicate, onRetryCallbacks);
    }

    /**
     * Returns a retry configuration that also invokes the supplied callback before each retry.
     *
     * @param callback the callback invoked with the next, 1-based attempt number and the previous failure; must not be
     *     {@code null}
     * @return a new retry configuration; never {@code null}
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    public Retry onRetry(final BiConsumer<Integer, Throwable> callback) {
        Objects.requireNonNull(callback, "callback is null");
        var callbacks = new ArrayList<>(onRetryCallbacks);
        callbacks.add(callback);
        return new Retry(backoff, maximumDuration, retryOn, callbacks);
    }

    /**
     * Runs the supplied operation with retries and returns the result.
     *
     * <p>On each failed attempt where the retry predicate returns {@code true} and the budget is not exhausted, the
     * on-retry callbacks run and the backoff delay is applied before the next attempt; the delay is capped at the
     * remaining budget. A {@link VirtualMachineError} other than {@link StackOverflowError} is rethrown immediately.
     *
     * @param throwableRunnable the operation to run; must not be {@code null}
     * @return the retry result; never {@code null}
     * @throws NullPointerException if {@code throwableRunnable} is {@code null}
     * @throws VirtualMachineError if the operation throws an unrecoverable error
     */
    public Result run(final ThrowingRunnable throwableRunnable) {
        Objects.requireNonNull(throwableRunnable, "throwableRunnable is null");

        long startNanos = System.nanoTime();
        var exceptions = new ArrayList<Throwable>();
        int attempt = 0;

        while (true) {
            attempt++;

            try {
                throwableRunnable.run();
                return new Result(maximumDuration, attempt, elapsedDuration(startNanos), true, List.of());
            } catch (Throwable e) {
                UnrecoverableErrors.rethrowIfUnrecoverable(e);
                exceptions.add(e);

                if (!retryOn.test(e)) {
                    return new Result(
                            maximumDuration, attempt, elapsedDuration(startNanos), false, List.copyOf(exceptions));
                }

                var elapsed = elapsedDuration(startNanos);
                var remaining = maximumDuration.minus(elapsed);

                if (remaining.isZero() || remaining.isNegative()) {
                    return new Result(maximumDuration, attempt, elapsed, false, List.copyOf(exceptions));
                }

                for (var callback : onRetryCallbacks) {
                    callback.accept(attempt + 1, e);
                }

                var waitDuration = backoff.delayFor(attempt + 1);
                if (waitDuration.compareTo(remaining) > 0) {
                    waitDuration = remaining;
                }

                if (!waitDuration.isZero()) {
                    try {
                        Thread.sleep(waitDuration.toMillis(), waitDuration.toNanosPart() % 1_000_000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        exceptions.add(ie);
                        return new Result(
                                maximumDuration, attempt, elapsedDuration(startNanos), false, List.copyOf(exceptions));
                    }
                }
            }
        }
    }

    /**
     * Runs the supplied operation with retries and rethrows the last captured exception when the budget is exhausted
     * or the retry predicate returns {@code false}.
     *
     * <p>Earlier exceptions are added to the last exception as suppressed throwables in attempt order.
     *
     * @param throwableRunnable the operation to run; must not be {@code null}
     * @throws Throwable the last captured exception, with earlier exceptions suppressed
     * @throws NullPointerException if {@code throwableRunnable} is {@code null}
     * @throws VirtualMachineError if the operation throws an unrecoverable error
     */
    public void runAndThrow(final ThrowingRunnable throwableRunnable) throws Throwable {
        Result result = run(throwableRunnable);

        if (!result.successful() && result.hasExceptions()) {
            List<Throwable> exceptions = result.exceptions();
            var lastException = exceptions.get(exceptions.size() - 1);
            for (int i = 0; i < exceptions.size() - 1; i++) {
                lastException.addSuppressed(exceptions.get(i));
            }
            throw lastException;
        }
    }

    private static Duration elapsedDuration(final long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    /**
     * Describes the outcome of a {@link Retry} run.
     *
     * @param maximumDuration the configured wall-clock duration budget; never {@code null}
     * @param attemptCount the number of attempts that were executed
     * @param elapsedDuration the total wall-clock duration from the first attempt to termination; never {@code null}
     * @param successful whether the operation succeeded within the budget
     * @param exceptions every captured exception in attempt order; never {@code null}
     */
    public record Result(
            Duration maximumDuration,
            int attemptCount,
            Duration elapsedDuration,
            boolean successful,
            List<Throwable> exceptions) {

        /**
         * Creates a result.
         *
         * @param maximumDuration the configured wall-clock duration budget; must not be {@code null}
         * @param attemptCount the number of attempts that were executed
         * @param elapsedDuration the elapsed wall-clock duration; must not be {@code null}
         * @param successful whether the operation succeeded
         * @param exceptions every captured exception in attempt order; must not be {@code null}
         */
        public Result {
            Objects.requireNonNull(maximumDuration, "maximumDuration is null");
            Objects.requireNonNull(elapsedDuration, "elapsedDuration is null");
            exceptions = List.copyOf(exceptions);
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
         * @return the captured exception, or empty when the index is out of range
         */
        public Optional<Throwable> exception(final int index) {
            if (index < 0 || index >= exceptions.size()) {
                return Optional.empty();
            }
            return Optional.ofNullable(exceptions.get(index));
        }
    }
}

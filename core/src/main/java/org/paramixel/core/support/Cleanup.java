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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.paramixel.core.internal.UnrecoverableErrors;

/**
 * Collects cleanup callbacks and runs them later in forward or reverse order.
 *
 * <p>This utility is intended for resource teardown flows where callers want to register cleanup work incrementally
 * and then either inspect failures with {@link CleanupResult} or rethrow them with {@link #runAndThrow()}.
 *
 * <p>This class is <strong>not thread-safe</strong>. Instances must not be shared across threads
 * or accessed concurrently. Typical usage confines a {@code Cleanup} instance to a single
 * lifecycle method pair such as {@code before}/{@code after}.
 */
public class Cleanup {

    /**
     * Defines the run order for registered cleanup callbacks.
     */
    public enum Mode {
        /**
         * Run callbacks in the order they were registered.
         */
        FORWARD,

        /**
         * Run callbacks in reverse registration order.
         */
        REVERSE
    }

    private final Mode mode;

    private final List<ThrowableRunnable> throwableRunnables = new ArrayList<>();

    private boolean hasRun = false;

    /**
     * Creates a cleanup sequence that runs callbacks in registration order.
     *
     * @return a new cleanup sequence
     */
    public static Cleanup of() {
        return new Cleanup(Mode.FORWARD);
    }

    /**
     * Creates a cleanup sequence with the supplied run mode.
     *
     * @param mode the callback run order
     * @return a new cleanup sequence
     * @throws NullPointerException if {@code mode} is {@code null}
     */
    public static Cleanup of(final Mode mode) {
        Objects.requireNonNull(mode, "mode must not be null");
        return new Cleanup(mode);
    }

    private Cleanup() {
        this(Mode.FORWARD);
    }

    private Cleanup(final Mode mode) {
        this.mode = mode;
    }

    /**
     * Returns the number of registered cleanup callbacks.
     *
     * @return the number of registered callbacks
     */
    public int getCount() {
        return throwableRunnables.size();
    }

    /**
     * Returns whether this cleanup sequence has already been run.
     *
     * @return {@code true} when {@link #run()} or {@link #runAndThrow()} has already been called
     */
    public boolean hasRun() {
        return hasRun;
    }

    /**
     * Registers a cleanup callback.
     *
     * @param throwableRunnable the callback to register
     * @return this cleanup sequence
     * @throws NullPointerException if {@code throwableRunnable} is {@code null}
     */
    public Cleanup add(final ThrowableRunnable throwableRunnable) {
        Objects.requireNonNull(throwableRunnable, "throwableRunnable must not be null");
        throwableRunnables.add(throwableRunnable);
        return this;
    }

    /**
     * Registers multiple cleanup callbacks.
     *
     * @param throwableRunnables the callbacks to register
     * @return this cleanup sequence
     * @throws NullPointerException if {@code throwableRunnables} is {@code null} or contains {@code null}
     */
    public Cleanup add(final ThrowableRunnable... throwableRunnables) {
        Objects.requireNonNull(throwableRunnables, "throwableRunnables must not be null");
        for (ThrowableRunnable throwableRunnable : throwableRunnables) {
            add(throwableRunnable);
        }
        return this;
    }

    /**
     * Registers multiple cleanup callbacks from the supplied list.
     *
     * @param throwableRunnables the callbacks to register
     * @return this cleanup sequence
     * @throws NullPointerException if {@code throwableRunnables} is {@code null} or contains {@code null}
     */
    public Cleanup add(final List<? extends ThrowableRunnable> throwableRunnables) {
        Objects.requireNonNull(throwableRunnables, "throwableRunnables must not be null");
        for (ThrowableRunnable throwableRunnable : throwableRunnables) {
            add(throwableRunnable);
        }
        return this;
    }

    /**
     * Registers a callback that runs only when the supplied condition evaluates to {@code true} at run time.
     *
     * @param condition the condition to evaluate during cleanup
     * @param throwableRunnable the callback to run when the condition passes
     * @return this cleanup sequence
     * @throws NullPointerException if {@code condition} or {@code throwableRunnable} is {@code null}
     */
    public Cleanup addWhen(final Supplier<Boolean> condition, final ThrowableRunnable throwableRunnable) {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(throwableRunnable, "throwableRunnable must not be null");
        return add(() -> {
            if (condition.get()) {
                throwableRunnable.run();
            }
        });
    }

    /**
     * Registers a callback that runs only when the supplied condition is {@code true}.
     *
     * @param condition the static condition to evaluate
     * @param throwableRunnable the callback to run when the condition passes
     * @return this cleanup sequence
     * @throws NullPointerException if {@code throwableRunnable} is {@code null}
     */
    public Cleanup addWhen(final boolean condition, final ThrowableRunnable throwableRunnable) {
        Objects.requireNonNull(throwableRunnable, "throwableRunnable must not be null");
        return add(() -> {
            if (condition) {
                throwableRunnable.run();
            }
        });
    }

    /**
     * Registers {@link AutoCloseable#close()} for the supplied resource when it is non-null.
     *
     * @param autoCloseable the resource to close during cleanup
     * @return this cleanup sequence
     */
    public Cleanup addCloseable(final AutoCloseable autoCloseable) {
        if (autoCloseable != null) {
            add(autoCloseable::close);
        }
        return this;
    }

    /**
     * Returns an immutable snapshot of the registered callbacks.
     *
     * @return an immutable snapshot of the registered callbacks, in registration order
     */
    public List<ThrowableRunnable> getThrowableRunnables() {
        return List.copyOf(throwableRunnables);
    }

    /**
     * Marks this cleanup sequence as not yet run without removing registered callbacks.
     *
     * @return this cleanup sequence
     */
    public Cleanup reset() {
        hasRun = false;
        return this;
    }

    /**
     * Removes all registered callbacks and resets execution state.
     *
     * @return this cleanup sequence
     */
    public Cleanup clear() {
        throwableRunnables.clear();
        hasRun = false;
        return this;
    }

    /**
     * Runs all registered callbacks and captures any failures that are not
     * {@link OutOfMemoryError} or {@link StackOverflowError}.
     *
     * <p>Callbacks run in the configured {@link Mode}. Every callback is attempted even when earlier callbacks fail.
     *
     * @return the cleanup result describing per-callback failures
     * @throws IllegalStateException if this cleanup sequence has already run
     * @throws OutOfMemoryError if a callback throws an {@code OutOfMemoryError}
     * @throws StackOverflowError if a callback throws a {@code StackOverflowError}
     */
    public CleanupResult run() {
        if (hasRun) {
            throw new IllegalStateException("Cleanup has already run");
        }

        hasRun = true;

        Throwable[] exceptions = new Throwable[throwableRunnables.size()];
        int[] indices = getRunIndices();

        for (int index : indices) {
            try {
                throwableRunnables.get(index).run();
            } catch (Throwable e) {
                UnrecoverableErrors.rethrowIfUnrecoverable(e);
                exceptions[index] = e;
            }
        }

        return new CleanupResult(throwableRunnables.size(), Arrays.asList(exceptions));
    }

    private int[] getRunIndices() {
        int size = throwableRunnables.size();
        int[] indices = new int[size];
        if (mode == Mode.FORWARD) {
            for (int i = 0; i < size; i++) {
                indices[i] = i;
            }
        } else {
            for (int i = 0; i < size; i++) {
                indices[i] = size - 1 - i;
            }
        }
        return indices;
    }

    /**
     * Runs all registered callbacks and rethrows the first failure that is not
     * an {@link OutOfMemoryError} or {@link StackOverflowError}.
     *
     * <p>Later failures are added to the first failure as suppressed exceptions in run order.
     *
     * @throws Throwable the first captured callback failure, with later failures suppressed
     * @throws IllegalStateException if this cleanup sequence has already run
     * @throws OutOfMemoryError if a callback throws an {@code OutOfMemoryError}
     * @throws StackOverflowError if a callback throws a {@code StackOverflowError}
     */
    public void runAndThrow() throws Throwable {
        CleanupResult result = run();

        Throwable firstException = null;
        int[] indices = getRunIndices();

        for (int index : indices) {
            Optional<Throwable> exception = result.getException(index);
            if (exception.isPresent()) {
                Throwable e = exception.get();
                if (firstException == null) {
                    firstException = e;
                } else {
                    firstException.addSuppressed(e);
                }
            }
        }

        if (firstException != null) {
            throw firstException;
        }
    }

    /**
     * Represents a single cleanup callback.
     */
    public interface ThrowableRunnable {

        /**
         * Runs the cleanup callback.
         *
         * @throws Throwable any failure raised by the callback
         */
        void run() throws Throwable;
    }
}

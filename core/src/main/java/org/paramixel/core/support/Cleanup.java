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

/**
 * Collects cleanup callbacks and executes them later in forward or reverse order.
 *
 * <p>This utility is intended for resource teardown flows where callers want to register cleanup work incrementally
 * and then either inspect failures with {@link CleanupResult} or rethrow them with {@link #runAndThrow()}.
 */
public class Cleanup {

    /**
     * Defines the execution order for registered cleanup callbacks.
     */
    public enum Mode {
        FORWARD,

        REVERSE
    }

    private final Mode mode;

    private final List<Executable> executables = new ArrayList<>();

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
     * Creates a cleanup sequence with the supplied execution mode.
     *
     * @param mode the callback execution order
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
        return executables.size();
    }

    /**
     * Returns whether this cleanup sequence has already been executed.
     *
     * @return {@code true} when {@link #run()} or {@link #runAndThrow()} has already been called
     */
    public boolean hasRun() {
        return hasRun;
    }

    /**
     * Registers a cleanup callback.
     *
     * @param executable the callback to register
     * @return this cleanup sequence
     * @throws NullPointerException if {@code executable} is {@code null}
     */
    public Cleanup add(final Executable executable) {
        Objects.requireNonNull(executable, "executable must not be null");
        executables.add(executable);
        return this;
    }

    /**
     * Registers multiple cleanup callbacks.
     *
     * @param executables the callbacks to register
     * @return this cleanup sequence
     * @throws NullPointerException if {@code executables} is {@code null} or contains {@code null}
     */
    public Cleanup add(final Executable... executables) {
        Objects.requireNonNull(executables, "executables must not be null");
        for (Executable executable : executables) {
            add(executable);
        }
        return this;
    }

    /**
     * Registers multiple cleanup callbacks from the supplied list.
     *
     * @param executables the callbacks to register
     * @return this cleanup sequence
     * @throws NullPointerException if {@code executables} is {@code null} or contains {@code null}
     */
    public Cleanup add(final List<? extends Executable> executables) {
        Objects.requireNonNull(executables, "executables must not be null");
        for (Executable executable : executables) {
            add(executable);
        }
        return this;
    }

    /**
     * Registers a callback that runs only when the supplied condition evaluates to {@code true} at execution time.
     *
     * @param condition the condition to evaluate during cleanup
     * @param executable the callback to run when the condition passes
     * @return this cleanup sequence
     * @throws NullPointerException if {@code condition} or {@code executable} is {@code null}
     */
    public Cleanup addWhen(final Supplier<Boolean> condition, final Executable executable) {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(executable, "executable must not be null");
        return add(() -> {
            if (condition.get()) {
                executable.run();
            }
        });
    }

    /**
     * Registers a callback that runs only when the supplied condition is {@code true}.
     *
     * @param condition the static condition to evaluate
     * @param executable the callback to run when the condition passes
     * @return this cleanup sequence
     * @throws NullPointerException if {@code executable} is {@code null}
     */
    public Cleanup addWhen(final boolean condition, final Executable executable) {
        Objects.requireNonNull(executable, "executable must not be null");
        return add(() -> {
            if (condition) {
                executable.run();
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
     * @return the registered callbacks in registration order
     */
    public List<Executable> getExecutables() {
        return List.copyOf(executables);
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
        executables.clear();
        hasRun = false;
        return this;
    }

    /**
     * Executes all registered callbacks and captures any non-{@link Error} failures.
     *
     * <p>Callbacks run in the configured {@link Mode}. Every callback is attempted even when earlier callbacks fail.
     *
     * @return the cleanup result describing per-callback failures
     * @throws IllegalStateException if this cleanup sequence has already run
     * @throws Error if a callback throws an {@link Error}
     */
    public CleanupResult run() {
        if (hasRun) {
            throw new IllegalStateException("Cleanup has already run");
        }

        hasRun = true;

        Throwable[] exceptions = new Throwable[executables.size()];
        int[] indices = getExecutionIndices();

        for (int index : indices) {
            try {
                executables.get(index).run();
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                exceptions[index] = e;
            }
        }

        return new CleanupResult(executables.size(), Arrays.asList(exceptions));
    }

    private int[] getExecutionIndices() {
        int size = executables.size();
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
     * Executes all registered callbacks and rethrows the first non-{@link Error} failure.
     *
     * <p>Later failures are added to the first failure as suppressed exceptions in execution order.
     *
     * @throws Throwable the first captured callback failure, with later failures suppressed
     * @throws IllegalStateException if this cleanup sequence has already run
     * @throws Error if a callback throws an {@link Error}
     */
    public void runAndThrow() throws Throwable {
        CleanupResult result = run();

        Throwable firstException = null;
        int[] indices = getExecutionIndices();

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
    public interface Executable {

        /**
         * Runs the cleanup callback.
         *
         * @throws Throwable any failure raised by the callback
         */
        void run() throws Throwable;
    }
}

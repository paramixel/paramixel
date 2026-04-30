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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Collects and executes cleanup tasks in the specified order.
 *
 * <p>Tasks are executed in the order determined by the {@link Mode} specified
 * at construction. Any exceptions thrown by tasks are collected in the
 * {@link CleanupResult} returned by {@link #run()}.
 *
 * @see CleanupResult
 * @see Executable
 * @see Mode
 */
public class Cleanup {

    /**
     * Execution order mode for cleanup tasks.
     */
    public enum Mode {

        /**
         * Execute tasks in registration order (first-registered executes first).
         */
        FORWARD,

        /**
         * Execute tasks in reverse registration order (last-registered executes first).
         */
        REVERSE
    }

    private final Mode mode;

    private final List<Executable> executables = new ArrayList<>();

    private boolean hasRun = false;

    /**
     * Creates a new Cleanup with execution mode {@code Mode.FORWARD}.
     *
     * @return a new cleanup instance; never {@code null}
     */
    public static Cleanup of() {
        return new Cleanup(Mode.FORWARD);
    }

    /**
     * Creates a new Cleanup with the specified execution mode.
     *
     * @param mode The execution mode; must not be null.
     * @return a new cleanup instance; never {@code null}
     * @throws NullPointerException if {@code mode} is {@code null}
     */
    public static Cleanup of(final Mode mode) {
        Objects.requireNonNull(mode, "mode must not be null");
        return new Cleanup(mode);
    }

    /**
     * Creates a new Cleanup with execution mode {@code Mode.FORWARD}
     */
    private Cleanup() {
        this(Mode.FORWARD);
    }

    /**
     * Creates a new Cleanup with the specified execution mode.
     *
     * @param mode The execution mode; must not be null.
     */
    private Cleanup(final Mode mode) {
        this.mode = mode;
    }

    /**
     * Returns the number of registered cleanup tasks.
     *
     * @return The number of registered cleanup tasks.
     */
    public int getCount() {
        return executables.size();
    }

    /**
     * Returns whether this cleanup instance has already been executed.
     *
     * <p>Once {@link #run()} or {@link #runAndThrow()} has been called successfully, this
     * method returns {@code true} until {@link #reset()} is invoked.</p>
     *
     * @return {@code true} if cleanup has already run; otherwise {@code false}
     */
    public boolean hasRun() {
        return hasRun;
    }

    /**
     * Registers a cleanup executable.
     *
     * @param executable The executable to register; must not be null.
     * @return This cleanup.
     */
    public Cleanup add(final Executable executable) {
        Objects.requireNonNull(executable, "executable must not be null");
        executables.add(executable);
        return this;
    }

    /**
     * Registers multiple cleanup executables.
     *
     * @param executables The executables to register; must not be null.
     * @return This cleanup.
     */
    public Cleanup add(final Executable... executables) {
        Objects.requireNonNull(executables, "executables must not be null");
        for (Executable executable : executables) {
            add(executable);
        }
        return this;
    }

    /**
     * Registers multiple cleanup executables from a list.
     *
     * @param executables The executables to register; must not be null.
     * @return This cleanup.
     */
    public Cleanup add(final List<? extends Executable> executables) {
        Objects.requireNonNull(executables, "executables must not be null");
        for (Executable executable : executables) {
            add(executable);
        }
        return this;
    }

    /**
     * Registers a cleanup executable that executes only when the condition evaluates to {@code true}.
     *
     * @param condition The condition supplier to evaluate; must not be null.
     * @param executable The executable to execute if the condition is {@code true}; must not be null.
     * @return This cleanup.
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
     * Registers a cleanup executable that executes only when the condition is {@code true}.
     *
     * @param condition The condition to evaluate.
     * @param executable The executable to execute if the condition is {@code true}; must not be null.
     * @return This cleanup.
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
     * Registers an {@link AutoCloseable} to close during cleanup.
     *
     * <p>If {@code autoCloseable} is null, this method does nothing (null-safe).
     *
     * @param autoCloseable The {@code AutoCloseable} to close; may be null.
     * @return This cleanup.
     */
    public Cleanup addCloseable(final AutoCloseable autoCloseable) {
        if (autoCloseable != null) {
            add(autoCloseable::close);
        }
        return this;
    }

    /**
     * Returns the registered cleanup executables.
     *
     * @return An unmodifiable list of registered cleanup executables; never null.
     */
    public List<Executable> getExecutables() {
        return Collections.unmodifiableList(executables);
    }

    /**
     * Clears all registered executables and resets the execution state.
     *
     * <p>This method allows the same {@code Cleanup} instance to be reused after a prior
     * run. Any previously collected run state is discarded.</p>
     *
     * @return this cleanup instance
     */
    public Cleanup reset() {
        executables.clear();
        hasRun = false;
        return this;
    }

    /**
     * Executes all registered cleanup tasks and collects any thrown exceptions.
     *
     * <p>Tasks execute in the order defined by the configured {@link Mode}. Every task is
     * given a chance to run even if earlier tasks fail. Thrown exceptions are captured in
     * the returned {@link CleanupResult} by task index.</p>
     *
     * <p>This method may be called only once unless {@link #reset()} is invoked.</p>
     *
     * @return the aggregated cleanup result
     * @throws IllegalStateException if this cleanup has already run and has not been reset
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
     * Executes all executables if not yet executed, then throws the first exception with all others suppressed.
     *
     * <p>If multiple executables threw exceptions, the first exception encountered during
     * execution (based on the mode) is thrown with all subsequent exceptions added via
     * {@link Throwable#addSuppressed(Throwable)}.
     *
     * @throws Throwable The first exception thrown by any executable, with all other exceptions suppressed.
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
     * A functional interface for cleanup executables that can throw checked exceptions.
     */
    public interface Executable {

        /**
         * Executes the cleanup executable.
         *
         * @throws Throwable If the cleanup executable fails.
         */
        void run() throws Throwable;
    }
}

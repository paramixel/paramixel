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

import java.util.Collection;
import java.util.Objects;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.ThrowingRunnable;

/**
 * A single-use wrapper that executes a {@link ThrowingRunnable} and captures any
 * throwable thrown during execution.
 *
 * <p>Inspired by JUnit 5's {@code ThrowableCollector}, this class executes a runnable
 * once, records any non-unrecoverable throwable, and provides methods to query or
 * rethrow the captured result. Unrecoverable errors (non-{@code StackOverflowError}
 * {@link VirtualMachineError} subtypes) are rethrown immediately and never captured.
 *
 * <p>A {@code null} {@link AutoCloseable} passed to {@link #of(AutoCloseable)} is treated
 * as a no-op — calling {@link #run()} succeeds without capturing any throwable.
 *
 * <p>Each instance may only be run once; subsequent calls to {@link #run()} or
 * {@link #runAndThrow()} throw {@link IllegalStateException}.
 *
 * @see #of(ThrowingRunnable)
 * @see #of(AutoCloseable)
 * @see #runAndThrow()
 */
public final class CleanUp {

    private final ThrowingRunnable throwableRunnable;

    private boolean hasRun;

    private Throwable throwable;

    /**
     * Creates a new {@code CleanUp} for the supplied runnable.
     *
     * @param throwableRunnable the runnable to execute; must not be {@code null}
     * @return a new {@code CleanUp} instance
     * @throws NullPointerException if {@code throwableRunnable} is {@code null}
     */
    public static CleanUp of(final ThrowingRunnable throwableRunnable) {
        Objects.requireNonNull(throwableRunnable, "throwableRunnable is null");
        return new CleanUp(throwableRunnable);
    }

    /**
     * Creates a new {@code CleanUp} for the supplied {@link AutoCloseable}.
     *
     * <p>A {@code null} closeable is treated as a no-op. A non-null closeable
     * is wrapped as a {@link ThrowingRunnable} that invokes {@code close()}.
     *
     * @param autoCloseable the closeable to close on {@link #run()}, or {@code null} for a no-op
     * @return a new {@code CleanUp} instance
     */
    public static CleanUp of(final AutoCloseable autoCloseable) {
        if (autoCloseable != null) {
            return new CleanUp(autoCloseable::close);
        }
        return new CleanUp(null);
    }

    private CleanUp(final ThrowingRunnable throwableRunnable) {
        this.throwableRunnable = throwableRunnable;
    }

    /**
     * Executes the runnable and captures any non-unrecoverable throwable.
     *
     * <p>If the runnable throws an unrecoverable error (a non-{@code StackOverflowError}
     * {@link VirtualMachineError}), the error is rethrown immediately. All other
     * throwables are captured and available via {@link #throwable()}.
     *
     * <p>When created via {@link #of(AutoCloseable)} with a {@code null} closeable,
     * this method does nothing.
     *
     * <p>This method may only be called once per instance.
     *
     * @throws IllegalStateException if this instance has already been run
     * @throws VirtualMachineError if the runnable throws a non-{@code StackOverflowError}
     *     {@code VirtualMachineError}
     */
    public void run() {
        if (hasRun) {
            throw new IllegalStateException("CleanUp has already run");
        }
        hasRun = true;
        if (throwableRunnable == null) {
            return;
        }
        try {
            throwableRunnable.run();
        } catch (Throwable e) {
            UnrecoverableErrors.rethrowIfUnrecoverable(e);
            throwable = e;
        }
    }

    /**
     * Executes the runnable and throws the captured throwable if one was recorded.
     *
     * <p>Behaves like {@link #run()}, but after execution, if a throwable was captured,
     * it is thrown directly.
     *
     * @throws IllegalStateException if this instance has already been run
     * @throws Throwable the captured throwable, if any
     * @throws VirtualMachineError if the runnable throws a non-{@code StackOverflowError}
     *     {@code VirtualMachineError}
     */
    public void runAndThrow() throws Throwable {
        run();
        if (throwable != null) {
            throw throwable;
        }
    }

    /**
     * Returns the throwable captured during {@link #run()}, if any.
     *
     * @return the captured throwable, or {@code null} if the runnable succeeded
     *     or the instance was created with a {@code null} closeable
     */
    public Throwable throwable() {
        return throwable;
    }

    /**
     * Returns whether this instance has already been run.
     *
     * @return {@code true} if {@link #run()} or {@link #runAndThrow()} has been called
     */
    public boolean hasRun() {
        return hasRun;
    }

    /**
     * Returns whether no throwable was captured during execution.
     *
     * @return {@code true} if the runnable completed without a captured throwable
     */
    public boolean isEmpty() {
        return throwable == null;
    }

    /**
     * Returns whether a throwable was captured during execution.
     *
     * @return {@code true} if the runnable threw a captured throwable
     */
    public boolean isNotEmpty() {
        return throwable != null;
    }

    /**
     * Runs all supplied {@code CleanUp} instances and throws the first captured
     * throwable with any subsequent throwables added as
     * {@linkplain Throwable#addSuppressed(Throwable) suppressed exceptions}.
     *
     * <p>Each instance is run in array order unless it has already been run via
     * {@link #run()} or {@link #runAndThrow()}, in which case its previously captured
     * throwable (if any) is collected without re-execution. If an unrecoverable error
     * occurs, it is rethrown immediately and remaining instances are not run.
     *
     * @param cleanUps the {@code CleanUp} instances to run; must not be {@code null}
     * @throws NullPointerException if {@code cleanUps} is {@code null} or contains a
     *     {@code null} element
     * @throws Throwable the first captured throwable with subsequent throwables suppressed,
     *     if any instance captured a throwable
     */
    public static void runAndThrow(final CleanUp... cleanUps) throws Throwable {
        Objects.requireNonNull(cleanUps, "cleanUps is null");
        Throwable firstException = null;
        for (CleanUp cleanUp : cleanUps) {
            Objects.requireNonNull(cleanUp, "cleanUp is null");
            if (!cleanUp.hasRun()) {
                cleanUp.run();
            }
            Throwable t = cleanUp.throwable();
            if (t != null) {
                if (firstException == null) {
                    firstException = t;
                } else {
                    firstException.addSuppressed(t);
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    /**
     * Runs all supplied {@code CleanUp} instances and throws the first captured
     * throwable with any subsequent throwables added as
     * {@linkplain Throwable#addSuppressed(Throwable) suppressed exceptions}.
     *
     * <p>Each instance is run in collection iteration order unless it has already been
     * run via {@link #run()} or {@link #runAndThrow()}, in which case its previously
     * captured throwable (if any) is collected without re-execution. If an unrecoverable
     * error occurs, it is rethrown immediately and remaining instances are not run.
     *
     * @param cleanUps the {@code CleanUp} instances to run; must not be {@code null}
     * @throws NullPointerException if {@code cleanUps} is {@code null} or contains a
     *     {@code null} element
     * @throws Throwable the first captured throwable with subsequent throwables suppressed,
     *     if any instance captured a throwable
     */
    public static void runAndThrow(final Collection<CleanUp> cleanUps) throws Throwable {
        Objects.requireNonNull(cleanUps, "cleanUps is null");
        runAllAndThrow(cleanUps);
    }

    private static void runAllAndThrow(final Collection<? extends CleanUp> cleanUps) throws Throwable {
        Throwable firstException = null;
        for (CleanUp cleanUp : cleanUps) {
            Objects.requireNonNull(cleanUp, "cleanUp is null");
            if (!cleanUp.hasRun()) {
                cleanUp.run();
            }
            Throwable t = cleanUp.throwable();
            if (t != null) {
                if (firstException == null) {
                    firstException = t;
                } else {
                    firstException.addSuppressed(t);
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }
}

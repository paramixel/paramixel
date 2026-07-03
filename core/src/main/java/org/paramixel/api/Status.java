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

package org.paramixel.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nonapi.org.paramixel.exception.UserCodeException;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

/**
 * Represents the local execution state of a descriptor.
 *
 * <p>A descriptor starts in {@link #PENDING}, transitions to {@link #RUNNING}, and then
 * reaches exactly one terminal status: {@link #PASSED}, {@link #FAILED}, {@link #SKIPPED},
 * or {@link #ABORTED}.
 *
 * <p>Instances are immutable. The canonical instances are available as static constants
 * on this class. Terminal statuses carrying a message or throwable can be created via
 * the named factory methods {@link #failed(String)}, {@link #skipped(String)}, and
 * {@link #aborted(String)}.
 *
 * <p>Outcome detail carried by a status is exposed through {@link #message()} and
 * {@link #throwable()}.
 */
public final class Status {

    /**
     * Descriptor has been discovered but has not started execution.
     */
    public static final Status PENDING = new Status("PENDING");

    /**
     * Descriptor execution has started and has not yet reached a terminal state.
     */
    public static final Status RUNNING = new Status("RUNNING");

    /**
     * Descriptor completed without errors or exceptions.
     */
    public static final Status PASSED = new Status("PASSED");

    /**
     * Descriptor completed with a failure.
     */
    public static final Status FAILED = new Status("FAILED");

    /**
     * Descriptor was deliberately skipped and did not perform normal work.
     */
    public static final Status SKIPPED = new Status("SKIPPED");

    /**
     * Descriptor was aborted due to a failed precondition or assumption.
     */
    public static final Status ABORTED = new Status("ABORTED");

    private final String statusName;
    private final String message;
    private final Throwable throwable;

    private Status(final String statusName) {
        this.statusName = statusName;
        this.message = null;
        this.throwable = null;
    }

    private Status(final String statusName, final String message) {
        this.statusName = statusName;
        this.message = message;
        this.throwable = null;
    }

    private Status(final String statusName, final String message, final Throwable throwable) {
        this.statusName = statusName;
        this.message = message;
        this.throwable = throwable;
    }

    /**
     * Creates a {@code FAILED} status with a message.
     *
     * @param message the status message; must not be {@code null}
     * @return a failed status with the given message
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public static Status failed(final String message) {
        Objects.requireNonNull(message, "message is null");
        return new Status("FAILED", message);
    }

    /**
     * Creates a {@code FAILED} status with a message and throwable.
     *
     * @param message the status message; must not be {@code null}
     * @param throwable the throwable associated with the status; must not be {@code null}
     * @return a failed status with the given message and throwable
     * @throws NullPointerException if any argument is {@code null}
     */
    public static Status failed(final String message, final Throwable throwable) {
        Objects.requireNonNull(message, "message is null");
        Objects.requireNonNull(throwable, "throwable is null");
        return new Status("FAILED", message, throwable);
    }

    /**
     * Creates a {@code SKIPPED} status with a message.
     *
     * @param message the status message; must not be {@code null}
     * @return a skipped status with the given message
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public static Status skipped(final String message) {
        Objects.requireNonNull(message, "message is null");
        return new Status("SKIPPED", message);
    }

    /**
     * Creates a {@code SKIPPED} status with a message and throwable.
     *
     * @param message the status message; must not be {@code null}
     * @param throwable the throwable associated with the status; must not be {@code null}
     * @return a skipped status with the given message and throwable
     * @throws NullPointerException if any argument is {@code null}
     */
    public static Status skipped(final String message, final Throwable throwable) {
        Objects.requireNonNull(message, "message is null");
        Objects.requireNonNull(throwable, "throwable is null");
        return new Status("SKIPPED", message, throwable);
    }

    /**
     * Creates a {@code ABORTED} status with a message.
     *
     * @param message the status message; must not be {@code null}
     * @return an aborted status with the given message
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public static Status aborted(final String message) {
        Objects.requireNonNull(message, "message is null");
        return new Status("ABORTED", message);
    }

    /**
     * Creates a {@code ABORTED} status with a message and throwable.
     *
     * @param message the status message; must not be {@code null}
     * @param throwable the throwable associated with the status; must not be {@code null}
     * @return an aborted status with the given message and throwable
     * @throws NullPointerException if any argument is {@code null}
     */
    public static Status aborted(final String message, final Throwable throwable) {
        Objects.requireNonNull(message, "message is null");
        Objects.requireNonNull(throwable, "throwable is null");
        return new Status("ABORTED", message, throwable);
    }

    /**
     * Returns the name of this status.
     *
     * <p>The name matches the canonical constant name: {@code "PENDING"}, {@code "RUNNING"},
     * {@code "PASSED"}, {@code "FAILED"}, {@code "SKIPPED"}, or {@code "ABORTED"}.
     *
     * @return the status name; never {@code null}
     */
    public String name() {
        return statusName;
    }

    /**
     * Returns whether this status means execution has not started.
     *
     * @return {@code true} when pending
     */
    public boolean isPending() {
        return this == PENDING || "PENDING".equals(statusName);
    }

    /**
     * Returns whether this status means execution is in progress.
     *
     * @return {@code true} when running
     */
    public boolean isRunning() {
        return this == RUNNING || "RUNNING".equals(statusName);
    }

    /**
     * Returns whether execution completed successfully.
     *
     * @return {@code true} when passed
     */
    public boolean isPassed() {
        return this == PASSED || "PASSED".equals(statusName);
    }

    /**
     * Returns whether execution completed with a failure.
     *
     * @return {@code true} when failed
     */
    public boolean isFailed() {
        return this == FAILED || "FAILED".equals(statusName);
    }

    /**
     * Returns whether execution was skipped.
     *
     * @return {@code true} when skipped
     */
    public boolean isSkipped() {
        return this == SKIPPED || "SKIPPED".equals(statusName);
    }

    /**
     * Returns whether execution was aborted.
     *
     * @return {@code true} when aborted
     */
    public boolean isAborted() {
        return this == ABORTED || "ABORTED".equals(statusName);
    }

    /**
     * Returns the optional message associated with this status.
     *
     * @return the message, or empty when no message is available
     */
    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    /**
     * Returns the optional throwable associated with this status.
     *
     * @return the throwable, or empty when no throwable is available
     */
    public Optional<Throwable> throwable() {
        return Optional.ofNullable(throwable);
    }

    /**
     * Computes the aggregate status from a list of child descriptors.
     *
     * <p>Severity ordering: {@code FAILED} &gt; {@code ABORTED} &gt; {@code RUNNING}/{@code PENDING} &gt;
     * {@code SKIPPED} &gt; {@code PASSED}. Non-terminal child statuses (RUNNING or PENDING) cause the
     * aggregate to be {@link #RUNNING}, indicating that execution is incomplete.
     *
     * @param descriptors the child descriptors; must not be {@code null}
     * @return the aggregate status
     */
    public static Status aggregate(final List<Descriptor> descriptors) {
        boolean hasFailed = false;
        boolean hasAborted = false;
        boolean hasNonTerminal = false;
        boolean hasSkipped = false;
        for (Descriptor descriptor : descriptors) {
            if (descriptor.isFailed()) {
                hasFailed = true;
            }
            if (descriptor.isAborted()) {
                hasAborted = true;
            }
            if (!descriptor.isCompleted()) {
                hasNonTerminal = true;
            }
            if (descriptor.isSkipped()) {
                hasSkipped = true;
            }
        }
        if (hasFailed) {
            return FAILED;
        }
        if (hasAborted) {
            return ABORTED;
        }
        if (hasNonTerminal) {
            return RUNNING;
        }
        if (hasSkipped) {
            return SKIPPED;
        }
        return PASSED;
    }

    /**
     * Maps a throwable to the corresponding terminal status.
     *
     * <p>If the throwable is a framework wrapper, its cause is unwrapped first.
     * Then {@link AbortedException}, {@link SkipException}, and {@link FailException}
     * are mapped to their corresponding statuses. Unrecoverable errors are rethrown.
     * {@link InterruptedException} restores the interrupt flag.
     *
     * @param throwable the throwable to map; must not be {@code null}
     * @return the terminal status; never {@code null}
     * @throws Error if the throwable is an unrecoverable error
     */
    public static Status fromThrowable(final Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable is null");
        var t = throwable;
        if (t instanceof UserCodeException userCodeException) {
            t = userCodeException.getCause();
            if (t == null) {
                t = throwable;
            }
        }
        UnrecoverableErrors.rethrowIfUnrecoverable(t);
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        if (t instanceof AbortedException e) {
            return aborted(e.getMessage(), e);
        }
        if (t instanceof SkipException e) {
            return skipped(e.getMessage(), e);
        }
        if (t instanceof FailException e) {
            return failed(e.getMessage(), e);
        }
        return failed(t.getMessage() != null ? t.getMessage() : "failed", t);
    }

    @Override
    public String toString() {
        return statusName;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Status other)) {
            return false;
        }
        return statusName.equals(other.statusName)
                && Objects.equals(message, other.message)
                && Objects.equals(throwable, other.throwable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusName, message, throwable);
    }
}

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

import java.util.Objects;
import java.util.Optional;

/**
 * The local execution state of a descriptor: a terminal or in-flight {@link Kind}, plus optional
 * detail.
 *
 * <p>A descriptor starts in {@link #PENDING}, transitions to {@link #RUNNING}, and then reaches
 * exactly one terminal kind: {@link Kind#PASSED}, {@link Kind#FAILED}, {@link Kind#SKIPPED}, or
 * {@link Kind#ABORTED}. Compare with {@link #kind()}, which supports {@code switch}, rather than with
 * a string or with the canonical constants: {@code Status.failed("x").equals(Status.FAILED)} is
 * {@code false} because the message differs.
 *
 * <p>Instances are immutable. The canonical, detail-free instances are the constants on this class;
 * terminal statuses that carry a message or a throwable are created with {@link #failed(String)},
 * {@link #failed(String, Throwable)}, {@link #skipped(String)}, {@link #skipped(String, Throwable)},
 * {@link #aborted(String)} and {@link #aborted(String, Throwable)}.
 *
 * <p>Detail is exposed through {@link #message()} and {@link #throwable()}. Equality compares only
 * {@link #kind()} and {@link #message()}: the throwable is ignored so that two equal-looking failures
 * compare equal.
 */
public final class Status {

    /**
     * The kind of a status.
     *
     * <p>Severity ordering used when a container aggregates its children:
     * {@code FAILED} &gt; {@code ABORTED} &gt; {@code RUNNING}/{@code PENDING} &gt; {@code SKIPPED}
     * &gt; {@code PASSED}.
     */
    public enum Kind {

        /** The descriptor has been discovered but has not started execution. */
        PENDING(false),

        /** Descriptor execution has started and has not reached a terminal kind. */
        RUNNING(false),

        /** Descriptor completed without errors or exceptions. */
        PASSED(true),

        /** Descriptor completed with a failure. */
        FAILED(true),

        /** Descriptor was deliberately skipped and did not perform normal work. */
        SKIPPED(true),

        /** Descriptor was aborted because a precondition or assumption did not hold. */
        ABORTED(true);

        private final boolean terminal;

        Kind(final boolean terminal) {
            this.terminal = terminal;
        }

        /**
         * Returns whether this kind is terminal.
         *
         * @return {@code true} for {@code PASSED}, {@code FAILED}, {@code SKIPPED} and {@code ABORTED}
         */
        public boolean isTerminal() {
            return terminal;
        }
    }

    /**
     * Descriptor has been discovered but has not started execution.
     */
    public static final Status PENDING = new Status(Kind.PENDING, null, null);

    /**
     * Descriptor execution has started and has not yet reached a terminal state.
     */
    public static final Status RUNNING = new Status(Kind.RUNNING, null, null);

    /**
     * Descriptor completed without errors or exceptions.
     */
    public static final Status PASSED = new Status(Kind.PASSED, null, null);

    /**
     * Descriptor completed with a failure.
     */
    public static final Status FAILED = new Status(Kind.FAILED, null, null);

    /**
     * Descriptor was deliberately skipped and did not perform normal work.
     */
    public static final Status SKIPPED = new Status(Kind.SKIPPED, null, null);

    /**
     * Descriptor was aborted due to a failed precondition or assumption.
     */
    public static final Status ABORTED = new Status(Kind.ABORTED, null, null);

    private final Kind kind;
    private final String message;
    private final Throwable throwable;

    private Status(final Kind kind, final String message, final Throwable throwable) {
        this.kind = Objects.requireNonNull(kind, "kind is null");
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
        return new Status(Kind.FAILED, Objects.requireNonNull(message, "message is null"), null);
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
        return new Status(
                Kind.FAILED,
                Objects.requireNonNull(message, "message is null"),
                Objects.requireNonNull(throwable, "throwable is null"));
    }

    /**
     * Creates a {@code SKIPPED} status with a message.
     *
     * @param message the status message; must not be {@code null}
     * @return a skipped status with the given message
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public static Status skipped(final String message) {
        return new Status(Kind.SKIPPED, Objects.requireNonNull(message, "message is null"), null);
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
        return new Status(
                Kind.SKIPPED,
                Objects.requireNonNull(message, "message is null"),
                Objects.requireNonNull(throwable, "throwable is null"));
    }

    /**
     * Creates an {@code ABORTED} status with a message.
     *
     * @param message the status message; must not be {@code null}
     * @return an aborted status with the given message
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public static Status aborted(final String message) {
        return new Status(Kind.ABORTED, Objects.requireNonNull(message, "message is null"), null);
    }

    /**
     * Creates an {@code ABORTED} status with a message and throwable.
     *
     * @param message the status message; must not be {@code null}
     * @param throwable the throwable associated with the status; must not be {@code null}
     * @return an aborted status with the given message and throwable
     * @throws NullPointerException if any argument is {@code null}
     */
    public static Status aborted(final String message, final Throwable throwable) {
        return new Status(
                Kind.ABORTED,
                Objects.requireNonNull(message, "message is null"),
                Objects.requireNonNull(throwable, "throwable is null"));
    }

    /**
     * Returns the kind of this status.
     *
     * @return the status kind; never {@code null}
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns whether execution has not started.
     *
     * @return {@code true} when the kind is {@link Kind#PENDING}
     */
    public boolean isPending() {
        return kind == Kind.PENDING;
    }

    /**
     * Returns whether execution is in progress.
     *
     * @return {@code true} when the kind is {@link Kind#RUNNING}
     */
    public boolean isRunning() {
        return kind == Kind.RUNNING;
    }

    /**
     * Returns whether execution completed successfully.
     *
     * @return {@code true} when the kind is {@link Kind#PASSED}
     */
    public boolean isPassed() {
        return kind == Kind.PASSED;
    }

    /**
     * Returns whether execution completed with a failure.
     *
     * @return {@code true} when the kind is {@link Kind#FAILED}
     */
    public boolean isFailed() {
        return kind == Kind.FAILED;
    }

    /**
     * Returns whether execution was skipped.
     *
     * @return {@code true} when the kind is {@link Kind#SKIPPED}
     */
    public boolean isSkipped() {
        return kind == Kind.SKIPPED;
    }

    /**
     * Returns whether execution was aborted.
     *
     * @return {@code true} when the kind is {@link Kind#ABORTED}
     */
    public boolean isAborted() {
        return kind == Kind.ABORTED;
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

    @Override
    public String toString() {
        return kind.name();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Status other)) {
            return false;
        }
        return kind == other.kind && Objects.equals(message, other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, message);
    }
}

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

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of one execution occurrence, phase, or attempt.
 *
 * <p>A status is a {@link Kind} plus an optional human-readable reason and an optional throwable. Instances are
 * immutable; compare with {@link #kind()} rather than with the canonical constants, because a status that carries a
 * message is not equal to the message-free constant.
 *
 * <p>Two kinds are easy to confuse and are deliberately distinct:
 *
 * <ul>
 *   <li>{@link Kind#ABORTED} — the test code decided that a precondition or assumption did not hold
 *       ({@link org.paramixel.api.exception.AbortException}). The task is not a failure and not a cancellation.</li>
 *   <li>{@link Kind#CANCELED} — the run, a timeout, or a shutdown stopped the task cooperatively. The task never
 *       produced a verdict of its own.</li>
 * </ul>
 *
 * <p>Aggregation uses the fixed severity order {@code FAILED > CANCELED > ABORTED > SKIPPED > PASSED}
 * (see {@link #mostSevere(Collection)}).
 */
public final class Status {

    /**
     * The kind of a status.
     *
     * <p>{@link #severity()} orders kinds for aggregation; higher values are more severe.
     */
    public enum Kind {

        /** The occurrence is compiled but has not been admitted. */
        PENDING(false, 0),

        /** Execution has started and has not reached a terminal kind. */
        RUNNING(false, 0),

        /** Every required phase succeeded. */
        PASSED(true, 1),

        /** Deliberately not executed: condition false, dependency unsatisfied, selection, or stop policy. */
        SKIPPED(true, 2),

        /** An assumption or precondition abort signal ({@link org.paramixel.api.exception.AbortException}). */
        ABORTED(true, 3),

        /** Cooperative cancellation, timeout-driven abortion, or run shutdown. */
        CANCELED(true, 4),

        /** An operation, validation-at-runtime, timeout, or finalization failed. */
        FAILED(true, 5);

        private final boolean terminal;
        private final int severity;

        Kind(final boolean terminal, final int severity) {
            this.terminal = terminal;
            this.severity = severity;
        }

        /**
         * Returns whether this kind is terminal.
         *
         * @return {@code true} for {@code PASSED}, {@code SKIPPED}, {@code ABORTED}, {@code CANCELED} and
         *     {@code FAILED}
         */
        public boolean isTerminal() {
            return terminal;
        }

        /**
         * Returns the aggregation severity of this kind.
         *
         * @return the severity; higher values are more severe
         */
        public int severity() {
            return severity;
        }
    }

    /** The occurrence is compiled but has not been admitted. */
    public static final Status PENDING = new Status(Kind.PENDING, null, null);

    /** Execution has started and has not reached a terminal kind. */
    public static final Status RUNNING = new Status(Kind.RUNNING, null, null);

    /** Every required phase succeeded. */
    public static final Status PASSED = new Status(Kind.PASSED, null, null);

    /** Deliberately not executed. */
    public static final Status SKIPPED = new Status(Kind.SKIPPED, null, null);

    /** An assumption or precondition did not hold. */
    public static final Status ABORTED = new Status(Kind.ABORTED, null, null);

    /** Cooperative cancellation or timeout-driven abortion. */
    public static final Status CANCELED = new Status(Kind.CANCELED, null, null);

    /** An operation, validation-at-runtime, or finalization failed. */
    public static final Status FAILED = new Status(Kind.FAILED, null, null);

    private final Kind kind;
    private final String message;
    private final Throwable throwable;

    private Status(final Kind kind, final String message, final Throwable throwable) {
        this.kind = Objects.requireNonNull(kind, "kind is null");
        this.message = message;
        this.throwable = throwable;
    }

    /**
     * Creates a {@code FAILED} status.
     *
     * @param message the reason; must not be {@code null}
     * @return the status; never {@code null}
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public static Status failed(final String message) {
        return new Status(Kind.FAILED, Objects.requireNonNull(message, "message is null"), null);
    }

    /**
     * Creates a {@code FAILED} status carrying a throwable.
     *
     * @param message the reason; must not be {@code null}
     * @param throwable the cause; must not be {@code null}
     * @return the status; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static Status failed(final String message, final Throwable throwable) {
        return new Status(
                Kind.FAILED,
                Objects.requireNonNull(message, "message is null"),
                Objects.requireNonNull(throwable, "throwable is null"));
    }

    /**
     * Creates a {@code SKIPPED} status.
     *
     * @param message the reason; must not be {@code null}
     * @return the status; never {@code null}
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public static Status skipped(final String message) {
        return new Status(Kind.SKIPPED, Objects.requireNonNull(message, "message is null"), null);
    }

    /**
     * Creates a {@code SKIPPED} status carrying a throwable.
     *
     * @param message the reason; must not be {@code null}
     * @param throwable the cause; must not be {@code null}
     * @return the status; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static Status skipped(final String message, final Throwable throwable) {
        return new Status(
                Kind.SKIPPED,
                Objects.requireNonNull(message, "message is null"),
                Objects.requireNonNull(throwable, "throwable is null"));
    }

    /**
     * Creates an {@code ABORTED} status.
     *
     * @param message the reason; must not be {@code null}
     * @return the status; never {@code null}
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public static Status aborted(final String message) {
        return new Status(Kind.ABORTED, Objects.requireNonNull(message, "message is null"), null);
    }

    /**
     * Creates an {@code ABORTED} status carrying a throwable.
     *
     * @param message the reason; must not be {@code null}
     * @param throwable the cause; must not be {@code null}
     * @return the status; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static Status aborted(final String message, final Throwable throwable) {
        return new Status(
                Kind.ABORTED,
                Objects.requireNonNull(message, "message is null"),
                Objects.requireNonNull(throwable, "throwable is null"));
    }

    /**
     * Creates a {@code CANCELED} status.
     *
     * @param message the reason; must not be {@code null}
     * @return the status; never {@code null}
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public static Status canceled(final String message) {
        return new Status(Kind.CANCELED, Objects.requireNonNull(message, "message is null"), null);
    }

    /**
     * Creates a {@code CANCELED} status carrying a throwable.
     *
     * @param message the reason; must not be {@code null}
     * @param throwable the cause; must not be {@code null}
     * @return the status; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static Status canceled(final String message, final Throwable throwable) {
        return new Status(
                Kind.CANCELED,
                Objects.requireNonNull(message, "message is null"),
                Objects.requireNonNull(throwable, "throwable is null"));
    }

    /**
     * Returns the most severe kind of the supplied statuses.
     *
     * <p>Empty input yields {@link #PASSED}: a task with no recorded phases has nothing to fail. The message and
     * throwable of the returned status are taken from the first status that has the winning kind.
     *
     * @param statuses the statuses to aggregate; must not be {@code null} and must not contain {@code null}
     * @return the most severe status; never {@code null}
     * @throws NullPointerException if the collection or an element is {@code null}
     */
    public static Status mostSevere(final Collection<Status> statuses) {
        Objects.requireNonNull(statuses, "statuses is null");
        Status result = PASSED;
        for (Status status : statuses) {
            Objects.requireNonNull(status, "status is null");
            if (status.kind.severity > result.kind.severity) {
                result = status;
            }
        }
        return result;
    }

    /**
     * Returns the kind.
     *
     * @return the kind; never {@code null}
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns whether execution has not started.
     *
     * @return {@code true} for {@link Kind#PENDING}
     */
    public boolean isPending() {
        return kind == Kind.PENDING;
    }

    /**
     * Returns whether execution is in progress.
     *
     * @return {@code true} for {@link Kind#RUNNING}
     */
    public boolean isRunning() {
        return kind == Kind.RUNNING;
    }

    /**
     * Returns whether the outcome succeeded.
     *
     * @return {@code true} for {@link Kind#PASSED}
     */
    public boolean isPassed() {
        return kind == Kind.PASSED;
    }

    /**
     * Returns whether the outcome failed.
     *
     * @return {@code true} for {@link Kind#FAILED}
     */
    public boolean isFailed() {
        return kind == Kind.FAILED;
    }

    /**
     * Returns whether the outcome was skipped.
     *
     * @return {@code true} for {@link Kind#SKIPPED}
     */
    public boolean isSkipped() {
        return kind == Kind.SKIPPED;
    }

    /**
     * Returns whether the outcome was an assumption abort.
     *
     * @return {@code true} for {@link Kind#ABORTED}
     */
    public boolean isAborted() {
        return kind == Kind.ABORTED;
    }

    /**
     * Returns whether the outcome was canceled.
     *
     * @return {@code true} for {@link Kind#CANCELED}
     */
    public boolean isCanceled() {
        return kind == Kind.CANCELED;
    }

    /**
     * Returns whether the outcome is terminal.
     *
     * @return {@code true} when the kind is terminal
     */
    public boolean isTerminal() {
        return kind.isTerminal();
    }

    /**
     * Returns the reason, when one is available.
     *
     * @return the reason, or an empty optional
     */
    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    /**
     * Returns the throwable, when one is available.
     *
     * @return the throwable, or an empty optional
     */
    public Optional<Throwable> throwable() {
        return Optional.ofNullable(throwable);
    }

    /**
     * Returns this status with the supplied message when it has none.
     *
     * @param fallback the message to apply; must not be {@code null}
     * @return this status when it already has a message, otherwise a copy carrying {@code fallback}
     * @throws NullPointerException if {@code fallback} is {@code null}
     */
    public Status withDefaultMessage(final String fallback) {
        Objects.requireNonNull(fallback, "fallback is null");
        return message == null ? new Status(kind, fallback, throwable) : this;
    }

    @Override
    public String toString() {
        return message == null ? kind.name() : kind.name() + " (" + message + ")";
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

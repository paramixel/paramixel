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

package org.paramixel.core;

import java.util.Optional;

/**
 * Identifies the state of an action execution.
 *
 * <p>Status represents the outcome of an {@link Action} and transitions through a simple state machine:
 * <ul>
 *   <li>{@code STAGED} - Initial state before execution begins</li>
 *   <li>{@code PASS} - Successful completion (no exceptions)</li>
 *   <li>{@code FAIL} - Failed completion (exception thrown or {@link FailException})</li>
 *   <li>{@code SKIP} - Skipped execution ({@link SkipException} or parent-level skip)</li>
 * </ul>
 *
 * <p>Each status is mutually exclusive; only one method will return {@code true} at any time.
 * The status transitions exactly once from STAGED to PASS, FAIL, or SKIP during execution.</p>
 *
 * <p>For parent actions, status is typically derived from child results:
 * <ul>
 *   <li>{@code FAIL} - If any child failed</li>
 *   <li>{@code SKIP} - If any child skipped and none failed</li>
 *   <li>{@code PASS} - If all children passed</li>
 * </ul>
 *
 * <p>Some action types modify this behavior (e.g., {@link org.paramixel.core.action.Lifecycle}
 * has special phase propagation rules).</p>
 *
 * @see Result
 * @see FailException
 * @see SkipException
 */
public final class Status {

    private enum Kind {
        STAGED,
        PASS,
        FAILURE,
        SKIP
    }

    private static final Status STAGED = new Status(Kind.STAGED, null, null);
    private static final Status PASS = new Status(Kind.PASS, null, null);
    private static final Status SKIP = new Status(Kind.SKIP, null, null);

    private final Kind kind;
    private final String message;
    private final Throwable throwable;

    private Status(Kind kind, String message, Throwable throwable) {
        this.kind = kind;
        this.message = message;
        this.throwable = throwable;
    }

    /**
     * Returns a STAGED status.
     *
     * @return a STAGED status
     */
    public static Status staged() {
        return STAGED;
    }

    /**
     * Returns a PASS status.
     *
     * @return a PASS status
     */
    public static Status pass() {
        return PASS;
    }

    /**
     * Returns a FAILURE status with the given throwable.
     *
     * @param t the throwable that caused the failure
     * @return a FAILURE status
     */
    public static Status failure(Throwable t) {
        return new Status(Kind.FAILURE, null, t);
    }

    /**
     * Returns a FAILURE status with the given message.
     *
     * @param message the failure message
     * @return a FAILURE status
     */
    public static Status failure(String message) {
        return new Status(Kind.FAILURE, message, null);
    }

    /**
     * Returns a FAILURE status with the given throwable and message.
     *
     * @param t the throwable that caused the failure
     * @param message the failure message
     * @return a FAILURE status
     */
    public static Status failure(Throwable t, String message) {
        return new Status(Kind.FAILURE, message, t);
    }

    /**
     * Returns a FAILURE status with no message or throwable.
     *
     * @return a FAILURE status
     */
    public static Status failure() {
        return new Status(Kind.FAILURE, null, null);
    }

    /**
     * Returns a SKIP status with no reason.
     *
     * @return a SKIP status
     */
    public static Status skip() {
        return SKIP;
    }

    /**
     * Returns a SKIP status with the given reason.
     *
     * @param reason the reason for skipping
     * @return a SKIP status
     */
    public static Status skip(String reason) {
        return new Status(Kind.SKIP, reason, null);
    }

    /**
     * Returns whether this action is in the STAGED state.
     *
     * <p>STAGED is the initial state before execution begins. It is set immediately before
     * {@link Listener#beforeAction} is invoked and remains until execution completes.</p>
     *
     * <p>While staged, the action has not yet executed any logic. This state is useful for
     * tracking execution progress and for listeners that need to know when an action is about
     * to run.</p>
     *
     * <p>The STAGED state transitions to exactly one of PASS, FAIL, or SKIP after execution.</p>
     *
     * @return {@code true} if this action is staged (not yet executed), {@code false} otherwise
     */
    public boolean isStaged() {
        return kind == Kind.STAGED;
    }

    /**
     * Returns whether this action completed successfully.
     *
     * <p>PASS indicates that the action executed without throwing any exceptions.
     * This includes successful completion of all child actions for parent actions.</p>
     *
     * <p>An action is PASS if and only if:
     * <ul>
     *   <li>No exceptions were thrown during execution</li>
     *   <li>No {@link FailException} was thrown</li>
     *   <li>No {@link SkipException} was thrown</li>
     *   <li>For parent actions, all children completed with PASS status</li>
     * </ul>
     *
     * <p>PASS is a terminal state; once set, it never changes.</p>
     *
     * @return {@code true} if this action passed, {@code false} otherwise
     */
    public boolean isPass() {
        return kind == Kind.PASS;
    }

    /**
     * Returns whether this action failed during execution.
     *
     * <p>FAIL indicates that execution encountered an error. This can happen in several ways:
     * <ul>
     *   <li>An uncaught exception was thrown</li>
     *   <li>A {@link FailException} was thrown explicitly</li>
     *   <li>For parent actions, any child action failed</li>
     * </ul>
     *
     * <p>When an action fails, the exception that caused the failure is available via
     * {@link #getThrowable()}. The failure message is available via {@link #getMessage()}.</p>
     *
     * <p>FAIL is a terminal state; once set, it never changes. In strict action types
     * (e.g., {@link org.paramixel.core.action.StrictSequential}), remaining siblings may be
     * skipped when a failure occurs.</p>
     *
     * @return {@code true} if this action failed, {@code false} otherwise
     * @see #getThrowable()
     * @see #getMessage()
     */
    public boolean isFailure() {
        return kind == Kind.FAILURE;
    }

    /**
     * Returns whether this action was skipped.
     *
     * <p>SKIP indicates that execution was bypassed. This can happen in several ways:
     * <ul>
     *   <li>A {@link SkipException} was thrown explicitly by the action</li>
     *   <li>A parent action was skipped (descendants inherit skip)</li>
     *   <li>A parent action failed and remaining siblings are being skipped in a strict action type</li>
     *   <li>A lifecycle action's before phase skipped the main phase</li>
     * </ul>
     *
     * <p>When an action is skipped, the reason is available via {@link #getMessage()}.
     * The throwable will be empty unless a {@link SkipException} caused the skip.</p>
     *
     * <p>SKIP is a terminal state; once set, it never changes. Skipped actions do not
     * execute their business logic.</p>
     *
     * @return {@code true} if this action was skipped, {@code false} otherwise
     * @see #getMessage()
     * @see SkipException
     */
    public boolean isSkip() {
        return kind == Kind.SKIP;
    }

    /**
     * Returns the human-readable display name for this status.
     *
     * <p>The display name is a short, uppercase string representing the status:
     * <ul>
     *   <li>{@code "STAGED"} - Before execution</li>
     *   <li>{@code "PASS"} - Successful execution</li>
     *   <li>{@code "FAIL"} - Failed execution</li>
     *   <li>{@code "SKIP"} - Skipped execution</li>
     * </ul>
     *
     * <p>Display names are typically used in console output, logs, and user interfaces.
     * They are formatted with ANSI colors in console listeners for visual distinction.</p>
     *
     * @return the display name; never {@code null}, always a non-empty uppercase string
     */
    public String getDisplayName() {
        return switch (kind) {
            case STAGED -> "STAGED";
            case PASS -> "PASS";
            case FAILURE -> "FAIL";
            case SKIP -> "SKIP";
        };
    }

    /**
     * Returns a message providing additional context about the status.
     *
     * <p>The message provides human-readable information about why the action achieved
     * its current status:
     * <ul>
     *   <li>For PASS: typically empty</li>
     *   <li>For FAIL: contains the failure reason or exception message</li>
     *   <li>For SKIP: contains the skip reason or explanation</li>
     *   <li>For STAGED: typically empty</li>
     * </ul>
     *
     * <p>Messages are optional and may not be present for all status values. Use
     * {@link Optional#isPresent()} to check availability.</p>
     *
     * @return an {@link Optional} containing the status message, or empty if no message is available
     * @see #getThrowable()
     */
    public Optional<String> getMessage() {
        if (message != null) {
            return Optional.of(message);
        }
        if (throwable != null) {
            return Optional.ofNullable(throwable.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Returns the exception associated with this status, if any.
     *
     * <p>The throwable is present only when the status is FAIL or SKIP was caused by
     * a {@link SkipException}:
     * <ul>
     *   <li>For FAIL caused by an exception: contains the thrown exception</li>
     *   <li>For FAIL caused by {@link FailException}: contains the FailException</li>
     *   <li>For SKIP caused by {@link SkipException}: contains the SkipException</li>
     *   <li>For SKIP caused by parent skip: empty</li>
     *   <li>For PASS: empty</li>
     *   <li>For STAGED: empty</li>
     * </ul>
     *
     * <p>The throwable includes the full stack trace for debugging purposes. For exceptions
     * that wrap other exceptions, use {@link Throwable#getCause()} to access the root cause.</p>
     *
     * <p><strong>Note:</strong> The throwable reference may retain memory. For long-running
     * applications, consider whether you need to retain stack traces for failed actions.</p>
     *
     * @return an {@link Optional} containing the exception, or empty if no exception is associated
     * @see #getMessage()
     * @see FailException
     * @see SkipException
     */
    public Optional<Throwable> getThrowable() {
        return Optional.ofNullable(throwable);
    }

    /**
     * Returns the human-readable display name for this status.
     *
     * @return the display name used by {@link Result} formatting and listeners
     */
    @Override
    public String toString() {
        return getDisplayName();
    }
}

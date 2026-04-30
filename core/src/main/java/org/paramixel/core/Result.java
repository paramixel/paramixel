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

import java.time.Duration;
import java.util.Objects;

/**
 * Describes the outcome of executing an {@link Action}.
 *
 * <p>Result is a lightweight object containing only status and timing information.
 * Navigate the {@link Action} tree to access hierarchy and names.</p>
 *
 * <p>Each action owns exactly one result, which transitions through the following lifecycle:
 * <ul>
 *   <li>{@link Status#isStaged()} - Before execution begins</li>
 *   <li>{@link Status#isPass()} - After successful execution</li>
 *   <li>{@link Status#isFailure()} - After failed execution (exception thrown or {@link FailException})</li>
 *   <li>{@link Status#isSkip()} - After skipped execution ({@link SkipException} or parent-level skip)</li>
 * </ul>
 *
 * <p>Results are immutable and thread-safe. Access the result via {@link Action#getResult()}.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * Action action = Direct.of("myAction", ctx -> {
 *     System.out.println("Executing");
 * });
 * runner.run(action);
 * Result result = action.getResult();
 * if (result.getStatus().isPass()) {
 *     System.out.println("Success in " + result.getElapsedTime().toMillis() + "ms");
 * }
 * }</pre>
 */
public final class Result {

    private final Status status;
    private final Duration timing;

    private Result(Status status, Duration timing) {
        this.status = status;
        this.timing = timing;
    }

    /**
     * Creates a result.
     *
     * @param status the status; must not be {@code null}
     * @param timing the timing; must not be {@code null}
     * @return a new Result
     * @throws NullPointerException if {@code status} or {@code timing} is {@code null}
     */
    public static Result of(Status status, Duration timing) {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(timing, "timing must not be null");
        return new Result(status, timing);
    }

    /**
     * Creates a staged result.
     *
     * @return a new Result with STAGED status and zero duration
     */
    public static Result staged() {
        return new Result(Status.staged(), Duration.ZERO);
    }

    /**
     * Creates a passing result.
     *
     * @param timing the elapsed time; must not be {@code null}
     * @return a new Result with PASS status
     * @throws NullPointerException if {@code timing} is {@code null}
     */
    public static Result pass(Duration timing) {
        Objects.requireNonNull(timing, "timing must not be null");
        return new Result(Status.pass(), timing);
    }

    /**
     * Creates a failing result.
     *
     * @param timing the elapsed time; must not be {@code null}
     * @param failure the failure throwable; must not be {@code null}
     * @return a new Result with FAIL status
     * @throws NullPointerException if {@code timing} or {@code failure} is {@code null}
     */
    public static Result fail(Duration timing, Throwable failure) {
        Objects.requireNonNull(timing, "timing must not be null");
        Objects.requireNonNull(failure, "failure must not be null");
        return new Result(Status.failure(failure), timing);
    }

    /**
     * Creates a failing result with a message.
     *
     * @param timing the elapsed time; must not be {@code null}
     * @param failureMessage the failure message
     * @return a new Result with FAIL status
     * @throws NullPointerException if {@code timing} is {@code null}
     */
    public static Result fail(Duration timing, String failureMessage) {
        Objects.requireNonNull(timing, "timing must not be null");
        return new Result(Status.failure(failureMessage), timing);
    }

    /**
     * Creates a skipped result.
     *
     * @param timing the elapsed time; must not be {@code null}
     * @return a new Result with SKIP status
     * @throws NullPointerException if {@code timing} is {@code null}
     */
    public static Result skip(Duration timing) {
        Objects.requireNonNull(timing, "timing must not be null");
        return new Result(Status.skip(), timing);
    }

    /**
     * Creates a skipped result with a reason.
     *
     * @param timing the elapsed time; must not be {@code null}
     * @param skipReason the reason for skipping
     * @return a new Result with SKIP status
     * @throws NullPointerException if {@code timing} is {@code null}
     */
    public static Result skip(Duration timing, String skipReason) {
        Objects.requireNonNull(timing, "timing must not be null");
        return new Result(Status.skip(skipReason), timing);
    }

    /**
     * Returns the execution status.
     *
     * <p>The status indicates the outcome of the action execution:
     * <ul>
     *   <li>{@link Status#isStaged()} - Before execution starts (during {@link Listener#beforeAction})</li>
     *   <li>{@link Status#isPass()} - After successful completion (no exceptions)</li>
     *   <li>{@link Status#isFailure()} - After failure (exception thrown or {@link FailException})</li>
     *   <li>{@link Status#isSkip()} - After skip ({@link SkipException} or parent-level skip)</li>
     * </ul>
     *
     * <p>Status transitions are guaranteed: STAGED → PASS/FAIL/SKIP exactly once per execution.</p>
     *
     * @return the execution status; never {@code null}
     * @see Status
     * @see Listener#afterAction(Context, Action, Result)
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Returns the elapsed time for this action's execution.
     *
     * <p>The duration is measured from the moment {@link Listener#beforeAction} is invoked
     * until {@link Listener#afterAction} is invoked. This includes the execution of all child actions.</p>
     *
     * <p>For parent actions, the duration includes the total time spent executing all children,
     * accounting for sequential or parallel execution patterns as defined by the action type.</p>
     *
     * <p>Timing precision is typically milliseconds, but the exact precision depends on the
     * underlying system clock and the {@link Duration} implementation.</p>
     *
     * @return the elapsed execution time; never {@code null}, may be zero for instant actions
     * @see Duration
     */
    public Duration getElapsedTime() {
        return timing;
    }

    /**
     * Returns a compact diagnostic representation of this result.
     *
     * @return a string containing the status display and elapsed time in milliseconds
     */
    @Override
    public String toString() {
        return status + " | " + timing.toMillis() + " ms";
    }
}

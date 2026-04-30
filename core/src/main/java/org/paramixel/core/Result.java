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
public interface Result {

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
    Status getStatus();

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
     * underlying system clock and the {@link java.time.Duration} implementation.</p>
     *
     * @return the elapsed execution time; never {@code null}, may be zero for instant actions
     * @see java.time.Duration
     */
    Duration getElapsedTime();
}

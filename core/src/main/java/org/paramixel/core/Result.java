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
import java.util.List;
import java.util.Optional;
import org.paramixel.core.internal.DefaultResult;

/**
 * Describes the outcome of executing an {@link Action}.
 */
public interface Result {

    /**
     * Identifies the terminal state of an executed action.
     */
    enum Status {

        /**
         * Indicates that the action completed successfully.
         */
        PASS,

        /**
         * Indicates that the action failed.
         */
        FAIL,

        /**
         * Indicates that the action was skipped.
         */
        SKIP
    }

    /**
     * Returns the action that produced this result.
     *
     * @return The executed action.
     */
    Action action();

    /**
     * Returns the terminal status of the action.
     *
     * @return The action status.
     */
    Status status();

    /**
     * Returns the elapsed execution time.
     *
     * @return The measured duration.
     */
    Duration timing();

    /**
     * Returns the failure that caused this result, when present.
     *
     * @return An {@link Optional} containing the failure, or empty when the
     *     action did not fail.
     */
    Optional<Throwable> failure();

    /**
     * Returns the parent result when this result was produced by a child action.
     *
     * @return the parent result in the execution tree, or empty when this result was produced by a root action.
     */
    Optional<Result> parent();

    /**
     * Returns results produced by child actions.
     *
     * @return The child results in execution order; never null.
     */
    List<Result> children();

    /**
     * Creates a passing result.
     *
     * @param action The action; must not be null.
     * @param timing The execution duration; must not be null.
     * @return A passing result.
     */
    static Result pass(Action action, Duration timing) {
        return DefaultResult.pass(action, timing);
    }

    /**
     * Creates a failing result.
     *
     * @param action The action; must not be null.
     * @param timing The execution duration; must not be null.
     * @param failure The failure; may be null.
     * @return A failing result.
     */
    static Result fail(Action action, Duration timing, Throwable failure) {
        return DefaultResult.fail(action, timing, failure);
    }

    /**
     * Creates a skipped result.
     *
     * @param action The action; must not be null.
     * @param timing The execution duration; must not be null.
     * @return A skipped result.
     */
    static Result skip(Action action, Duration timing) {
        return DefaultResult.skip(action, timing);
    }

    /**
     * Creates a skipped result with a reason.
     *
     * @param action The action; must not be null.
     * @param timing The execution duration; must not be null.
     * @param skipReason The reason for skipping; may be null.
     * @return A skipped result.
     */
    static Result skip(Action action, Duration timing, Throwable skipReason) {
        return DefaultResult.skip(action, timing, skipReason);
    }

    /**
     * Creates a result with the specified status.
     *
     * @param action The action; must not be null.
     * @param status The result status; must not be null.
     * @param timing The execution duration; must not be null.
     * @param failure The failure; may be null.
     * @param children The child results; must not be null.
     * @return A result with the specified properties.
     */
    static Result of(Action action, Status status, Duration timing, Throwable failure, List<Result> children) {
        return DefaultResult.of(action, status, timing, failure, children);
    }
}

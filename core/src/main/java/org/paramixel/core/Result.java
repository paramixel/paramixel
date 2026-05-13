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
import java.util.Objects;
import java.util.Optional;
import org.paramixel.core.internal.DefaultResult;

/**
 * Describes the outcome of running an {@link Action}.
 *
 * <p>A {@link Result} records the action that was run, its final {@link Status}, run duration, and any nested
 * child results produced by composed actions.
 */
public interface Result {

    /**
     * Produces a result indicating that the action has not yet been run.
     *
     * @param action the action represented by the result
     * @return a staged result
     */
    static Result staged(Action action) {
        return builder(action).status(Status.staged()).build();
    }

    /**
     * Produces a result indicating that the action completed successfully.
     *
     * @param action the action represented by the result
     * @return a passing result
     */
    static Result pass(Action action) {
        return builder(action).status(Status.pass()).build();
    }

    /**
     * Produces a result indicating that the action was skipped.
     *
     * @param action the action represented by the result
     * @return a skipped result
     */
    static Result skip(Action action) {
        return builder(action).status(Status.skip()).build();
    }

    /**
     * Produces a result indicating that the action failed with the supplied throwable.
     *
     * @param action the action represented by the result
     * @param throwable the exception that caused the action to fail
     * @return a failed result
     */
    static Result failure(Action action, Throwable throwable) {
        return builder(action).status(Status.failure(throwable)).build();
    }

    /**
     * Creates a result builder for the supplied action.
     *
     * @param action the action represented by the result
     * @return a result builder
     */
    static Builder builder(Action action) {
        return new Builder(action);
    }

    /**
     * Returns the parent result, if this result belongs to a nested action.
     *
     * @return the parent result, or an empty {@link Optional} when this is the root result
     */
    Optional<Result> getParent();

    /**
     * Returns the child results produced under this result.
     *
     * <p>For container actions, children are in declaration order. For parallel actions, children are in completion
     * order. The returned list is immutable.
     *
     * @return the child results
     */
    List<Result> getChildren();

    /**
     * Returns the action associated with this result.
     *
     * @return the executed action
     */
    Action getAction();

    /**
     * Returns the final status for this result.
     *
     * @return the execution status
     */
    Status getStatus();

    /**
     * Returns the run duration for this action.
     *
     * <p>This is the wall-clock time of the full run or skip.
     *
     * @return the run duration for the action represented by this result
     */
    Duration getRunDuration();

    /**
     * Builds a {@link Result} with explicit status, duration, and children.
     */
    final class Builder {

        private final DefaultResult result;
        private boolean built;

        private Builder(Action action) {
            result = new DefaultResult(Objects.requireNonNull(action, "action must not be null"));
        }

        /**
         * Sets the result status.
         *
         * @param status the run status to record for this result
         * @return this builder
         * @throws NullPointerException if {@code status} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder status(Status status) {
            ensureNotBuilt();
            result.setStatus(Objects.requireNonNull(status, "status must not be null"));
            return this;
        }

        /**
         * Sets the result run duration.
         *
         * @param runDuration the wall-clock duration of the action run
         * @return this builder
         * @throws NullPointerException if {@code runDuration} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder runDuration(Duration runDuration) {
            ensureNotBuilt();
            result.setRunDuration(Objects.requireNonNull(runDuration, "runDuration must not be null"));
            return this;
        }

        /**
         * Adds a child result.
         *
         * @param child the child result produced by a nested action run
         * @return this builder
         * @throws NullPointerException if {@code child} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder child(Result child) {
            ensureNotBuilt();
            result.addChild(Objects.requireNonNull(child, "child must not be null"));
            return this;
        }

        /**
         * Builds the result.
         *
         * @return the result
         * @throws IllegalStateException if this builder has already been built
         */
        public Result build() {
            ensureNotBuilt();
            built = true;
            return result;
        }

        private void ensureNotBuilt() {
            if (built) {
                throw new IllegalStateException("builder already built");
            }
        }
    }
}

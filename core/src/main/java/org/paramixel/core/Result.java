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
 * Describes the outcome of executing an {@link Action}.
 *
 * <p>A {@code Result} records the action that was executed, its final {@link Status}, run duration, and any nested
 * child results produced by composed actions.
 */
public interface Result {

    /**
     * Creates a staged result for the supplied action.
     *
     * @param action the action represented by the result
     * @return a staged result
     */
    static Result staged(Action action) {
        return builder(action).status(Status.staged()).build();
    }

    /**
     * Creates a passing result for the supplied action.
     *
     * @param action the action represented by the result
     * @return a passing result
     */
    static Result pass(Action action) {
        return builder(action).status(Status.pass()).build();
    }

    /**
     * Creates a skipped result for the supplied action.
     *
     * @param action the action represented by the result
     * @return a skipped result
     */
    static Result skip(Action action) {
        return builder(action).status(Status.skip()).build();
    }

    /**
     * Creates a failed result for the supplied action and throwable.
     *
     * @param action the action represented by the result
     * @param throwable the failure throwable
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
     * @return the child results in implementation-defined order
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
     * <p>This is the wall-clock time of the full execute or skip.
     *
     * @return the run duration for the action represented by this result
     */
    Duration getRunDuration();

    /**
     * Builder for simple public {@link Result} construction.
     */
    final class Builder {

        private final DefaultResult result;
        private boolean built;

        private Builder(Action action) {
            result = new DefaultResult(action);
        }

        /**
         * Sets the result status.
         *
         * @param status the status
         * @return this builder
         */
        public Builder status(Status status) {
            ensureNotBuilt();
            result.setStatus(Objects.requireNonNull(status, "status must not be null"));
            return this;
        }

        /**
         * Sets the result run duration.
         *
         * @param runDuration the run duration
         * @return this builder
         */
        public Builder runDuration(Duration runDuration) {
            ensureNotBuilt();
            result.setRunDuration(Objects.requireNonNull(runDuration, "runDuration must not be null"));
            return this;
        }

        /**
         * Adds a child result.
         *
         * @param child the child result
         * @return this builder
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

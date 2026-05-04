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

/**
 * Describes the outcome of executing an {@link Action}.
 *
 * <p>A {@code Result} records the action that was executed, its final {@link Status}, elapsed time information, and
 * any nested child results produced by composed actions.
 */
public interface Result {

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
     * Returns the elapsed time recorded for this action.
     *
     * @return the elapsed time for the action represented by this result
     */
    Duration getElapsedTime();

    /**
     * Returns the cumulative elapsed time for descendant execution.
     *
     * <p>For leaf results this is typically the same as {@link #getElapsedTime()}. For composed results, the value may
     * represent the aggregate of child execution times.
     *
     * @return the cumulative elapsed time represented by this result
     */
    Duration getCumulativeElapsedTime();
}

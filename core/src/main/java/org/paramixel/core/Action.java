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

/**
 * Defines the fundamental run unit processed by a {@link Runner}, with identity and run behavior.
 *
 * <p>An {@link Action} is the fundamental run unit handled by a {@link Runner}. Actions may be composed into
 * parent-child hierarchies by implementations such as {@link CompositeAction} to model sequential, parallel, or
 * otherwise structured runs.
 *
 * <p>The action instance itself defines run behavior, while {@link Result} captures the outcome of a specific
 * run.
 *
 * <p>Implementations should return a stable identifier from {@link #getId()} for the lifetime of the action
 *     instance.
 */
public interface Action {

    /**
     * Returns the identifier for this action.
     *
     * <p>Identifiers are typically used for reporting, selection, and correlating an {@link Action} with its
     * corresponding {@link Result}.
     *
     * @return the stable identifier used to correlate this action with its {@link Result} in reports and selection
     */
    String getId();

    /**
     * Returns the human-readable display name used in console output and reports.
     *
     * @return the human-readable name used in output and reporting
     */
    String getName();

    /**
     * Runs this action using the supplied run context.
     *
     * <p>The returned {@link Result} describes the outcome for this action run and may include child results for
     * composed actions.
     *
     * @param context the run context for this invocation
     * @return the run result for this action
     */
    Result run(Context context);

    /**
     * Produces a result representing a skipped run of this action.
     *
     * <p>This method is used when an action is intentionally not run but still needs a reported outcome.
     *
     * @param context the run context associated with the skipped action
     * @return the result representing the skipped outcome
     */
    Result skip(Context context);
}

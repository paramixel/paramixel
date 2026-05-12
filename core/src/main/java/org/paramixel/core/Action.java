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
 * Represents a Paramixel action.
 *
 * <p>An {@link Action} is the fundamental execution unit handled by a {@link Runner}. Actions may be composed into
 * parent-child hierarchies by implementations such as {@link CompositeAction} to model sequential, parallel, or
 * otherwise structured execution.
 *
 * <p>The action instance itself defines execution behavior, while {@link Result} captures the outcome of a specific
 * execution.
 *
 * @implSpec Implementations should return a stable identifier from {@link #getId()} for the lifetime of the action
 *     instance.
 */
public interface Action {

    /**
     * Controls how an action scopes the {@link Context} it receives from its parent.
     */
    enum ContextMode {

        /**
         * Execute with a fresh child context whose parent is the received context.
         */
        ISOLATED,

        /**
         * Execute with the same context instance received from the parent action.
         */
        SHARED
    }

    /**
     * Returns the identifier for this action.
     *
     * <p>Identifiers are typically used for reporting, selection, and correlating an {@link Action} with its
     * corresponding {@link Result}.
     *
     * @return the action identifier
     */
    String getId();

    /**
     * Returns the human-readable display name used in console output and reports.
     *
     * @return the human-readable name used in output and reporting
     */
    String getName();

    /**
     * Returns this action's context scoping mode.
     *
     * @return the context mode used when this action executes or skips
     */
    ContextMode getContextMode();

    /**
     * Executes this action using the supplied execution context.
     *
     * <p>The returned {@link Result} describes the outcome for this action execution and may include child results for
     * composed actions.
     *
     * @param context the execution context for this invocation
     * @return the execution result for this action
     */
    Result execute(Context context);

    /**
     * Produces a result representing a skipped execution of this action.
     *
     * <p>This method is used when an action is intentionally not executed but still needs a reported outcome.
     *
     * @param context the execution context associated with the skipped action
     * @return the result representing the skipped outcome
     */
    Result skip(Context context);
}

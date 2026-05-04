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

import java.util.List;
import java.util.Optional;

/**
 * Represents a node in a Paramixel action tree.
 *
 * <p>An {@code Action} is the fundamental execution unit handled by a {@link Runner}. Actions may be composed into
 * parent-child hierarchies to model sequential, parallel, or otherwise structured execution.
 *
 * <p>The action instance itself defines execution behavior, while {@link Result} captures the outcome of a specific
 * execution.
 *
 * @implSpec Implementations should return a stable identifier from {@link #getId()} for the lifetime of the action
 *     instance and should keep parent-child relationships internally consistent when {@link #setParent(Action)} and
 *     {@link #addChild(Action)} are used.
 */
public interface Action {

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
     * Returns the display name for this action.
     *
     * @return the human-readable name used in output and reporting
     */
    String getName();

    /**
     * Returns the parent action, if this action is attached to another action.
     *
     * @return the parent action, or an empty {@link Optional} when this action is a root action
     */
    Optional<Action> getParent();

    /**
     * Sets the parent action for this action.
     *
     * @param parent the new parent action
     */
    void setParent(Action parent);

    /**
     * Adds a child action to this action.
     *
     * @param child the child action to add
     */
    void addChild(Action child);

    /**
     * Returns the child actions owned by this action.
     *
     * @return the child actions in the order maintained by the implementation
     */
    List<Action> getChildren();

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

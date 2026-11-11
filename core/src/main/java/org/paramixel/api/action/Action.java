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

package org.paramixel.api.action;

import org.paramixel.api.Context;

/**
 * Defines a reusable execution unit processed by the Paramixel scheduler.
 *
 * <p>Actions are reusable definitions. Discovery binds an action occurrence to a descriptor,
 * and execution uses a {@link Context} for action-facing runtime services such as
 * configuration and fixture instances.
 *
 * <p>The scheduler owns all traversal, status aggregation, and listener callbacks.
 *
 * <p>For actions with mutable configuration, use the corresponding {@link ActionBuilder} to
 * construct an immutable action snapshot via {@link ActionBuilder#build()}.
 *
 * <h2>How a child's outcome affects its container</h2>
 *
 * <p>A child ends as {@code FAILED}, {@code SKIPPED} or {@code ABORTED} (or {@code PASSED}). What a container
 * does with the remaining work depends on the container and on the child's outcome:
 *
 * <table>
 *   <caption>Effect of a child's outcome on the rest of its container</caption>
 *   <tr><th>Container</th><th>child {@code FAILED}</th><th>child {@code SKIPPED}</th><th>child {@code ABORTED}</th></tr>
 *   <tr><td>{@link Sequential} (dependent, the default)</td><td>remaining children {@code SKIPPED}</td><td>remaining children {@code SKIPPED}</td><td>remaining children still run</td></tr>
 *   <tr><td>{@link Sequential} (independent)</td><td>all run</td><td>all run</td><td>all run</td></tr>
 *   <tr><td>{@link Parallel}</td><td>all run</td><td>all run</td><td>all run</td></tr>
 *   <tr><td>{@link Loop}</td><td>next iteration runs</td><td>next iteration runs</td><td>remaining iterations {@code SKIPPED}, the loop is {@code ABORTED}</td></tr>
 *   <tr><td>{@link Scope} ({@code before})</td><td>{@code body} {@code SKIPPED}, {@code after} runs</td><td>{@code body} {@code SKIPPED}, {@code after} runs</td><td>{@code body} {@code SKIPPED}, {@code after} runs</td></tr>
 * </table>
 *
 * <p>A container's own status combines its children as {@code FAILED} over {@code ABORTED} over still-running over
 * {@code SKIPPED} over {@code PASSED}. These rules are pinned by {@code ContainerOutcomeMatrixTest}.
 */
public sealed interface Action extends ActionBuilder
        permits Conditional, Isolated, Loop, Parallel, Scope, Sequential, Step, Timeout {

    /**
     * Returns the display name used in console output and reports.
     *
     * @return the action display name
     */
    String displayName();

    /**
     * Returns this action. An action is its own built form, which lets an {@link Action} be used wherever an
     * {@link ActionBuilder} is accepted.
     *
     * @return this action; never {@code null}
     */
    @Override
    default Action build() {
        return this;
    }
}

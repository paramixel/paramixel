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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the runtime state available while an {@link Action} executes.
 */
public interface Context {

    /**
     * Returns the parent context for nested execution.
     *
     * @return An {@link Optional} containing the parent context, or empty for
     *     the root context.
     */
    Optional<Context> parent();

    /**
     * Returns the action associated with this context.
     *
     * @return The current action.
     */
    Action action();

    /**
     * Sets an attachment on this context, replacing any existing attachment.
     *
     * @param <T> The attachment type.
     * @param attachment The attachment to set; may be null.
     * @return This context for method chaining.
     */
    <T> Context setAttachment(T attachment);

    /**
     * Returns the attachment cast to the requested type.
     *
     * @param <T> The requested type.
     * @param type The class token for the requested type; must not be null.
     * @return An {@link Optional} containing the attachment if present and
     *     assignable to {@code type}, or empty otherwise.
     */
    <T> Optional<T> attachment(Class<T> type);

    /**
     * Removes and returns the current attachment.
     *
     * @return An {@link Optional} containing the removed attachment, or empty if
     *     no attachment was present.
     */
    Optional<Object> removeAttachment();

    /**
     * Creates a child context for executing a nested action.
     *
     * @param child The child action; must not be null.
     * @return A new child context.
     */
    Context createChild(Action child);

    /**
     * Executes an action synchronously as a child of this context.
     *
     * @param child The action to execute; must not be null.
     * @return The execution result.
     */
    Result execute(Action child);

    /**
     * Executes an action asynchronously as a child of this context.
     *
     * @param child The action to execute; must not be null.
     * @return A future that will complete with the execution result.
     */
    CompletableFuture<Result> executeAsync(Action child);

    /**
     * Notifies the listener that an action is about to start.
     *
     * @param context The active context; must not be null.
     * @param action The action about to execute; must not be null.
     */
    void beforeAction(Context context, Action action);

    /**
     * Notifies the listener that an action has finished.
     *
     * @param context The active context; must not be null.
     * @param action The completed action; must not be null.
     * @param result The execution result; must not be null.
     */
    void afterAction(Context context, Action action, Result result);
}

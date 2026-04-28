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
 * Represents a named unit of executable work in an action tree.
 *
 * <p>An action has a stable identifier, a display name, an optional parent,
 * and zero or more children. Implementations define how the action executes.
 */
public interface Action {

    /**
     * Returns this action's unique identifier.
     *
     * @return The generated action identifier.
     */
    String id();

    /**
     * Returns this action's display name.
     *
     * @return The non-blank action name.
     */
    String name();

    /**
     * Returns this action's parent when it has been adopted by another action.
     *
     * @return An {@link Optional} containing the parent action, or empty when
     *     this action is a root.
     */
    default Optional<Action> parent() {
        return Optional.empty();
    }

    /**
     * Returns this action's child actions.
     *
     * @return The child actions in execution order; never null.
     */
    default List<Action> children() {
        return List.of();
    }

    /**
     * Executes this action in the supplied execution context.
     *
     * @param context The active execution context; must not be null.
     * @return The execution result.
     */
    Result execute(Context context);

    /**
     * Skips this action without executing it.
     *
     * @param context The active execution context; must not be null.
     * @return A skipped result.
     */
    Result skip(Context context);
}

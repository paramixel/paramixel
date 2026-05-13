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

package org.paramixel.core.internal;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.paramixel.core.Action;
import org.paramixel.core.CompositeAction;

/**
 * Internal action hierarchy index for components that need path information during execution.
 *
 * <p>This class uses static mutable state and is not safe for concurrent access across runner instances. Only one
 * runner should be active at a time; concurrent runs would corrupt the hierarchy index and produce incorrect path
 * resolution in reporting output.
 */
public final class ActionHierarchy {

    private static final Object LOCK = new Object();
    private static Map<Action, List<Action>> paths = Map.of();

    private ActionHierarchy() {
        // Intentionally empty
    }

    /**
     * Installs an action hierarchy index for the supplied root action.
     *
     * @param root the root action
     * @return a scope that clears the installed index when closed
     */
    public static Scope install(Action root) {
        Objects.requireNonNull(root, "root must not be null");
        var indexedPaths = new IdentityHashMap<Action, List<Action>>();
        index(root, new ArrayDeque<>(), indexedPaths);
        synchronized (LOCK) {
            paths = indexedPaths;
        }
        return new Scope();
    }

    /**
     * Returns the root-to-action path for an action when an index is installed.
     *
     * @param action the action
     * @return the root-to-action path, or an empty {@link Optional} when no hierarchy is installed or the action is not in the installed index
     */
    public static Optional<List<Action>> pathOf(Action action) {
        Objects.requireNonNull(action, "action must not be null");
        synchronized (LOCK) {
            return Optional.ofNullable(paths.get(action));
        }
    }

    private static void index(Action action, Deque<Action> path, Map<Action, List<Action>> indexedPaths) {
        path.addLast(action);
        indexedPaths.putIfAbsent(action, List.copyOf(path));
        if (action instanceof CompositeAction compositeAction) {
            for (Action child : compositeAction.getChildren()) {
                index(
                        Objects.requireNonNull(child, () -> "action '" + action.getName() + "' returned a null child"),
                        path,
                        indexedPaths);
            }
        }
        path.removeLast();
    }

    /**
     * An auto-closeable scope that installs and removes the action hierarchy index. Closing this scope clears the
     * installed index.
     */
    public static final class Scope implements AutoCloseable {

        private Scope() {
            // Intentionally empty
        }

        /**
         * Removes the installed action hierarchy index.
         */
        @Override
        public void close() {
            synchronized (LOCK) {
                paths = Collections.emptyMap();
            }
        }
    }
}

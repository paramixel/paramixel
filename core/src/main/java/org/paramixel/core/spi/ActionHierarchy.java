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

package org.paramixel.core.spi;

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
 */
public final class ActionHierarchy {

    private static final Object LOCK = new Object();
    private static Map<Action, List<Action>> paths = Map.of();

    private ActionHierarchy() {}

    /**
     * Installs an action hierarchy index for the supplied root action.
     *
     * @param root the root action
     * @return a scope that clears the installed index when closed
     */
    public static Scope install(Action root) {
        Objects.requireNonNull(root, "root must not be null");
        Map<Action, List<Action>> indexedPaths = new IdentityHashMap<>();
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
     * @return the indexed path, or empty when the action is not known
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
     * Installed action hierarchy scope.
     */
    public static final class Scope implements AutoCloseable {

        private Scope() {}

        @Override
        public void close() {
            synchronized (LOCK) {
                paths = Collections.emptyMap();
            }
        }
    }
}

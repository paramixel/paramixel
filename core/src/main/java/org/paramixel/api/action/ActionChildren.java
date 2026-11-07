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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates and snapshots the child list of a composite action.
 *
 * <p>Composite actions reject a {@code null} list and {@code null} elements, then copy the
 * children so later builder mutation cannot change an already-built action.
 */
final class ActionChildren {

    private ActionChildren() {
        // Intentionally empty
    }

    /**
     * Returns an immutable snapshot of the supplied children after rejecting null entries.
     *
     * @param children the child actions; must not be {@code null}
     * @return an immutable copy of the children in order
     * @throws NullPointerException if {@code children} or any element is {@code null}
     */
    static List<Action> validateChildren(final List<Action> children) {
        Objects.requireNonNull(children, "children is null");
        var validated = new ArrayList<Action>(children.size());
        for (Action child : children) {
            Objects.requireNonNull(child, "children contains null element");
            validated.add(child);
        }
        return List.copyOf(validated);
    }
}

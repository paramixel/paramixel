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
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.paramixel.core.Action;
import org.paramixel.core.exception.CycleDetectedException;

/**
 * Detects cycles in an {@link Action} graph.
 *
 * <p>This internal validator walks parent-child relationships by identity and rejects graphs that revisit an action
 * while it is still on the active traversal path.
 */
public final class CycleLoopDetector {

    private final Map<Action, VisitState> visitStates = new IdentityHashMap<>();
    private final Deque<Action> path = new ArrayDeque<>();

    /**
     * Validates that the supplied root action and all descendants form an acyclic graph.
     *
     * @param root the root action to validate
     * @throws NullPointerException if {@code root} is {@code null}
     * @throws CycleDetectedException if a cycle is detected
     */
    public void validateNoCycles(Action root) {
        Objects.requireNonNull(root, "root must not be null");
        visit(root);
    }

    private void visit(Action action) {
        VisitState visitState = visitStates.get(action);
        if (visitState == VisitState.VISITED) {
            return;
        }
        if (visitState == VisitState.VISITING) {
            throw CycleDetectedException.of(buildCycleMessage(action));
        }

        visitStates.put(action, VisitState.VISITING);
        path.addLast(action);

        try {
            for (Action child : action.getChildren()) {
                visit(Objects.requireNonNull(child, () -> "action '" + action.getName() + "' returned a null child"));
            }
        } finally {
            path.removeLast();
        }

        visitStates.put(action, VisitState.VISITED);
    }

    private String buildCycleMessage(Action repeatedAction) {
        List<Action> cycle = new ArrayList<>();
        boolean collecting = false;
        for (Action action : path) {
            if (action == repeatedAction) {
                collecting = true;
            }
            if (collecting) {
                cycle.add(action);
            }
        }
        cycle.add(repeatedAction);

        return "Cycle detected in action graph: "
                + cycle.stream()
                        .map(action -> action.getName() + "[" + action.getId() + "]")
                        .collect(Collectors.joining(" -> "));
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}

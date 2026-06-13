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

package nonapi.org.paramixel.action;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.stream.Collectors;
import org.paramixel.api.action.Action;
import org.paramixel.api.exception.CycleDetectedException;

/**
 * Binds reusable action definitions into a per-run descriptor tree using
 * a pull-based stack walk with cycle detection.
 *
 * <p>Structurally mirrors the action tree: before, child actions, and after
 * are assigned to separate descriptor slots rather than flattened into the
 * children list.
 */
@SuppressWarnings("removal")
public final class DescriptorBuilder {

    /**
     * Creates a descriptor builder.
     */
    public DescriptorBuilder() {}

    /**
     * Builds a descriptor tree rooted at the supplied action.
     *
     * @param root the root action; must not be {@code null}
     * @return the root descriptor
     */
    public MutableDescriptor discover(final Action root) {
        Objects.requireNonNull(root, "root is null");
        var rootDescriptor = new ConcreteDescriptor(null, root);
        buildDescendants(rootDescriptor, root, new ArrayDeque<>(), new IdentityHashMap<>());
        return rootDescriptor;
    }

    private void buildDescendants(
            final MutableDescriptor descriptor,
            final Action action,
            final Deque<Action> path,
            final IdentityHashMap<Action, Boolean> visited) {
        path.addLast(action);
        visited.put(action, Boolean.TRUE);
        try {
            DescriptorExpansions.expand(
                    action,
                    new DescriptorExpansionContext(descriptor, child -> build(descriptor, child, path, visited)));
        } finally {
            path.removeLast();
            visited.remove(action);
        }
    }

    private MutableDescriptor build(
            final MutableDescriptor parent,
            final Action action,
            final Deque<Action> path,
            final IdentityHashMap<Action, Boolean> visited) {
        if (visited.containsKey(action)) {
            throw new CycleDetectedException(buildCycleMessage(path, action));
        }

        var descriptor = new ConcreteDescriptor(parent, action);
        buildDescendants(descriptor, action, path, visited);
        return descriptor;
    }

    private static String buildCycleMessage(final Deque<Action> path, final Action repeatedAction) {
        var cycle = new ArrayDeque<Action>();
        var collecting = false;
        for (Action action : path) {
            if (action == repeatedAction) {
                collecting = true;
            }
            if (collecting) {
                cycle.addLast(action);
            }
        }
        cycle.addLast(repeatedAction);
        return "Cycle detected in action tree: "
                + cycle.stream()
                        .map(action ->
                                action.displayName() + "[" + action.getClass().getName() + "]")
                        .collect(Collectors.joining(" -> "));
    }
}

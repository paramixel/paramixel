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
import org.paramixel.api.action.Assert;
import org.paramixel.api.action.Conditional;
import org.paramixel.api.action.Delay;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Repeat;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Static;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;
import org.paramixel.api.action.Until;
import org.paramixel.api.exception.CycleDetectedException;

/**
 * Binds reusable action definitions into a per-run descriptor tree using
 * a pull-based stack walk with cycle detection.
 *
 * <p>Structurally mirrors the action tree: before, child actions, and after
 * are assigned to separate descriptor slots rather than flattened into the
 * children list.
 *
 * <p>This class owns the single authoritative mapping from action shape to descriptor shape. The
 * execution plan derives child order and execution policy from the resulting tree.
 */
@SuppressWarnings("removal")
public final class DescriptorBuilder {

    /**
     * Binds an action to a new descriptor attached to a specific parent.
     */
    @FunctionalInterface
    private interface ChildBinder {

        /**
         * Builds the descriptor for one action.
         *
         * @param action the action to bind; must not be {@code null}
         * @return the bound descriptor; never {@code null}
         */
        MutableDescriptor bind(Action action);
    }

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
            expand(descriptor, action, child -> build(descriptor, child, path, visited));
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

    private static void expand(final MutableDescriptor descriptor, final Action action, final ChildBinder binder) {
        if (action instanceof Scope scope) {
            scope.before().ifPresent(before -> descriptor.setBefore(binder.bind(before)));
            if (scope.body() != null) {
                descriptor.addChild(binder.bind(scope.body()));
            }
            scope.after().ifPresent(after -> descriptor.setAfter(binder.bind(after)));
            return;
        }
        if (action instanceof Static staticAction) {
            staticAction.before().ifPresent(before -> descriptor.setBefore(binder.bind(before)));
            if (staticAction.body() != null) {
                descriptor.addChild(binder.bind(staticAction.body()));
            }
            staticAction.after().ifPresent(after -> descriptor.setAfter(binder.bind(after)));
            return;
        }
        if (action instanceof Instance instance) {
            descriptor.setBefore(binder.bind(instance.instantiate()));
            if (instance.body() != null) {
                descriptor.addChild(binder.bind(instance.body()));
            }
            descriptor.setAfter(binder.bind(instance.destroy()));
            return;
        }
        if (action instanceof Parallel parallel) {
            parallel.children().forEach(child -> descriptor.addChild(binder.bind(child)));
            return;
        }
        if (action instanceof Sequence sequence) {
            sequence.children().forEach(child -> descriptor.addChild(binder.bind(child)));
            return;
        }
        if (action instanceof Sequential sequential) {
            sequential.children().forEach(child -> descriptor.addChild(binder.bind(child)));
            return;
        }
        if (action instanceof Loop loop) {
            for (var i = 0; i < loop.maxIterations(); i++) {
                descriptor.addChild(binder.bind(loop.body()));
            }
            return;
        }
        if (action instanceof Repeat repeat) {
            for (var i = 0; i < repeat.iterations(); i++) {
                descriptor.addChild(binder.bind(repeat.body()));
            }
            return;
        }
        if (action instanceof Until until) {
            for (var i = 0; i < until.maxIterations(); i++) {
                descriptor.addChild(binder.bind(until.body()));
            }
            return;
        }
        if (action instanceof Timeout timeout) {
            descriptor.addChild(binder.bind(timeout.body()));
            return;
        }
        if (action instanceof Isolated isolated) {
            descriptor.addChild(binder.bind(isolated.body()));
            return;
        }
        if (action instanceof Conditional conditional) {
            descriptor.addChild(binder.bind(conditional.body()));
            return;
        }
        if (action instanceof Step || action instanceof Assert || action instanceof Delay) {
            return;
        }
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

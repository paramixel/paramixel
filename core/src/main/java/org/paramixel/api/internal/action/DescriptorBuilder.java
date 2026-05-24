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

package org.paramixel.api.internal.action;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.stream.Collectors;
import org.paramixel.api.Configuration;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Delay;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Repeat;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Static;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;
import org.paramixel.api.exception.CycleDetectedException;

/**
 * Binds reusable action definitions into a per-run descriptor tree using
 * a pull-based stack walk with {@code instanceof} pattern matching.
 */
public final class DescriptorBuilder {

    private final Configuration configuration;

    /**
     * Creates a descriptor builder.
     *
     * @param configuration the run configuration; must not be {@code null}
     */
    public DescriptorBuilder(final Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    /**
     * Builds a descriptor tree rooted at the supplied action.
     *
     * @param root the root action; must not be {@code null}
     * @return the root descriptor
     */
    public MutableDescriptor discover(final Action<?> root) {
        Objects.requireNonNull(root, "root must not be null");
        var rootDescriptor = new ConcreteRootDescriptor(root);
        return build(rootDescriptor, root, new ArrayDeque<>());
    }

    private MutableDescriptor build(
            final MutableDescriptor parent, final Action<?> action, final Deque<Action<?>> path) {
        if (containsIdentity(path, action)) {
            throw new CycleDetectedException(buildCycleMessage(path, action));
        }

        var descriptor = createDescriptor(parent, action);
        parent.addChild(descriptor);

        path.addLast(action);
        action.before().ifPresent(b -> build(descriptor, b, path));
        action.children().forEach(c -> build(descriptor, c, path));
        action.after().ifPresent(a -> build(descriptor, a, path));
        path.removeLast();
        return descriptor;
    }

    private MutableDescriptor createDescriptor(final MutableDescriptor parent, final Action<?> action) {
        if (action instanceof Step<?> s) {
            return new ConcreteStepDescriptor(parent, s);
        }
        if (action instanceof Delay d) {
            return new ConcreteDelayDescriptor(parent, d);
        }
        if (action instanceof Sequential<?> s) {
            return new ConcreteSequentialDescriptor(parent, s);
        }
        if (action instanceof Parallel<?> p) {
            return new ConcreteParallelDescriptor(parent, p);
        }
        if (action instanceof Lifecycle<?> l) {
            return new ConcreteLifecycleDescriptor(parent, l);
        }
        if (action instanceof Static s) {
            return new ConcreteStaticDescriptor(parent, s);
        }
        if (action instanceof Instance<?> i) {
            return new ConcreteInstanceDescriptor(parent, i);
        }
        if (action instanceof Repeat<?> r) {
            return new ConcreteRepeatDescriptor(parent, r);
        }
        if (action instanceof Timeout t) {
            return new ConcreteTimeoutDescriptor(parent, t);
        }
        return new ConcreteDescriptor(parent, action);
    }

    private static boolean containsIdentity(final Deque<Action<?>> path, final Action<?> action) {
        for (Action<?> current : path) {
            if (current == action) {
                return true;
            }
        }
        return false;
    }

    private static String buildCycleMessage(final Deque<Action<?>> path, final Action<?> repeatedAction) {
        var cycle = new ArrayDeque<Action<?>>();
        var collecting = false;
        for (Action<?> action : path) {
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
                        .map(action -> action.name() + "[" + action.getClass().getName() + "]")
                        .collect(Collectors.joining(" -> "));
    }
}

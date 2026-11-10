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

package nonapi.org.paramixel;

import java.util.Objects;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Assert;
import org.paramixel.api.action.Conditional;
import org.paramixel.api.action.Delay;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Repeat;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;
import org.paramixel.api.action.Until;

/**
 * Immutable, per-run compiled form of a discovered action tree.
 *
 * <p>The plan is the single source of truth for execution policy: node kind, child shape, resolved
 * fan-out limits, timeout deadlines, and isolation lock names. It is built once, before execution
 * starts, and never changes afterwards.
 *
 * <p>Structure is snapshotted into the plan so the engine never consults a mutable descriptor graph
 * to decide what to run. Descriptors are referenced for identity, status, and listener reporting
 * only.
 *
 * <p>Compilation validates what can be decided statically and fails before any user code runs.
 */
final class ExecutionPlan {

    /**
     * Execution policy for one plan node.
     */
    enum Kind {
        /** Runs user code directly: {@code Step}, {@code Assert}, and {@code Delay}. */
        TERMINAL,
        /** Runs children in order, switching to {@code SKIP} after a failure when dependent. */
        SEQUENTIAL,
        /** Runs children concurrently with bounded admission. */
        PARALLEL,
        /** Runs an optional before child, body children, and an optional after child. */
        LIFECYCLE,
        /** Runs iterations in order with an optional predicate and inter-iteration delay. */
        LOOP,
        /** Runs a fixed number of body repetitions in order. */
        REPEAT,
        /** Runs iterations in order until a predicate is satisfied. */
        UNTIL,
        /** Runs one child under a deadline. */
        TIMEOUT,
        /** Runs one child while holding a named lock. */
        ISOLATED,
        /** Runs one child only when a predicate holds. */
        CONDITIONAL
    }

    /**
     * One immutable plan node.
     */
    static final class Node {

        final MutableDescriptor descriptor;
        final Kind kind;
        final Action control;
        final boolean dependent;
        final int cap;
        final long timeoutNanos;
        final String lockName;
        final Node before;
        final Node[] children;
        final Node after;

        Node(
                final MutableDescriptor descriptor,
                final Kind kind,
                final Action control,
                final boolean dependent,
                final int cap,
                final long timeoutNanos,
                final String lockName,
                final Node before,
                final Node[] children,
                final Node after) {
            this.descriptor = descriptor;
            this.kind = kind;
            this.control = control;
            this.dependent = dependent;
            this.cap = cap;
            this.timeoutNanos = timeoutNanos;
            this.lockName = lockName;
            this.before = before;
            this.children = children;
            this.after = after;
        }
    }

    private final Node root;

    private ExecutionPlan(final Node root) {
        this.root = root;
    }

    /**
     * Compiles the frozen descriptor tree into an execution plan.
     *
     * @param root the root descriptor; must not be {@code null}
     * @return the compiled plan
     * @throws NullPointerException if {@code root} is {@code null}
     * @throws IllegalStateException if the tree is structurally invalid
     */
    static ExecutionPlan compile(final MutableDescriptor root) {
        Objects.requireNonNull(root, "root is null");
        if (!root.isFrozen()) {
            root.freeze();
        }
        return new ExecutionPlan(compileNode(root));
    }

    Node root() {
        return root;
    }

    int nodeCount() {
        return count(root);
    }

    private static int count(final Node node) {
        if (node == null) {
            return 0;
        }
        var total = 1;
        if (node.before != null) {
            total += count(node.before);
        }
        for (var child : node.children) {
            total += count(child);
        }
        if (node.after != null) {
            total += count(node.after);
        }
        return total;
    }

    private static Node compileNode(final MutableDescriptor descriptor) {
        var action = descriptor.action();
        var kind = kindOf(action);
        var dependent = dependentOf(action);
        var cap = capOf(action);
        var timeoutNanos = timeoutNanosOf(action);
        var lockName = lockNameOf(action);

        var before = descriptor.before().orElse(null);
        var after = descriptor.after().orElse(null);
        var children = descriptor.children();

        requireSingleChild(descriptor, kind, children.size());

        var beforeNode = before == null ? null : compileNode(asMutable(before, descriptor));
        var childNodes = new Node[children.size()];
        for (var i = 0; i < children.size(); i++) {
            childNodes[i] = compileNode(asMutable(children.get(i), descriptor));
        }
        var afterNode = after == null ? null : compileNode(asMutable(after, descriptor));

        return new Node(
                descriptor, kind, action, dependent, cap, timeoutNanos, lockName, beforeNode, childNodes, afterNode);
    }

    private static MutableDescriptor asMutable(final Object descriptor, final MutableDescriptor parent) {
        if (descriptor instanceof MutableDescriptor mutable) {
            return mutable;
        }
        throw new IllegalStateException("child of '" + parent.id() + "' is not a mutable descriptor");
    }

    private static void requireSingleChild(final MutableDescriptor descriptor, final Kind kind, final int childCount) {
        if (kind != Kind.TIMEOUT && kind != Kind.ISOLATED && kind != Kind.CONDITIONAL) {
            return;
        }
        if (childCount != 1) {
            throw new IllegalStateException(
                    kind + " action '" + descriptor.id() + "' requires exactly one child but has " + childCount);
        }
    }

    private static Kind kindOf(final Action action) {
        if (action instanceof Step || action instanceof Assert || action instanceof Delay) {
            return Kind.TERMINAL;
        }
        if (action instanceof Parallel) {
            return Kind.PARALLEL;
        }
        if (action instanceof Sequential || action instanceof Sequence) {
            return Kind.SEQUENTIAL;
        }
        if (action instanceof Loop) {
            return Kind.LOOP;
        }
        if (action instanceof Repeat) {
            return Kind.REPEAT;
        }
        if (action instanceof Until) {
            return Kind.UNTIL;
        }
        if (action instanceof Timeout) {
            return Kind.TIMEOUT;
        }
        if (action instanceof Isolated) {
            return Kind.ISOLATED;
        }
        if (action instanceof Conditional) {
            return Kind.CONDITIONAL;
        }
        return Kind.LIFECYCLE;
    }

    private static boolean dependentOf(final Action action) {
        if (action instanceof Sequential sequential) {
            return sequential.isDependent();
        }
        if (action instanceof Sequence sequence) {
            return sequence.isDependent();
        }
        return false;
    }

    private static int capOf(final Action action) {
        return action instanceof Parallel parallel ? parallel.parallelism() : Integer.MAX_VALUE;
    }

    private static long timeoutNanosOf(final Action action) {
        if (!(action instanceof Timeout timeout)) {
            return -1L;
        }
        try {
            return timeout.timeout().toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static String lockNameOf(final Action action) {
        return action instanceof Isolated isolated ? isolated.lockName() : null;
    }
}

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.FixtureConsumer;
import nonapi.org.paramixel.action.FixtureCreator;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.action.StepAccessors;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Conditional;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;

/**
 * The compiled form of one run: the descriptor tree and the execution plan, produced together in one pass.
 *
 * <p>Compilation walks the {@link Action} tree once. A single dispatch maps each action type to its descriptor
 * shape and its execution policy; reaching an action type without a branch throws, so teaching the framework a new
 * action type without teaching the compiler about it fails loudly instead of silently producing a lifecycle node.
 *
 * <p>Descriptors are built bottom-up: children first, then their parent, then the parent links are attached. The
 * structure of every descriptor is therefore final, and no stage after compilation can reshape the tree.
 *
 * <p>The plan is the single source of truth for execution policy: node kind, child shape, fan-out pool, timeout
 * deadline and isolation lock name.
 */
final class CompiledPlan {

    /**
     * Execution policy for one plan node.
     */
    enum Kind {
        /** Runs user code directly: {@code Step}. */
        TERMINAL,
        /** Runs children in order, switching to {@code SKIP} after a failure when dependent. */
        SEQUENTIAL,
        /** Runs children concurrently with bounded admission. */
        PARALLEL,
        /** Runs an optional before child, body children, and an optional after child. */
        LIFECYCLE,
        /** Runs iterations in order with an optional predicate and inter-iteration delay. */
        LOOP,
        /** Runs one child under a deadline. */
        TIMEOUT,
        /** Runs one child while holding a named lock. */
        ISOLATED,
        /** Runs one child only when a predicate holds. */
        CONDITIONAL
    }

    /**
     * One node of the plan, paired with the descriptor it runs.
     */
    static final class Node {

        final ConcreteDescriptor descriptor;
        final Kind kind;
        final Action control;
        final boolean dependent;
        final String poolName;
        final boolean passThrough;
        final long timeoutNanos;
        final String lockName;
        final Node before;
        final Node[] children;
        final Node after;

        Node(
                final ConcreteDescriptor descriptor,
                final Kind kind,
                final Action control,
                final boolean dependent,
                final String poolName,
                final boolean passThrough,
                final long timeoutNanos,
                final String lockName,
                final Node before,
                final Node[] children,
                final Node after) {
            this.descriptor = descriptor;
            this.kind = kind;
            this.control = control;
            this.dependent = dependent;
            this.poolName = poolName;
            this.passThrough = passThrough;
            this.timeoutNanos = timeoutNanos;
            this.lockName = lockName;
            this.before = before;
            this.children = children;
            this.after = after;
        }
    }

    /**
     * A compiled subtree: its descriptor and its plan node.
     */
    private record Shape(ConcreteDescriptor descriptor, Node node) {}

    private final Node root;

    private CompiledPlan(final Node root) {
        this.root = Objects.requireNonNull(root, "root is null");
    }

    /**
     * Compiles the supplied action tree.
     *
     * <p>The pass also validates typed steps: a {@code step(name, T.class, ...)} needs an enclosing
     * {@code Instance} whose declared type is assignable to {@code T}, and one level may not declare two fixtures
     * of the same type. Both are rejected here, before any user code runs, with the offending step's path.
     *
     * @param action the root action; must not be {@code null}
     * @return the compiled plan; never {@code null}
     * @throws NullPointerException if {@code action} is {@code null}
     * @throws IllegalStateException if an action type has no compilation branch, or the tree's fixtures do not
     *     match its typed steps
     */
    static CompiledPlan compile(final Action action) {
        Objects.requireNonNull(action, "action is null");
        var root = compileShape(action, List.of(action.displayName()), List.of(), Set.of());
        return new CompiledPlan(root.node());
    }

    Node root() {
        return root;
    }

    int nodeCount() {
        return count(root);
    }

    /**
     * Returns the user pool names the plan uses, in first-use order.
     *
     * <p>The runner compares this with the configured pool keys to warn about a key that names a pool no
     * {@code Parallel} uses, which is the usual symptom of a typo in a properties file.
     *
     * @return the pool names; never {@code null}
     */
    Set<String> poolNames() {
        var names = new LinkedHashSet<String>();
        collectPoolNames(root, names);
        return Set.copyOf(names);
    }

    private static void collectPoolNames(final Node node, final Set<String> names) {
        if (node == null) {
            return;
        }
        if (node.kind == Kind.PARALLEL && !Pools.isRootPool(node.poolName)) {
            names.add(node.poolName);
        }
        collectPoolNames(node.before, names);
        for (var child : node.children) {
            collectPoolNames(child, names);
        }
        collectPoolNames(node.after, names);
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

    /**
     * The single dispatch over the action hierarchy.
     *
     * <p>Every permitted action type has exactly one branch, and the final throw makes an unhandled type fail loudly
     * instead of falling through to a lifecycle node.
     *
     * @param action the action to compile; must not be {@code null}
     * @param path the display path of the action, used in error messages
     * @param provided the fixture types provided by enclosing {@code Instance} nodes
     * @param activeLocks the {@code Isolated} lock names held on the path
     * @return the compiled subtree; never {@code null}
     */
    private static Shape compileShape(
            final Action action,
            final List<String> path,
            final List<Class<?>> provided,
            final Set<String> activeLocks) {
        if (action instanceof Step step) {
            requireFixture(step, path, provided);
            return terminal(action);
        }
        if (action instanceof Scope scope) {
            return lifecycle(scope, path, provided, activeLocks);
        }
        if (action instanceof Parallel parallel) {
            return container(
                    parallel,
                    Kind.PARALLEL,
                    parallel.children(),
                    false,
                    parallel.pool(),
                    null,
                    -1L,
                    path,
                    provided,
                    activeLocks);
        }
        if (action instanceof Sequential sequential) {
            return container(
                    sequential,
                    Kind.SEQUENTIAL,
                    sequential.children(),
                    sequential.isDependent(),
                    null,
                    null,
                    -1L,
                    path,
                    provided,
                    activeLocks);
        }
        if (action instanceof Loop loop) {
            var iterations = new ArrayList<Action>(loop.maxIterations());
            for (var i = 0; i < loop.maxIterations(); i++) {
                iterations.add(loop.body());
            }
            return container(loop, Kind.LOOP, iterations, false, null, null, -1L, path, provided, activeLocks);
        }
        if (action instanceof Timeout timeout) {
            return singleChild(
                    timeout, Kind.TIMEOUT, timeout.body(), timeoutNanosOf(timeout), true, path, provided, activeLocks);
        }
        if (action instanceof Isolated isolated) {
            var passThrough = activeLocks.contains(isolated.lockName());
            var inner = passThrough ? activeLocks : withLock(activeLocks, isolated.lockName());
            return singleChild(isolated, Kind.ISOLATED, isolated.body(), -1L, passThrough, path, provided, inner);
        }
        if (action instanceof Conditional conditional) {
            return singleChild(
                    conditional, Kind.CONDITIONAL, conditional.body(), -1L, false, path, provided, activeLocks);
        }
        throw new IllegalStateException(
                "No compilation branch for action type " + action.getClass().getName());
    }

    private static Shape terminal(final Action action) {
        var descriptor = new ConcreteDescriptor(action);
        return new Shape(
                descriptor,
                new Node(descriptor, Kind.TERMINAL, action, false, null, false, -1L, null, null, new Node[0], null));
    }

    private static Shape lifecycle(
            final Scope scope, final List<String> path, final List<Class<?>> provided, final Set<String> activeLocks) {
        var fixtureType = fixtureTypeOf(scope.before().orElse(null));
        final List<Class<?>> inner = fixtureType != null ? withFixture(provided, fixtureType) : provided;
        var before = scope.before()
                .map(child -> compileShape(child, pathOf(path, child), inner, activeLocks))
                .orElse(null);
        var body = compileShape(scope.body(), pathOf(path, scope.body()), inner, activeLocks);
        var after = scope.after()
                .map(child -> compileShape(child, pathOf(path, child), inner, activeLocks))
                .orElse(null);
        return shape(scope, Kind.LIFECYCLE, false, null, false, -1L, null, before, List.of(body), after);
    }

    private static Shape container(
            final Action action,
            final Kind kind,
            final List<Action> children,
            final boolean dependent,
            final String poolName,
            final String lockName,
            final long timeoutNanos,
            final List<String> path,
            final List<Class<?>> provided,
            final Set<String> activeLocks) {
        var compiled = new ArrayList<Shape>(children.size());
        for (var child : children) {
            compiled.add(compileShape(child, pathOf(path, child), provided, activeLocks));
        }
        if (kind == Kind.LOOP && compiled.isEmpty()) {
            throw new IllegalStateException("Loop action '" + action.displayName() + "' has no iterations");
        }
        return shape(action, kind, dependent, poolName, false, timeoutNanos, lockName, null, compiled, null);
    }

    private static Shape singleChild(
            final Action action,
            final Kind kind,
            final Action child,
            final long timeoutNanos,
            final boolean passThrough,
            final List<String> path,
            final List<Class<?>> provided,
            final Set<String> activeLocks) {
        return shape(
                action,
                kind,
                false,
                null,
                passThrough,
                timeoutNanos,
                kind == Kind.ISOLATED ? lockNameOf(action) : null,
                null,
                List.of(compileShape(child, pathOf(path, child), provided, activeLocks)),
                null);
    }

    private static Shape shape(
            final Action action,
            final Kind kind,
            final boolean dependent,
            final String poolName,
            final boolean passThrough,
            final long timeoutNanos,
            final String lockName,
            final Shape before,
            final List<Shape> children,
            final Shape after) {
        var descriptors = new ArrayList<MutableDescriptor>(children.size());
        for (var child : children) {
            descriptors.add(child.descriptor());
        }
        var descriptor = new ConcreteDescriptor(
                action,
                before == null ? null : before.descriptor(),
                descriptors,
                after == null ? null : after.descriptor());
        descriptor.attachSlots();
        var childNodes = new Node[children.size()];
        for (var i = 0; i < children.size(); i++) {
            childNodes[i] = children.get(i).node();
        }
        return new Shape(
                descriptor,
                new Node(
                        descriptor,
                        kind,
                        action,
                        dependent,
                        poolName,
                        passThrough,
                        timeoutNanos,
                        lockName,
                        before == null ? null : before.node(),
                        childNodes,
                        after == null ? null : after.node()));
    }

    private static List<String> pathOf(final List<String> path, final Action child) {
        var extended = new ArrayList<>(path);
        extended.add(child.displayName());
        return extended;
    }

    private static List<Class<?>> withFixture(final List<Class<?>> provided, final Class<?> fixtureType) {
        var extended = new ArrayList<>(provided);
        extended.add(fixtureType);
        return extended;
    }

    /**
     * Returns the fixture type declared by a scope's before slot, or {@code null} when the slot is not a fixture.
     *
     * <p>The {@code Instance} builder puts the creating step first in the slot, directly or as the first child of
     * the sequential that also holds the user's before step, so the search follows {@code Sequential} children.
     */
    private static Class<?> fixtureTypeOf(final Action slot) {
        if (slot instanceof Step step && StepAccessors.consumerOf(step) instanceof FixtureCreator creator) {
            return creator.declaredType();
        }
        if (slot instanceof Sequential sequential) {
            for (var child : sequential.children()) {
                var type = fixtureTypeOf(child);
                if (type != null) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * Rejects a typed step that no enclosing {@code Instance} provides a compatible fixture for, and one whose
     * enclosing fixtures are ambiguous.
     *
     * <p>The check is per step rather than per level: sibling fixtures in unrelated subtrees are fine, because no
     * step sees both. A step sees only its ancestors, so exactly one of those must provide the type it asks for.
     */
    private static void requireFixture(final Step step, final List<String> path, final List<Class<?>> provided) {
        if (!(StepAccessors.consumerOf(step) instanceof FixtureConsumer<?> consumer)) {
            return;
        }
        var type = consumer.type();
        var matching = new LinkedHashMap<Class<?>, Boolean>();
        for (var candidate : provided) {
            if (type.isAssignableFrom(candidate)) {
                matching.put(candidate, Boolean.TRUE);
            }
        }
        if (matching.isEmpty()) {
            throw new IllegalStateException("Step '" + String.join(" / ", path) + "' requires a fixture of type "
                    + type.getName() + ", but no enclosing Instance provides one"
                    + (provided.isEmpty()
                            ? "; wrap the step in Instance.of(name, " + type.getSimpleName() + ".class, ...)"
                            : "; enclosing fixtures provide " + names(provided)));
        }
        if (matching.size() > 1) {
            throw new IllegalStateException("Step '" + String.join(" / ", path) + "' requires a fixture of type "
                    + type.getName() + ", but its enclosing Instances provide more than one compatible fixture ("
                    + names(new ArrayList<>(matching.keySet()))
                    + "); a typed step could not tell them apart");
        }
    }

    private static String names(final List<Class<?>> provided) {
        var names = new ArrayList<String>(provided.size());
        for (var type : provided) {
            names.add(type.getName());
        }
        return String.join(", ", names);
    }

    private static String lockNameOf(final Action action) {
        return action instanceof Isolated isolated ? isolated.lockName() : null;
    }

    private static Set<String> withLock(final Set<String> locks, final String lockName) {
        var extended = new HashSet<>(locks);
        extended.add(lockName);
        return Set.copyOf(extended);
    }

    private static long timeoutNanosOf(final Timeout timeout) {
        try {
            return timeout.timeout().toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}

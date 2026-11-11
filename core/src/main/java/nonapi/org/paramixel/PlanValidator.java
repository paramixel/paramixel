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
import java.util.List;
import java.util.Map;
import java.util.Set;
import nonapi.org.paramixel.listener.Listeners;
import org.paramixel.api.exception.ConfigurationException;

/**
 * Validates a compiled plan before anything runs, and reports every problem at once.
 *
 * <p>A child of a pooled {@code Parallel} holds {@code pool:<name>} for its whole subtree, and a non-pass-through
 * {@code Isolated} node holds {@code lock:<name>} for its child. Walking the plan with the set of held resources
 * catches three mistakes that would otherwise hang a run:
 *
 * <ul>
 *   <li>a pool that is not defined in the configuration (every undefined pool is reported in one message);
 *   <li>a pool that is already held on the path, because a child waiting for a permit its own ancestor holds can
 *       never proceed once the pool is exhausted;
 *   <li>a cycle in the resource order, because two branches that take the same resources in opposite order can
 *       deadlock.
 * </ul>
 *
 * <p>Structural rules that are decidable from the plan are checked too: a {@code Loop} must have at least one
 * iteration. Every problem is gathered and thrown together as a {@link ConfigurationException}, so one run reports
 * every configuration mistake instead of only the first.
 *
 * <p>The check is conservative and independent of pool sizes. It cannot see locks taken inside a {@code Step}.
 */
final class PlanValidator {

    private final Pools pools;
    private final List<String> errors = new ArrayList<>();
    private final Map<String, Map<String, String>> edges = new LinkedHashMap<>();

    private PlanValidator(final Pools pools) {
        this.pools = pools;
    }

    /**
     * Validates the plan.
     *
     * @param plan the compiled plan; must not be {@code null}
     * @param pools the run's pools; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws ConfigurationException if the plan or its configuration is invalid, listing every problem
     */
    static void validate(final CompiledPlan plan, final Pools pools) {
        var validator = new PlanValidator(pools);
        validator.walk(plan.root(), new ArrayList<>());
        validator.requireAcyclic();
        if (!validator.errors.isEmpty()) {
            throw new ConfigurationException("Invalid Paramixel configuration:" + System.lineSeparator() + "  - "
                    + String.join(System.lineSeparator() + "  - ", validator.errors));
        }
    }

    private record Held(String resource, String path) {}

    private void walk(final CompiledPlan.Node node, final List<Held> held) {
        var path = Listeners.formatIdPath(node.descriptor);
        switch (node.kind) {
            case PARALLEL -> {
                try {
                    pools.pool(node.poolName);
                } catch (ConfigurationException e) {
                    errors.add(e.getMessage() + " (used by " + path + ")");
                }
                var resource = "pool:" + node.poolName;
                requireNotHeld(resource, held, path);
                connect(held, resource, path);
                var inner = new ArrayList<>(held);
                inner.add(new Held(resource, path));
                children(node, inner);
            }
            case ISOLATED -> {
                if (node.passThrough) {
                    children(node, held);
                    return;
                }
                var resource = "lock:" + node.lockName;
                connect(held, resource, path);
                var inner = new ArrayList<>(held);
                inner.add(new Held(resource, path));
                children(node, inner);
            }
            default -> children(node, held);
        }
    }

    private void children(final CompiledPlan.Node node, final List<Held> held) {
        if (node.before != null) {
            walk(node.before, held);
        }
        for (var child : node.children) {
            walk(child, held);
        }
        if (node.after != null) {
            walk(node.after, held);
        }
    }

    private void requireNotHeld(final String resource, final List<Held> held, final String path) {
        for (var h : held) {
            if (h.resource().equals(resource)) {
                var pool = resource.substring("pool:".length());
                if (Pools.isRootPool(pool)) {
                    errors.add("'" + path + "' is inside '" + h.path()
                            + "', which already holds the runner's root pool: a user Parallel cannot reuse the root"
                            + " pool; name a pool");
                    return;
                }
                errors.add("Parallel pool '" + pool + "' is used by '" + path + "' inside '" + h.path()
                        + "', which already holds it: a pool cannot be nested under itself (use a different pool for"
                        + " the inner Parallel)");
                return;
            }
        }
    }

    private void connect(final List<Held> held, final String resource, final String path) {
        for (var h : held) {
            edges.computeIfAbsent(h.resource(), k -> new LinkedHashMap<>())
                    .putIfAbsent(
                            resource,
                            "'" + h.path() + "' holds " + h.resource() + " while '" + path + "' takes " + resource);
        }
    }

    private void requireAcyclic() {
        var done = new HashSet<String>();
        for (var start : edges.keySet()) {
            visit(start, new ArrayList<>(), done);
        }
    }

    private void visit(final String resource, final List<String> stack, final Set<String> done) {
        if (done.contains(resource)) {
            return;
        }
        var at = stack.indexOf(resource);
        if (at >= 0) {
            var cycle = new ArrayList<>(stack.subList(at, stack.size()));
            cycle.add(resource);
            var detail = new StringBuilder();
            for (var i = 0; i + 1 < cycle.size(); i++) {
                detail.append(System.lineSeparator())
                        .append("      ")
                        .append(edges.get(cycle.get(i)).get(cycle.get(i + 1)));
            }
            errors.add("Resources can be taken in conflicting order and may deadlock: " + String.join(" -> ", cycle)
                    + detail);
            return;
        }
        stack.add(resource);
        for (var next : edges.getOrDefault(resource, Map.of()).keySet()) {
            visit(next, stack, done);
        }
        stack.remove(stack.size() - 1);
        done.add(resource);
    }
}

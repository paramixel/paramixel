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

package nonapi.org.paramixel.compiler;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable compiled plan: the occurrence tree, its deterministic identities, its resolved child order, and the
 * resource capacities it requires.
 *
 * <p>A plan contains no execution state and no fixture. It may be inspected concurrently and is independent of any
 * run, so one definition may be compiled and executed by several runner instances without shared mutable state.
 */
public final class CompiledPlan {

    private final CompiledOccurrence root;
    private final List<CompiledOccurrence> preOrder;
    private final Set<String> poolNames;
    private final Map<String, Integer> poolCapacities;
    private final Set<String> lockNames;
    private final List<Diagnostic> warnings;

    CompiledPlan(
            final CompiledOccurrence root,
            final List<CompiledOccurrence> preOrder,
            final Set<String> poolNames,
            final Map<String, Integer> poolCapacities,
            final Set<String> lockNames,
            final List<Diagnostic> warnings) {
        this.root = root;
        this.preOrder = List.copyOf(preOrder);
        this.poolNames = Set.copyOf(poolNames);
        this.poolCapacities = Map.copyOf(poolCapacities);
        this.lockNames = Set.copyOf(lockNames);
        this.warnings = List.copyOf(warnings);
    }

    /**
     * Returns the root occurrence.
     *
     * @return the root; never {@code null}
     */
    public CompiledOccurrence root() {
        return root;
    }

    /**
     * Returns every occurrence in depth-first admission order.
     *
     * @return the immutable occurrences; never {@code null}
     */
    public List<CompiledOccurrence> preOrder() {
        return preOrder;
    }

    /**
     * Returns the user pool names the plan requires.
     *
     * @return the immutable names; never {@code null}
     */
    public Set<String> poolNames() {
        return poolNames;
    }

    /**
     * Returns the resolved pool capacities.
     *
     * @return the immutable capacities; never {@code null}
     */
    public Map<String, Integer> poolCapacities() {
        return poolCapacities;
    }

    /**
     * Returns the exclusive lock names the plan requires.
     *
     * @return the immutable names; never {@code null}
     */
    public Set<String> lockNames() {
        return lockNames;
    }

    /**
     * Returns the non-fatal diagnostics produced during compilation.
     *
     * @return the immutable warnings; never {@code null}
     */
    public List<Diagnostic> warnings() {
        return warnings;
    }

    /**
     * Returns the occurrence with the supplied identifier.
     *
     * @param id the occurrence identifier; must not be {@code null}
     * @return the occurrence
     * @throws java.util.NoSuchElementException if no occurrence has that identifier
     */
    public CompiledOccurrence occurrence(final String id) {
        Objects.requireNonNull(id, "id is null");
        for (CompiledOccurrence occurrence : preOrder) {
            if (occurrence.id().equals(id)) {
                return occurrence;
            }
        }
        throw new java.util.NoSuchElementException("No occurrence with id " + id);
    }
}

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
import java.util.Objects;
import org.paramixel.api.ExecutionMode;
import org.paramixel.api.Task;

/**
 * One compiled occurrence of a {@link Task}.
 *
 * <p>A definition can be included several times in one plan; the compiler materializes a distinct {@code
 * CompiledOccurrence} per inclusion, with a distinct occurrence identifier and its own binding slot, so per-run state
 * never keys on user-provided object identity.
 *
 * <p>Everything here is immutable and safe to read concurrently. No execution state and no fixture is stored.
 */
public final class CompiledOccurrence {

    private final String id;
    private final String definitionId;
    private final String parentId;
    private final String name;
    private final String path;
    private final Task task;
    private final ExecutionMode mode;
    private final String poolName;
    private final int budget;
    private final String bindingSlot;
    private final int preOrderIndex;
    private final int depth;
    private final boolean failFastScope;
    private final Long shuffleSeed;
    private final List<String> executionOrder;
    private final List<CompiledOccurrence> children;

    CompiledOccurrence(
            final String id,
            final String definitionId,
            final String parentId,
            final String name,
            final String path,
            final Task task,
            final ExecutionMode mode,
            final String poolName,
            final int budget,
            final String bindingSlot,
            final int preOrderIndex,
            final int depth,
            final boolean failFastScope,
            final Long shuffleSeed,
            final List<String> executionOrder,
            final List<CompiledOccurrence> children) {
        this.id = Objects.requireNonNull(id, "id is null");
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId is null");
        this.parentId = parentId;
        this.name = Objects.requireNonNull(name, "name is null");
        this.path = Objects.requireNonNull(path, "path is null");
        this.task = Objects.requireNonNull(task, "task is null");
        this.mode = Objects.requireNonNull(mode, "mode is null");
        this.poolName = poolName;
        this.budget = budget;
        this.bindingSlot = bindingSlot;
        this.preOrderIndex = preOrderIndex;
        this.depth = depth;
        this.failFastScope = failFastScope;
        this.shuffleSeed = shuffleSeed;
        this.executionOrder = List.copyOf(executionOrder);
        this.children = List.copyOf(children);
    }

    /**
     * Returns the occurrence identifier, derived from the structural ordinal path.
     *
     * @return the identifier; never {@code null}
     */
    public String id() {
        return id;
    }

    /**
     * Returns the stable identifier of the underlying definition node.
     *
     * <p>The same {@link Task} object always yields the same definition identifier, regardless of where it is placed.
     *
     * @return the definition identifier; never {@code null}
     */
    public String definitionId() {
        return definitionId;
    }

    /**
     * Returns the parent occurrence identifier.
     *
     * @return the parent identifier, or {@code null} for the root
     */
    public String parentId() {
        return parentId;
    }

    /**
     * Returns the display name.
     *
     * @return the name; never {@code null}
     */
    public String name() {
        return name;
    }

    /**
     * Returns the display path.
     *
     * @return the path; never {@code null}
     */
    public String path() {
        return path;
    }

    /**
     * Returns the immutable definition node.
     *
     * @return the task; never {@code null}
     */
    public Task task() {
        return task;
    }

    /**
     * Returns the child execution mode.
     *
     * @return the mode; never {@code null}
     */
    public ExecutionMode mode() {
        return mode;
    }

    /**
     * Returns the pool name of a parallel task.
     *
     * @return the pool name, or {@code null} for a sequential task
     */
    public String poolName() {
        return poolName;
    }

    /**
     * Returns the resolved concurrency budget of this task's direct children.
     *
     * <p>For a parallel task this is the capacity of its pool, or the runner admission budget for a discovery root.
     * A sequential task reports one.
     *
     * @return the budget; at least one
     */
    public int budget() {
        return budget;
    }

    /**
     * Returns the identifier of the nearest enclosing instance-owning occurrence.
     *
     * <p>This is the lexical binding slot: callbacks of this occurrence receive exactly the instance that belongs to
     * that slot, and no reflective type lookup is involved.
     *
     * @return the binding slot identifier, or {@code null} when no instance is bound in this scope
     */
    public String bindingSlot() {
        return bindingSlot;
    }

    /**
     * Returns the depth-first admission index used for deterministic priority.
     *
     * @return the index; zero for the root
     */
    public int preOrderIndex() {
        return preOrderIndex;
    }

    /**
     * Returns the nesting depth.
     *
     * @return the depth; zero for the root
     */
    public int depth() {
        return depth;
    }

    /**
     * Returns whether this task's direct children are the run's root occurrences for the fail-fast admission policy.
     *
     * @return {@code true} when a non-passing direct child stops admitting later direct children
     */
    public boolean failFastScope() {
        return failFastScope;
    }

    /**
     * Returns the effective shuffle seed of this task's direct children.
     *
     * @return the seed, or {@code null}
     */
    public Long shuffleSeed() {
        return shuffleSeed;
    }

    /**
     * Returns the direct child names in effective execution order.
     *
     * @return the immutable names; never {@code null}
     */
    public List<String> executionOrder() {
        return executionOrder;
    }

    /**
     * Returns the direct child occurrences in effective execution order.
     *
     * @return the immutable children; never {@code null}
     */
    public List<CompiledOccurrence> children() {
        return children;
    }

    @Override
    public String toString() {
        return "Occurrence[" + id + " " + path + "]";
    }
}

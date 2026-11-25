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

package org.paramixel.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.paramixel.api.support.RepeatPolicy;
import org.paramixel.api.support.RetryPolicy;

/**
 * The immutable implementation shared by {@link Sequential}, {@link Parallel}, and {@link Repeat}.
 *
 * <p>It deliberately does not implement {@link Task}: {@code Task} is sealed to the three node types, so a shared base
 * cannot be a permitted subtype. A subclass implements {@code Task} and inherits these {@code public} methods as the
 * interface implementation, which keeps the accessor logic in one place without adding a fourth node type.
 */
abstract class AbstractTask {

    private final TaskState state;

    AbstractTask(final TaskState state) {
        this.state = Objects.requireNonNull(state, "state is null");
    }

    /**
     * Returns the display name.
     *
     * @return the name; never {@code null} or blank
     */
    public String name() {
        return state.name();
    }

    /**
     * Returns how this task's direct children are executed.
     *
     * @return the execution mode; never {@code null}
     */
    public ExecutionMode mode() {
        return state.mode();
    }

    /**
     * Returns the concurrency budget of this task's direct children.
     *
     * @return the pool for a {@link Parallel} task, or an empty optional
     */
    public Optional<Pool> pool() {
        return Optional.ofNullable(state.pool());
    }

    /**
     * Returns whether a non-passing direct child stops later direct children of a sequential task.
     *
     * @return {@code true} when the task's direct children are dependent
     */
    public boolean dependent() {
        return state.dependent();
    }

    /**
     * Returns the direct children in resolved order.
     *
     * @return the immutable ordered children; never {@code null}
     */
    public List<Task> children() {
        return state.children();
    }

    /**
     * Returns the anchor class this definition resolved its hook identifiers against.
     *
     * @return the anchor, or an empty optional for a definition that names none
     */
    public Optional<Class<?>> anchor() {
        return Optional.ofNullable(state.anchor());
    }

    /**
     * Returns the values seeded into this task's scope at the start of every attempt.
     *
     * @return the immutable bindings, in declaration order; never {@code null}
     */
    public Map<String, Object> bindings() {
        return state.bindings();
    }

    /**
     * Returns the rendered plan parameters of this node.
     *
     * @return the immutable rendered parameters, in declaration order; never {@code null}
     */
    public Map<String, String> parameters() {
        return state.parameters();
    }

    /**
     * Returns the {@code before} slot of this node.
     *
     * @return the slot, or an empty optional when the node declares no {@code before}
     */
    public Optional<PhaseHook> before() {
        return Optional.ofNullable(state.before());
    }

    /**
     * Returns the {@code after} slot of this node.
     *
     * @return the slot, or an empty optional when the node declares no {@code after}
     */
    public Optional<PhaseHook> after() {
        return Optional.ofNullable(state.after());
    }

    /**
     * Returns the body operation, if this task has one.
     *
     * @return the body, or an empty optional for a pure group
     */
    public Optional<TaskCallback> body() {
        return Optional.ofNullable(state.body());
    }

    /**
     * Returns the declared tags.
     *
     * @return the immutable tags; never {@code null}
     */
    public List<String> tags() {
        return state.tags();
    }

    /**
     * Returns the shuffle seed applied to this task's direct children.
     *
     * @return the seed, or an empty optional when the children are not shuffled
     */
    public Optional<Long> shuffleSeed() {
        return Optional.ofNullable(state.shuffleSeed());
    }

    /**
     * Returns the runtime condition that gates this task's normal work.
     *
     * @return the condition, or an empty optional
     */
    public Optional<Predicate<Context>> condition() {
        return Optional.ofNullable(state.condition());
    }

    /**
     * Returns the cooperative timeout of this task and its subtree.
     *
     * @return the timeout, or an empty optional
     */
    public Optional<Duration> timeout() {
        return Optional.ofNullable(state.timeout());
    }

    /**
     * Returns the retry policy.
     *
     * @return the policy, or an empty optional
     */
    public Optional<RetryPolicy> retry() {
        return Optional.ofNullable(state.retry());
    }

    /**
     * Returns the repetition policy.
     *
     * @return the policy, or an empty optional
     */
    public Optional<RepeatPolicy> repeat() {
        return Optional.ofNullable(state.repeat());
    }

    /**
     * Returns the exclusive lock name this task acquires for its whole lifetime.
     *
     * @return the lock name, or an empty optional
     */
    public Optional<String> exclusiveLock() {
        return Optional.ofNullable(state.exclusiveLock());
    }

    /**
     * Returns whether this task is a pure no-op that runs no user code.
     *
     * @return {@code true} when the task declares no body, children, before or after slot, condition, or repeat policy
     */
    public boolean isEmpty() {
        return state.body() == null
                && state.children().isEmpty()
                && state.before() == null
                && state.after() == null
                && state.condition() == null
                && state.repeat() == null;
    }

    @Override
    public String toString() {
        final var pool = state.pool();
        return "Task[" + state.name() + ", " + state.mode() + (pool == null ? "" : ", pool=" + pool.name())
                + ", children=" + state.children().size() + "]";
    }
}

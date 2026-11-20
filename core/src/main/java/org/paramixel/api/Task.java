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
import java.util.Optional;
import java.util.function.Predicate;
import org.paramixel.api.support.RepeatPolicy;
import org.paramixel.api.support.RetryPolicy;

/**
 * An immutable, reusable task definition: one runtime definition type for a step, group, instance scope, condition,
 * repetition, or timeout boundary.
 *
 * <p>A task is a node of an ordered containment tree. It may have a body, children, or both. Its direct children run
 * {@linkplain #mode() sequentially or in parallel}; a parallel task names the {@link Pool} that bounds its children.
 *
 * <p><strong>This type is a definition, not an execution.</strong> It holds no status, no fixture, no timestamps, and
 * no builder methods. Building or compiling a definition never invokes a factory, a lifecycle callback, a test body, or
 * a runtime predicate. A definition may be reused: each inclusion under a parent is compiled into a distinct execution
 * occurrence with its own identity and its own owned instance.
 *
 * <p>User-provided lambdas and captured objects are opaque to Paramixel: callers are responsible for the thread safety
 * of their own captured mutable state.
 *
 * <p>Instances are created by {@link TaskBuilder#build()} and {@link Tasks}; equality is identity.
 */
public final class Task {

    private final String name;
    private final ExecutionMode mode;
    private final Pool pool;
    private final boolean dependent;
    private final List<Task> children;
    private final List<TaskCallback> before;
    private final List<TaskCallback> after;
    private final TaskCallback body;
    private final TaskFactory instanceFactory;
    private final boolean requiresPrimaryInstance;
    private final List<String> tags;
    private final Long shuffleSeed;
    private final Predicate<Context> condition;
    private final Duration timeout;
    private final RetryPolicy retry;
    private final RepeatPolicy repeat;
    private final String exclusiveLock;
    private final List<ResourceDeclaration> resources;

    Task(final Structure structure, final Lifecycle lifecycle, final Policies policies) {
        this.name = structure.name();
        this.mode = structure.mode();
        this.pool = structure.pool();
        this.dependent = structure.dependent();
        this.children = List.copyOf(structure.children());
        this.before = List.copyOf(lifecycle.before());
        this.after = List.copyOf(lifecycle.after());
        this.body = lifecycle.body();
        this.instanceFactory = lifecycle.instanceFactory();
        this.requiresPrimaryInstance = lifecycle.requiresPrimaryInstance();
        this.resources = List.copyOf(lifecycle.resources());
        this.tags = List.copyOf(policies.tags());
        this.shuffleSeed = policies.shuffleSeed();
        this.condition = policies.condition();
        this.timeout = policies.timeout();
        this.retry = policies.retry();
        this.repeat = policies.repeat();
        this.exclusiveLock = policies.exclusiveLock();
    }

    /**
     * The structural part of a definition, handed from {@link TaskBuilder#build()} to the constructor.
     *
     * @param name the display name
     * @param mode the child execution mode
     * @param pool the pool of a parallel task, or {@code null}
     * @param dependent whether a non-passing child stops later siblings
     * @param children the ordered children; copied by the constructor
     */
    record Structure(String name, ExecutionMode mode, Pool pool, boolean dependent, List<Task> children) {}

    /**
     * The lifecycle part of a definition, handed from {@link TaskBuilder#build()} to the constructor.
     *
     * @param before the before callbacks; copied by the constructor
     * @param after the after callbacks; copied by the constructor
     * @param body the body, or {@code null}
     * @param instanceFactory the primary instance factory, or {@code null}
     * @param requiresPrimaryInstance whether typed callbacks need an inherited instance
     * @param resources the resource declarations; copied by the constructor
     */
    record Lifecycle(
            List<TaskCallback> before,
            List<TaskCallback> after,
            TaskCallback body,
            TaskFactory instanceFactory,
            boolean requiresPrimaryInstance,
            List<ResourceDeclaration> resources) {}

    /**
     * The execution policies of a definition, handed from {@link TaskBuilder#build()} to the constructor.
     *
     * @param tags the tags; copied by the constructor
     * @param shuffleSeed the effective shuffle seed, or {@code null}
     * @param condition the condition, or {@code null}
     * @param timeout the timeout, or {@code null}
     * @param retry the retry policy, or {@code null}
     * @param repeat the repeat policy, or {@code null}
     * @param exclusiveLock the exclusive lock name, or {@code null}
     */
    record Policies(
            List<String> tags,
            Long shuffleSeed,
            Predicate<Context> condition,
            Duration timeout,
            RetryPolicy retry,
            RepeatPolicy repeat,
            String exclusiveLock) {}

    /**
     * Returns the display name.
     *
     * <p>Names are nonblank but not unique: the same name may appear under one parent, and identity always comes from
     * the compiler-assigned occurrence identifier, never from the name.
     *
     * @return the name; never {@code null} or blank
     */
    public String name() {
        return name;
    }

    /**
     * Returns how this task's direct children are executed.
     *
     * @return the execution mode; never {@code null}
     */
    public ExecutionMode mode() {
        return mode;
    }

    /**
     * Returns the concurrency budget of this task's direct children.
     *
     * @return the pool for a parallel task, or an empty optional for a sequential task
     */
    public Optional<Pool> pool() {
        return Optional.ofNullable(pool);
    }

    /**
     * Returns whether a non-passing direct child stops later direct children of a sequential task.
     *
     * <p>{@code true} is the default {@code dependent} policy. {@code false} is the {@code independent} policy: later
     * children are still attempted, while global cancellation, timeout, and fatal runner abort still override
     * continuation. The flag is always {@code true} for a parallel task, which has no continuation policy.
     *
     * @return {@code true} when the task's direct children are dependent
     */
    public boolean dependent() {
        return dependent;
    }

    /**
     * Returns the direct children in resolved order.
     *
     * @return the immutable ordered children; never {@code null}
     */
    public List<Task> children() {
        return children;
    }

    /**
     * Returns the {@code before} callbacks in declaration order.
     *
     * @return the immutable ordered callbacks; never {@code null}
     */
    public List<TaskCallback> before() {
        return before;
    }

    /**
     * Returns the {@code after} callbacks in declaration order.
     *
     * <p>The runtime invokes them in reverse declaration order.
     *
     * @return the immutable ordered callbacks; never {@code null}
     */
    public List<TaskCallback> after() {
        return after;
    }

    /**
     * Returns the body operation, if this task has one.
     *
     * @return the body, or an empty optional for a pure group
     */
    public Optional<TaskCallback> body() {
        return Optional.ofNullable(body);
    }

    /**
     * Returns the factory of the primary instance owned by this task, if any.
     *
     * @return the instance factory, or an empty optional
     */
    public Optional<TaskFactory> instanceFactory() {
        return Optional.ofNullable(instanceFactory);
    }

    /**
     * Returns whether this task or one of its callbacks needs a primary instance bound by an enclosing task.
     *
     * <p>The compiler uses this to reject a definition whose typed callbacks have no lexical instance binding, before
     * any user code runs.
     *
     * @return {@code true} when a primary instance is required
     */
    public boolean requiresPrimaryInstance() {
        return requiresPrimaryInstance;
    }

    /**
     * Returns the declared tags.
     *
     * @return the immutable tags; never {@code null}
     */
    public List<String> tags() {
        return tags;
    }

    /**
     * Returns the shuffle seed applied to this task's direct children.
     *
     * @return the seed, or an empty optional when the children are not shuffled
     */
    public Optional<Long> shuffleSeed() {
        return Optional.ofNullable(shuffleSeed);
    }

    /**
     * Returns the runtime condition that gates this task's normal work.
     *
     * @return the condition, or an empty optional
     */
    public Optional<Predicate<Context>> condition() {
        return Optional.ofNullable(condition);
    }

    /**
     * Returns the cooperative timeout of this task and its subtree.
     *
     * @return the timeout, or an empty optional
     */
    public Optional<Duration> timeout() {
        return Optional.ofNullable(timeout);
    }

    /**
     * Returns the retry policy.
     *
     * @return the policy, or an empty optional
     */
    public Optional<RetryPolicy> retry() {
        return Optional.ofNullable(retry);
    }

    /**
     * Returns the repetition policy.
     *
     * @return the policy, or an empty optional
     */
    public Optional<RepeatPolicy> repeat() {
        return Optional.ofNullable(repeat);
    }

    /**
     * Returns the exclusive lock name this task acquires for its whole lifetime.
     *
     * @return the lock name, or an empty optional
     */
    public Optional<String> exclusiveLock() {
        return Optional.ofNullable(exclusiveLock);
    }

    /**
     * Returns the declared secondary resources.
     *
     * @return the immutable declarations; never {@code null}
     */
    public List<ResourceDeclaration> resources() {
        return resources;
    }

    /**
     * Returns whether this task is a pure no-op that runs no user code: no body, no children, no {@code before} or
     * {@code after} callbacks, no instance or resource factory, no condition, and no repeat policy.
     *
     * <p>Execution metadata such as a timeout, retry policy, exclusive lock, pool, tags, or shuffle seed does not count
     * as work: it only governs how work runs, so a task that declares nothing else is still empty.
     *
     * <p>An empty named task is allowed and passes when admitted; optional strict diagnostics may warn about it.
     *
     * @return {@code true} when the task declares no work
     */
    public boolean isEmpty() {
        return body == null
                && children.isEmpty()
                && before.isEmpty()
                && after.isEmpty()
                && instanceFactory == null
                && resources.isEmpty()
                && condition == null
                && repeat == null;
    }

    @Override
    public String toString() {
        return "Task[" + name + ", " + mode + (pool == null ? "" : ", pool=" + pool.name()) + ", children="
                + children.size() + "]";
    }
}

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
import java.util.Optional;
import java.util.function.Predicate;
import org.paramixel.api.support.RepeatPolicy;
import org.paramixel.api.support.RetryPolicy;

/**
 * An immutable, reusable task definition: one node of an ordered containment tree.
 *
 * <p>A node is exactly one of three kinds, and each kind is a public type with static {@code builder} factories:
 *
 * <ul>
 *   <li>{@link Sequential} — direct children run one at a time in resolved order
 *       ({@link Sequential#builder(String)});</li>
 *   <li>{@link Parallel} — direct children are eligible concurrently within a {@link Pool}
 *       ({@link Parallel#builder(String, Pool)});</li>
 *   <li>{@link Repeat} — a bounded replay of one iteration body ({@link Repeat#builder(String)}).</li>
 * </ul>
 *
 * <p>Every node follows the same grammar:
 *
 * <pre>
 * bind*  &rarr;  before?  &rarr;  step+  &rarr;  after?
 * </pre>
 *
 * <p><strong>This type is a definition, not an execution.</strong> It holds no status, no fixture, no timestamps, and
 * no builder methods. Building or compiling a definition never invokes a factory, a lifecycle callback, a body, or a
 * runtime predicate. A definition may be reused: each inclusion under a parent is compiled into a distinct execution
 * occurrence with its own identity and scope.
 *
 * <p><strong>Bound values are plan parameters.</strong> A value passed to {@code bind} already exists when the plan is
 * built, is seeded into the node's scope before its {@code before} slot runs, and is reported as a plan parameter. A
 * value that only exists while the test runs — a started container, a client, a temporary directory — is created in
 * the {@code before} slot and stored with {@link Context#put(String, Object)}; runtime context entries are never
 * reported.
 *
 * <p>User-provided objects are opaque to Paramixel: callers are responsible for the thread safety of anything they
 * capture or share.
 *
 * @see Sequential
 * @see Parallel
 * @see Repeat
 * @see TaskBuilder
 */
public sealed interface Task permits Sequential, Parallel, Repeat {

    /**
     * Returns the display name.
     *
     * <p>Names are nonblank but not unique: the same name may appear under one parent, and identity always comes from
     * the compiler-assigned occurrence identifier, never from the name.
     *
     * @return the name; never {@code null} or blank
     */
    String name();

    /**
     * Returns how this task's direct children are executed.
     *
     * @return the execution mode; never {@code null}
     */
    ExecutionMode mode();

    /**
     * Returns the concurrency budget of this task's direct children.
     *
     * @return the pool of a {@link Parallel} task, or an empty optional
     */
    Optional<Pool> pool();

    /**
     * Returns whether a non-passing direct child stops later direct children of a sequential task.
     *
     * <p>{@code true} is the default {@code dependent} policy. {@code false} is the {@code independent} policy: later
     * children are still attempted, while global cancellation, timeout, and fatal runner abort still override
     * continuation. The flag is always {@code true} for a {@link Parallel} task, which has no continuation policy.
     *
     * @return {@code true} when the task's direct children are dependent
     */
    boolean dependent();

    /**
     * Returns the direct children in resolved order.
     *
     * <p>Phase subtrees are not children: a subtree given to {@code before} or {@code after} belongs to that node's
     * before or after slot and is reported there.
     *
     * @return the immutable ordered children; never {@code null}
     */
    List<Task> children();

    /**
     * Returns the anchor class this definition resolved its hook identifiers against.
     *
     * <p>The anchor is named once at the definition root and inherited by the builders attached to it. A task attached
     * from another definition keeps that definition's anchor. Discovery compares the root's anchor with the declaring
     * class of the factory that produced it.
     *
     * @return the anchor, or an empty optional for a definition that names none
     */
    Optional<Class<?>> anchor();

    /**
     * Returns the values seeded into this task's scope at the start of every attempt.
     *
     * <p>A bound value is captured when the definition is authored, so the same object is handed to every attempt. A
     * value that must be re-created per attempt belongs in a {@code before} slot with
     * {@link Context#put(String, Object)}, not here.
     *
     * @return the immutable bindings, in declaration order; never {@code null}
     */
    Map<String, Object> bindings();

    /**
     * Returns the rendered plan parameters of this node.
     *
     * <p>Each bound value is rendered once, when the definition is built, with {@link Object#toString()} and truncated
     * to 256 characters plus {@code …}. Parameters describe a plan, so they are recorded on the node that declared
     * them, are never copied onto descendants, and never describe runtime context entries.
     *
     * @return the immutable rendered parameters, in declaration order; never {@code null}
     */
    Map<String, String> parameters();

    /**
     * Returns the {@code before} slot of this node.
     *
     * @return the slot, or an empty optional when the node declares no {@code before}
     * @see PhaseHook
     */
    Optional<PhaseHook> before();

    /**
     * Returns the {@code after} slot of this node.
     *
     * <p>The slot runs once the node has started, whatever failed before it. An {@code after} subtree runs completely
     * under its own semantics; a {@link Sequential} cleanup subtree is {@link SequentialBuilder#dependent() dependent}
     * by default, so a failing cleanup step skips the later ones. Use {@link SequentialBuilder#independent()} when every
     * cleanup step must run.
     *
     * @return the slot, or an empty optional when the node declares no {@code after}
     * @see PhaseHook
     */
    Optional<PhaseHook> after();

    /**
     * Returns the body operation, if this task has one.
     *
     * @return the body, or an empty optional for a pure group
     */
    Optional<TaskCallback> body();

    /**
     * Returns the declared tags.
     *
     * @return the immutable tags; never {@code null}
     */
    List<String> tags();

    /**
     * Returns the shuffle seed applied to this task's direct children.
     *
     * @return the seed, or an empty optional when the children are not shuffled
     */
    Optional<Long> shuffleSeed();

    /**
     * Returns the runtime condition that gates this task's normal work.
     *
     * @return the condition, or an empty optional
     */
    Optional<Predicate<Context>> condition();

    /**
     * Returns the cooperative timeout of this task and its subtree.
     *
     * @return the timeout, or an empty optional
     */
    Optional<Duration> timeout();

    /**
     * Returns the retry policy.
     *
     * @return the policy, or an empty optional
     */
    Optional<RetryPolicy> retry();

    /**
     * Returns the repetition policy.
     *
     * @return the policy, or an empty optional
     */
    Optional<RepeatPolicy> repeat();

    /**
     * Returns the exclusive lock name this task acquires for its whole lifetime.
     *
     * @return the lock name, or an empty optional
     */
    Optional<String> exclusiveLock();

    /**
     * Returns whether this task is a pure no-op that runs no user code: no body, no children, no {@code before} or
     * {@code after} slot, no condition, and no repeat policy.
     *
     * <p>Execution metadata such as a timeout, retry policy, exclusive lock, pool, tags, or shuffle seed does not count
     * as work: it only governs how work runs, so a task that declares nothing else is still empty.
     *
     * <p>An empty named task is allowed and passes when admitted; optional strict diagnostics may warn about it.
     *
     * @return {@code true} when the task declares no work
     */
    boolean isEmpty();

    /**
     * Returns a short description of this node, for diagnostics only.
     *
     * @return the description; never {@code null}
     */
    @Override
    String toString();
}

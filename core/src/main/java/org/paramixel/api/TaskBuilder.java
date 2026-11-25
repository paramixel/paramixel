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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import nonapi.org.paramixel.authoring.IdResolver;
import nonapi.org.paramixel.authoring.IdRole;
import org.paramixel.api.exception.CompilationException;
import org.paramixel.api.support.RepeatPolicy;
import org.paramixel.api.support.RetryPolicy;

/**
 * Authors an immutable {@link Task} with identifier-bound hooks.
 *
 * <p>A builder is a mutable, single-thread-confined authoring surface; it is never a runtime node. {@link #build()}
 * returns a deeply immutable snapshot, so mutating a builder afterwards cannot change a task that was already built or
 * attached to a parent.
 *
 * <p>Hooks are {@code public static} methods annotated with {@link Paramixel.Id}; the builder refers to them by
 * identifier and resolves them against the definition's anchor class when {@link #build()} runs. There is no instance
 * type and no functional-interface overload: state lives in the {@link Context} scope.
 *
 * <p>Callbacks are attached to their owning task. {@code before} callbacks run in declaration order until one fails;
 * {@code after} callbacks run in reverse declaration order and all are attempted, even after failures.
 *
 * <p>Building performs no work: no factory, lifecycle callback, body, or runtime predicate is invoked while a
 * definition is authored, built, or compiled.
 *
 * @param <S> the concrete builder type, used to preserve fluent return types
 */
public abstract class TaskBuilder<S extends TaskBuilder<S>> {

    private static final Pattern VALID_RESOURCE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]*");
    private static final int MAX_NAME_LENGTH = 512;

    private final String name;
    private final ExecutionMode mode;
    private final Pool pool;
    private boolean dependent = true;
    private Class<?> anchor;
    private final Map<String, Object> bindings = new LinkedHashMap<>();
    private final List<Object> children = new ArrayList<>();
    private final List<TaskCallback> before = new ArrayList<>();
    private final List<TaskCallback> after = new ArrayList<>();
    private TaskCallback body;
    private String bodyId;
    private String conditionId;
    private final List<String> tags = new ArrayList<>();
    private boolean shuffleRequested;
    private Long shuffleSeed;
    private Predicate<Context> condition;
    private Duration timeout;
    private RetryPolicy retry;
    private RepeatPolicy repeat;
    private String exclusiveLock;

    /**
     * Creates a builder.
     *
     * @param name the display name; must not be {@code null}, blank, or longer than 512 characters
     * @param mode the child execution mode; must not be {@code null}
     * @param pool the pool of a parallel task, or {@code null} for a sequential task
     * @throws NullPointerException if {@code name} or {@code mode} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or too long
     */
    TaskBuilder(final String name, final ExecutionMode mode, final Pool pool) {
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(mode, "mode is null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Task name is blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Task name is longer than " + MAX_NAME_LENGTH + " characters: " + name);
        }
        this.name = name;
        this.mode = mode;
        this.pool = pool;
    }

    /**
     * Returns this builder as its concrete type.
     *
     * @return this builder; never {@code null}
     */
    protected abstract S self();

    /**
     * Adds a {@code before} callback that takes no arguments.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    S before(final ThrowingRunnable callback) {
        Objects.requireNonNull(callback, "callback is null");
        before.add(CallbackAdapters.runnable(callback));
        return self();
    }

    /**
     * Adds a {@code before} callback that receives only the execution context.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    S beforeWithContext(final ThrowingConsumer<Context> callback) {
        Objects.requireNonNull(callback, "callback is null");
        before.add(CallbackAdapters.contextConsumer(callback));
        return self();
    }

    /**
     * Adds an {@code after} callback that takes no arguments.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    S after(final ThrowingRunnable callback) {
        Objects.requireNonNull(callback, "callback is null");
        after.add(CallbackAdapters.runnable(callback));
        return self();
    }

    /**
     * Adds an {@code after} callback that receives only the execution context.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    S afterWithContext(final ThrowingConsumer<Context> callback) {
        Objects.requireNonNull(callback, "callback is null");
        after.add(CallbackAdapters.contextConsumer(callback));
        return self();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Name-bound authoring (`API_REFACTOR.md` revision 5, phase 1). Additive: the typed forms above remain until the
    // surface is replaced.
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Sets the anchor class that hook identifiers in this definition resolve against.
     *
     * @param anchor the anchor class; must not be {@code null}
     * @return this builder
     */
    final S anchor(final Class<?> anchor) {
        this.anchor = Objects.requireNonNull(anchor, "anchor is null");
        return self();
    }

    /**
     * Seeds a value into this task's scope at the start of every attempt.
     *
     * <p>The value is captured now, at authoring time, so the same object is handed to every attempt; anything that
     * must be re-created per attempt belongs in a {@code before} hook with {@link Context#put(String, Object)}.
     * Names must be unique within one scope.
     *
     * @param bindName the entry name; must not be {@code null} or blank
     * @param value the value; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code bindName} is blank
     */
    public S bind(final String bindName, final Object value) {
        Objects.requireNonNull(bindName, "bindName is null");
        Objects.requireNonNull(value, "value is null");
        if (bindName.isBlank()) {
            throw new IllegalArgumentException("bind name is blank");
        }
        if (bindings.containsKey(bindName)) {
            throw new IllegalArgumentException("Duplicate bind name on task '" + name + "': " + bindName);
        }
        bindings.put(bindName, value);
        return self();
    }

    /**
     * Adds a {@code before} hook by identifier, in declaration order.
     *
     * @param id the hook identifier; must not be {@code null} or blank
     * @return this builder
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public S before(final String id) {
        before.add(new PendingHook(requireId(id), IdRole.BEFORE));
        return self();
    }

    /**
     * Adds an {@code after} hook by identifier; hooks run in reverse declaration order.
     *
     * @param id the hook identifier; must not be {@code null} or blank
     * @return this builder
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public S after(final String id) {
        after.add(new PendingHook(requireId(id), IdRole.AFTER));
        return self();
    }

    /**
     * Adds a child step whose body is the identified hook and whose name is the identifier.
     *
     * @param id the hook identifier; must not be {@code null} or blank
     * @return this builder
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public S step(final String id) {
        children.add(new PendingChild(requireId(id)));
        return self();
    }

    /**
     * Sets this occurrence's body to the identified hook.
     *
     * <p>A body is an attribute of one occurrence, so a task can have both a body and children: this is the surface
     * that keeps a body-bearing step expressible once the three-argument {@code step} overloads are removed.
     *
     * @param id the hook identifier; must not be {@code null} or blank
     * @return this builder
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public S body(final String id) {
        this.bodyId = requireId(id);
        return self();
    }

    /**
     * Adds a runtime condition by identifier; the identified hook must return {@code boolean}.
     *
     * @param id the hook identifier; must not be {@code null} or blank
     * @return this builder
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public S onlyIf(final String id) {
        this.conditionId = requireId(id);
        return self();
    }

    /**
     * Attaches several builders as children, in iteration order.
     *
     * @param builders the child builders; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code builders} or any element is {@code null}
     */
    public S children(final Collection<? extends TaskBuilder<?>> builders) {
        Objects.requireNonNull(builders, "builders is null");
        for (final var builder : builders) {
            child(builder);
        }
        return self();
    }

    private static String requireId(final String id) {
        Objects.requireNonNull(id, "id is null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Hook id is blank");
        }
        return id;
    }

    /**
     * Returns whether this builder, or anything attached below it, references a hook identifier.
     *
     * <p>A definition that references no identifier needs no anchor and can be materialized immediately; one that does
     * is held until the anchor is known.
     *
     * @return {@code true} when an identifier is referenced anywhere in this subtree
     */
    final boolean usesIds() {
        if (bodyId != null || conditionId != null) {
            return true;
        }
        for (final TaskCallback hook : before) {
            if (hook instanceof PendingHook) {
                return true;
            }
        }
        for (final TaskCallback hook : after) {
            if (hook instanceof PendingHook) {
                return true;
            }
        }
        for (final Object child : children) {
            if (child instanceof PendingChild) {
                return true;
            }
            if (child instanceof TaskBuilder<?> builder && builder.usesIds()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the anchor this builder resolves against: its own, or the one inherited from the parent builder.
     *
     * @param inheritedAnchor the parent's anchor, or {@code null}
     * @return the effective anchor, or {@code null} when the definition names none
     */
    final Class<?> effectiveAnchor(final Class<?> inheritedAnchor) {
        return anchor != null ? anchor : inheritedAnchor;
    }

    /**
     * Adds a child step whose body takes no arguments.
     *
     * @param name the step name; must not be blank
     * @param operation the operation; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code operation} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    S step(final String name, final ThrowingRunnable operation) {
        Objects.requireNonNull(operation, "operation is null");
        children.add(stepTask(name, CallbackAdapters.runnable(operation)));
        return self();
    }

    /**
     * Adds a child step whose body receives only the context.
     *
     * @param name the step name; must not be blank
     * @param operation the operation; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code operation} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    S stepWithContext(final String name, final ThrowingConsumer<Context> operation) {
        Objects.requireNonNull(operation, "operation is null");
        children.add(stepTask(name, CallbackAdapters.contextConsumer(operation)));
        return self();
    }

    /**
     * Adds a child step with no-argument body and an explicit sequential subtree.
     *
     * @param name the step name; must not be blank
     * @param operation the operation; must not be {@code null}
     * @param configureChildren configures the step's own sequential child group; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    S step(final String name, final ThrowingRunnable operation, final Consumer<SequentialBuilder> configureChildren) {
        Objects.requireNonNull(operation, "operation is null");
        Objects.requireNonNull(configureChildren, "configureChildren is null");
        children.add(groupWithBody(name, CallbackAdapters.runnable(operation), configureChildren));
        return self();
    }

    /**
     * Adds a named sequential group as a child.
     *
     * <p>The group inherits the enclosing anchor and does not create a scope of its own.
     *
     * @param name the group name; must not be blank
     * @param configure configures the group; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code configure} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    S sequential(final String name, final Consumer<SequentialBuilder> configure) {
        Objects.requireNonNull(configure, "configure is null");
        var group = new SequentialBuilder(name);
        configure.accept(group);
        children.add(group.build());
        return self();
    }

    /**
     * Adds a named parallel group as a child.
     *
     * <p>Each direct child of the group holds one permit of {@code pool} for its whole lifetime. The group inherits the
     * enclosing anchor, so its hooks resolve against the same class.
     *
     * @param name the group name; must not be blank
     * @param pool the concurrency budget; must not be {@code null}
     * @param configure configures the group; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    S parallel(final String name, final Pool pool, final Consumer<ParallelBuilder> configure) {
        Objects.requireNonNull(pool, "pool is null");
        Objects.requireNonNull(configure, "configure is null");
        var group = new ParallelBuilder(name, pool);
        configure.accept(group);
        children.add(group.build());
        return self();
    }

    /**
     * Adds a named repetition task as a child.
     *
     * <p>Repetition is intentional replay, not retry: the callback must configure a finite iteration bound with
     * {@link RepeatBuilder#maxIterations(int)}.
     *
     * @param name the task name; must not be blank
     * @param configure configures the repetition; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code configure} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    S repeat(final String name, final Consumer<RepeatBuilder> configure) {
        Objects.requireNonNull(configure, "configure is null");
        var repeatBuilder = new RepeatBuilder(name);
        configure.accept(repeatBuilder);
        children.add(repeatBuilder.build());
        return self();
    }

    /**
     * Adds a conditional sequential group as a child.
     *
     * <p>The predicate runs at execution time after the group is admitted, never during authoring or compilation. A
     * false result marks the protected body and children {@code SKIPPED} with a reason; finalization of anything the
     * task already acquired still runs.
     *
     * @param name the group name; must not be blank
     * @param condition the runtime condition; must not be {@code null}
     * @param configure configures the group; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    S when(final String name, final Predicate<Context> condition, final Consumer<SequentialBuilder> configure) {
        Objects.requireNonNull(condition, "condition is null");
        Objects.requireNonNull(configure, "configure is null");
        var group = new SequentialBuilder(name);
        group.onlyIf(condition);
        configure.accept(group);
        children.add(group.build());
        return self();
    }

    /**
     * Gates this task's normal work behind a runtime condition.
     *
     * @param condition the runtime condition; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code condition} is {@code null}
     */
    S onlyIf(final Predicate<Context> condition) {
        this.condition = Objects.requireNonNull(condition, "condition is null");
        return self();
    }

    /**
     * Adds a pre-built immutable task as a child.
     *
     * <p>The compiler materializes a distinct execution occurrence at each placement, so the same definition can be
     * reused under several parents.
     *
     * @param child the child definition; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public S child(final Task child) {
        children.add(Objects.requireNonNull(child, "child is null"));
        return self();
    }

    /**
     * Adds a builder as a child, snapshotting it at addition time.
     *
     * <p>A later mutation of the child builder cannot alter the subtree already attached here.
     *
     * @param child the child builder; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public S child(final TaskBuilder<?> child) {
        Objects.requireNonNull(child, "child is null");
        if (child.usesIds()) {
            // The child references hook ids, so it cannot be resolved until the anchor is known. Materializing it here
            // would force every intermediate group to carry a class literal, which defeats anchor inheritance for the
            // ordinary bottom-up composition (`root.child(sequential("suite").child(...))`). Keep the builder and
            // build it during this builder's build, when `effectiveAnchor` is known.
            children.add(child);
        } else {
            children.add(child.build(anchor));
        }
        return self();
    }

    /**
     * Expands the supplied items into children eagerly, at definition time, preserving iteration order.
     *
     * <p>The mapper runs while the definition is authored: it may build detached definitions and capture application
     * objects, but it must not run test bodies, create fixtures, or acquire runtime resources. A {@code null} mapping
     * fails the definition immediately.
     *
     * @param <E> the item type
     * @param items the items; must not be {@code null}
     * @param mapper the mapper producing one builder per item; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code items} or {@code mapper} is {@code null}
     * @throws IllegalArgumentException if the mapper returns {@code null}
     */
    <E> S each(final Iterable<? extends E> items, final Function<? super E, ? extends TaskBuilder<?>> mapper) {
        Objects.requireNonNull(items, "items is null");
        Objects.requireNonNull(mapper, "mapper is null");
        var index = 0;
        for (final E item : items) {
            final var mapped = mapper.apply(item);
            if (mapped == null) {
                throw new IllegalArgumentException("each mapper returned null for item at index " + index);
            }
            children.add(((TaskBuilder<?>) mapped).build(anchor));
            index++;
        }
        return self();
    }

    /**
     * Requests a reproducible shuffle of this task's direct children.
     *
     * @param seed the deterministic seed; the same seed yields the same order for the same definition
     * @return this builder
     */
    public S shuffle(final long seed) {
        this.shuffleRequested = true;
        this.shuffleSeed = seed;
        return self();
    }

    /**
     * Requests a shuffle of this task's direct children with a seed chosen once, when the definition is first built.
     *
     * <p>The chosen seed is reported in the result metadata so the order can be reproduced, and later builds from this
     * builder reuse it. Calling this method discards any seed supplied earlier through {@link #shuffle(long)}.
     *
     * @return this builder
     */
    public S shuffle() {
        this.shuffleRequested = true;
        this.shuffleSeed = null;
        return self();
    }

    /**
     * Adds tags for diagnostics, selection, and reporting.
     *
     * @param values the tag values; must not be {@code null} and must not contain {@code null} or blank values
     * @return this builder
     * @throws NullPointerException if {@code values} or an element is {@code null}
     * @throws IllegalArgumentException if a tag is blank
     */
    public S tags(final String... values) {
        Objects.requireNonNull(values, "values is null");
        for (String value : values) {
            Objects.requireNonNull(value, "tag is null");
            if (value.isBlank()) {
                throw new IllegalArgumentException("Tag is blank");
            }
            tags.add(value);
        }
        return self();
    }

    /**
     * Sets a cooperative timeout for this task and its subtree.
     *
     * <p>The timeout is measured with a monotonic clock. When it expires, cancellation is requested for the subtree and
     * interruption is delivered to callbacks that are actually running. Cleanup of started owners runs only after
     * their callbacks have quiesced; a callback that ignores interruption leaves the run non-quiescent, which the
     * result reports explicitly.
     *
     * @param timeout the timeout; must be positive
     * @return this builder
     * @throws NullPointerException if {@code timeout} is {@code null}
     * @throws IllegalArgumentException if {@code timeout} is zero or negative
     */
    public S timeout(final Duration timeout) {
        Objects.requireNonNull(timeout, "timeout is null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
        this.timeout = timeout;
        return self();
    }

    /**
     * Sets a bounded retry policy for this task.
     *
     * <p>Retry re-attempts a non-success outcome named by the policy, never concurrently, and never for run
     * cancellation or assumption abort. A retried {@code instance} task creates a fresh instance per attempt.
     * A task may not be both retried and repeated.
     *
     * @param policy the policy; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code policy} is {@code null}
     */
    public S retry(final RetryPolicy policy) {
        this.retry = Objects.requireNonNull(policy, "policy is null");
        return self();
    }

    /**
     * Makes this task the exclusive holder of a named logical lock for its whole lifetime.
     *
     * <p>A lock is distinct from a pool permit. A task waiting for a lock does not occupy a worker thread, and the
     * compiler rejects provable acquisition-order cycles and unbounded lock nesting.
     *
     * @param lockName the lock name; must match {@code [A-Za-z][A-Za-z0-9_.-]*}
     * @return this builder
     * @throws NullPointerException if {@code lockName} is {@code null}
     * @throws IllegalArgumentException if the name is invalid
     */
    public S isolated(final String lockName) {
        Objects.requireNonNull(lockName, "lockName is null");
        if (!VALID_RESOURCE_NAME.matcher(lockName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid lock name '" + lockName + "': must match [A-Za-z][A-Za-z0-9_.-]*");
        }
        this.exclusiveLock = lockName;
        return self();
    }

    /**
     * Builds an immutable, deeply frozen snapshot of this definition.
     *
     * <p>The builder may be built repeatedly; each build returns a separate immutable definition, and later mutation of
     * the builder does not alter an earlier snapshot. Building has one deliberate effect on the builder: when
     * {@link #shuffle()} was requested without a seed, the first build chooses the seed and keeps it, so every later
     * build from this builder reproduces the same order.
     *
     * @return the immutable task; never {@code null}
     */
    public Task build() {
        return build(null);
    }

    /**
     * Builds this definition against the anchor inherited from the builder it is attached to.
     *
     * <p>Resolution runs here and never during execution: identifiers are matched to declared methods, malformed
     * hooks are rejected, and every problem is aggregated into one {@link CompilationException} before any hook can
     * run.
     *
     * @param inheritedAnchor the anchor of the parent builder, or {@code null} for a root
     * @return the immutable task; never {@code null}
     * @throws CompilationException if an identifier is unknown, ambiguous, malformed, or has no anchor
     */
    Task build(final Class<?> inheritedAnchor) {
        final var problems = new ArrayList<String>();
        final var task = materialize(inheritedAnchor, problems);
        if (!problems.isEmpty()) {
            throw new CompilationException(
                    problems.size() + (problems.size() == 1 ? " problem in " : " problems in ") + name,
                    List.copyOf(problems),
                    null);
        }
        return task;
    }

    /**
     * Materializes this definition against an anchor, appending every problem to the supplied list.
     *
     * <p>Subclasses override this rather than {@link #build(Class)} so that a deferred subtree — one held until the
     * anchor is known — still passes through their own policy handling.
     *
     * @param inheritedAnchor the anchor of the parent builder, or {@code null} for a root
     * @param problems the diagnostic list to append to
     * @return the immutable task; never {@code null}
     */
    Task materialize(final Class<?> inheritedAnchor, final List<String> problems) {
        final Long effectiveSeed;
        if (shuffleRequested) {
            if (shuffleSeed == null) {
                shuffleSeed = ThreadLocalRandom.current().nextLong();
            }
            effectiveSeed = shuffleSeed;
        } else {
            effectiveSeed = null;
        }
        final Class<?> effectiveAnchor = effectiveAnchor(inheritedAnchor);

        final var resolvedChildren = new ArrayList<Task>(children.size());
        for (final Object child : children) {
            if (child instanceof Task task) {
                resolvedChildren.add(task);
            } else if (child instanceof TaskBuilder<?> deferred) {
                resolvedChildren.add(deferred.materialize(effectiveAnchor, problems));
            } else {
                final var pending = (PendingChild) child;
                final var step = new SequentialBuilder(pending.id);
                step.setBody(new PendingHook(pending.id, IdRole.STEP));
                resolvedChildren.add(step.materialize(effectiveAnchor, problems));
            }
        }

        final var resolvedBefore = resolveAll(before, effectiveAnchor, problems);
        final var resolvedAfter = resolveAll(after, effectiveAnchor, problems);
        final TaskCallback resolvedBody;
        if (bodyId != null) {
            resolvedBody = IdResolver.resolveHook(bodyId, effectiveAnchor, IdRole.BODY, problems);
        } else if (body instanceof PendingHook pendingBody) {
            resolvedBody = IdResolver.resolveHook(pendingBody.id, effectiveAnchor, pendingBody.role, problems);
        } else {
            resolvedBody = body;
        }
        final Predicate<Context> resolvedCondition =
                conditionId == null ? condition : IdResolver.resolveCondition(conditionId, effectiveAnchor, problems);

        final var task = new Task(
                new Task.Structure(name, mode, pool, dependent, resolvedChildren, effectiveAnchor, bindings),
                new Task.Lifecycle(resolvedBefore, resolvedAfter, resolvedBody),
                new Task.Policies(tags, effectiveSeed, resolvedCondition, timeout, retry, repeat, exclusiveLock));
        return task;
    }

    private static List<TaskCallback> resolveAll(
            final List<TaskCallback> hooks, final Class<?> anchor, final List<String> problems) {
        final var resolved = new ArrayList<TaskCallback>(hooks.size());
        for (final TaskCallback hook : hooks) {
            if (hook instanceof PendingHook pending) {
                final var callback = IdResolver.resolveHook(pending.id, anchor, pending.role, problems);
                if (callback != null) {
                    resolved.add(callback);
                }
            } else {
                resolved.add(hook);
            }
        }
        return resolved;
    }

    /**
     * A hook identifier captured at authoring time and resolved when the definition is built.
     */
    private static final class PendingHook implements TaskCallback {

        private final String id;
        private final IdRole role;

        PendingHook(final String id, final IdRole role) {
            this.id = id;
            this.role = role;
        }

        @Override
        public void execute(final Context context) {
            throw new IllegalStateException("Unresolved hook id " + id + " (" + role + ")");
        }
    }

    /**
     * A {@code step(id)} child captured at authoring time and materialized when the definition is built.
     */
    private static final class PendingChild {

        private final String id;

        PendingChild(final String id) {
            this.id = id;
        }
    }

    final String builderName() {
        return name;
    }

    final void setDependent(final boolean dependent) {
        this.dependent = dependent;
    }

    final void setBody(final TaskCallback body) {
        this.body = Objects.requireNonNull(body, "body is null");
    }

    final void setRepeat(final RepeatPolicy repeat) {
        this.repeat = repeat;
    }

    private Task stepTask(final String name, final TaskCallback operation) {
        final var step = new SequentialBuilder(name);
        step.setBody(operation);
        return step.build();
    }

    private Task groupWithBody(
            final String name, final TaskCallback operation, final Consumer<SequentialBuilder> configureChildren) {
        final var group = new SequentialBuilder(name);
        group.setBody(operation);
        configureChildren.accept(group);
        return group.build();
    }
}

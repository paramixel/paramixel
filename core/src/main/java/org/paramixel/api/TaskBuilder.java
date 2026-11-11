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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.paramixel.api.support.RepeatPolicy;
import org.paramixel.api.support.RetryPolicy;

/**
 * Authors an immutable {@link Task} with typed fluent methods.
 *
 * <p>A builder is a mutable, single-thread-confined authoring surface; it is never a runtime node. {@link #build()}
 * returns a deeply immutable snapshot, so mutating a builder afterwards cannot change a task that was already built or
 * attached to a parent.
 *
 * <p>The type parameter {@code T} is the <strong>nearest lexical primary instance</strong>: {@code Void} means no
 * instance binding, and a nested group inherits the enclosing {@code T} without creating an instance of its own.
 * Method references such as {@code BasicTest::setUp} therefore need no class token, and nested callbacks at any depth
 * keep the same fluent type.
 *
 * <p>Callbacks are attached to their owning task. {@code before} callbacks run in declaration order until one fails;
 * {@code after} callbacks run in reverse declaration order and all are attempted, even after failures.
 *
 * <p>Building performs no work: no factory, lifecycle callback, body, or runtime predicate is invoked while a
 * definition is authored, built, or compiled.
 *
 * @param <T> the inherited primary instance type; {@link Void} when the task has no instance binding
 * @param <SELF> the concrete builder type, used to preserve fluent return types
 */
public abstract class TaskBuilder<T, SELF extends TaskBuilder<T, SELF>> {

    private static final Pattern VALID_RESOURCE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]*");
    private static final int MAX_NAME_LENGTH = 512;

    private final String name;
    private final ExecutionMode mode;
    private final Pool pool;
    private boolean dependent = true;
    private final List<Task> children = new ArrayList<>();
    private final List<TaskCallback> before = new ArrayList<>();
    private final List<TaskCallback> after = new ArrayList<>();
    private TaskCallback body;
    private TaskFactory instanceFactory;
    private boolean requiresPrimaryInstance;
    private final List<String> tags = new ArrayList<>();
    private boolean shuffleRequested;
    private Long shuffleSeed;
    private Predicate<Context> condition;
    private Duration timeout;
    private RetryPolicy retry;
    private RepeatPolicy repeat;
    private String exclusiveLock;
    private final List<ResourceDeclaration> resources = new ArrayList<>();

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
    protected abstract SELF self();

    /**
     * Adds a {@code before} callback that receives the primary instance.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    public SELF before(final ThrowingConsumer<? super T> callback) {
        Objects.requireNonNull(callback, "callback is null");
        before.add(CallbackAdapters.instanceConsumer(callback));
        requiresPrimaryInstance = true;
        return self();
    }

    /**
     * Adds a {@code before} callback that takes no arguments.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    public SELF before(final ThrowingRunnable callback) {
        Objects.requireNonNull(callback, "callback is null");
        before.add(CallbackAdapters.runnable(callback));
        return self();
    }

    /**
     * Adds a {@code before} callback that receives the primary instance and the execution context.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    public SELF beforeWithContext(final ThrowingBiConsumer<? super T, Context> callback) {
        Objects.requireNonNull(callback, "callback is null");
        before.add(CallbackAdapters.instanceAndContextConsumer(callback));
        requiresPrimaryInstance = true;
        return self();
    }

    /**
     * Adds a {@code before} callback that receives only the execution context.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    public SELF beforeWithContext(final ThrowingConsumer<Context> callback) {
        Objects.requireNonNull(callback, "callback is null");
        before.add(CallbackAdapters.contextConsumer(callback));
        return self();
    }

    /**
     * Adds an {@code after} callback that receives the primary instance.
     *
     * <p>{@code after} callbacks are attached to this task, not to its children, and run in reverse declaration order.
     * Paramixel does not implicitly close an {@link AutoCloseable} instance: the callback decides whether and how to
     * release it.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    public SELF after(final ThrowingConsumer<? super T> callback) {
        Objects.requireNonNull(callback, "callback is null");
        after.add(CallbackAdapters.instanceConsumer(callback));
        requiresPrimaryInstance = true;
        return self();
    }

    /**
     * Adds an {@code after} callback that takes no arguments.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    public SELF after(final ThrowingRunnable callback) {
        Objects.requireNonNull(callback, "callback is null");
        after.add(CallbackAdapters.runnable(callback));
        return self();
    }

    /**
     * Adds an {@code after} callback that receives the primary instance and the execution context.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    public SELF afterWithContext(final ThrowingBiConsumer<? super T, Context> callback) {
        Objects.requireNonNull(callback, "callback is null");
        after.add(CallbackAdapters.instanceAndContextConsumer(callback));
        requiresPrimaryInstance = true;
        return self();
    }

    /**
     * Adds an {@code after} callback that receives only the execution context.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    public SELF afterWithContext(final ThrowingConsumer<Context> callback) {
        Objects.requireNonNull(callback, "callback is null");
        after.add(CallbackAdapters.contextConsumer(callback));
        return self();
    }

    /**
     * Adds a child step whose body receives the primary instance.
     *
     * <p>The returned builder is this <strong>enclosing</strong> builder, so further {@code step} calls define siblings.
     * Attach children to the new step with
     * {@link #step(String, ThrowingConsumer, Consumer)}, which takes an explicit child configuration callback.
     *
     * @param name the step name; must not be blank
     * @param operation the operation; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code operation} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public SELF step(final String name, final ThrowingConsumer<? super T> operation) {
        Objects.requireNonNull(operation, "operation is null");
        children.add(stepTask(name, CallbackAdapters.instanceConsumer(operation), true));
        return self();
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
    public SELF step(final String name, final ThrowingRunnable operation) {
        Objects.requireNonNull(operation, "operation is null");
        children.add(stepTask(name, CallbackAdapters.runnable(operation), false));
        return self();
    }

    /**
     * Adds a child step whose body receives the primary instance and the context.
     *
     * @param name the step name; must not be blank
     * @param operation the operation; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code operation} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public SELF stepWithContext(final String name, final ThrowingBiConsumer<? super T, Context> operation) {
        Objects.requireNonNull(operation, "operation is null");
        children.add(stepTask(name, CallbackAdapters.instanceAndContextConsumer(operation), true));
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
    public SELF stepWithContext(final String name, final ThrowingConsumer<Context> operation) {
        Objects.requireNonNull(operation, "operation is null");
        children.add(stepTask(name, CallbackAdapters.contextConsumer(operation), false));
        return self();
    }

    /**
     * Adds a child step whose body receives the primary instance and which owns an explicit sequential subtree.
     *
     * <p>The three-argument form is the only way to attach children to a step. There is no builder method that silently
     * targets the most recently added step.
     *
     * @param name the step name; must not be blank
     * @param operation the operation; must not be {@code null}
     * @param configureChildren configures the step's own sequential child group; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public SELF step(
            final String name,
            final ThrowingConsumer<? super T> operation,
            final Consumer<SequentialBuilder<T>> configureChildren) {
        Objects.requireNonNull(operation, "operation is null");
        Objects.requireNonNull(configureChildren, "configureChildren is null");
        children.add(groupWithBody(name, CallbackAdapters.instanceConsumer(operation), true, configureChildren));
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
    public SELF step(
            final String name,
            final ThrowingRunnable operation,
            final Consumer<SequentialBuilder<T>> configureChildren) {
        Objects.requireNonNull(operation, "operation is null");
        Objects.requireNonNull(configureChildren, "configureChildren is null");
        children.add(groupWithBody(name, CallbackAdapters.runnable(operation), false, configureChildren));
        return self();
    }

    /**
     * Adds a named sequential group as a child.
     *
     * <p>The group inherits the enclosing primary instance type and does not create a new instance.
     *
     * @param name the group name; must not be blank
     * @param configure configures the group; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code configure} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public SELF sequential(final String name, final Consumer<SequentialBuilder<T>> configure) {
        Objects.requireNonNull(configure, "configure is null");
        var group = new SequentialBuilder<T>(name);
        configure.accept(group);
        children.add(group.build());
        return self();
    }

    /**
     * Adds a named parallel group as a child.
     *
     * <p>Each direct child of the group holds one permit of {@code pool} for its whole lifetime. The group inherits the
     * enclosing primary instance type, so parallel children share it and must handle concurrent access themselves.
     *
     * @param name the group name; must not be blank
     * @param pool the concurrency budget; must not be {@code null}
     * @param configure configures the group; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public SELF parallel(final String name, final Pool pool, final Consumer<ParallelBuilder<T>> configure) {
        Objects.requireNonNull(pool, "pool is null");
        Objects.requireNonNull(configure, "configure is null");
        var group = new ParallelBuilder<T>(name, pool);
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
    public SELF repeat(final String name, final Consumer<RepeatBuilder<T>> configure) {
        Objects.requireNonNull(configure, "configure is null");
        var repeatBuilder = new RepeatBuilder<T>(name);
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
    public SELF when(
            final String name, final Predicate<Context> condition, final Consumer<SequentialBuilder<T>> configure) {
        Objects.requireNonNull(condition, "condition is null");
        Objects.requireNonNull(configure, "configure is null");
        var group = new SequentialBuilder<T>(name);
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
    public SELF onlyIf(final Predicate<Context> condition) {
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
    public SELF child(final Task child) {
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
    public SELF child(final TaskBuilder<?, ?> child) {
        Objects.requireNonNull(child, "child is null");
        children.add(child.build());
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
    public <E> SELF each(
            final Iterable<? extends E> items, final Function<? super E, ? extends TaskBuilder<?, ?>> mapper) {
        Objects.requireNonNull(items, "items is null");
        Objects.requireNonNull(mapper, "mapper is null");
        var index = 0;
        for (final E item : items) {
            final var mapped = mapper.apply(item);
            if (mapped == null) {
                throw new IllegalArgumentException("each mapper returned null for item at index " + index);
            }
            children.add(mapped.build());
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
    public SELF shuffle(final long seed) {
        this.shuffleRequested = true;
        this.shuffleSeed = seed;
        return self();
    }

    /**
     * Requests a shuffle of this task's direct children with a seed chosen once, when the definition is first built.
     *
     * <p>The chosen seed is reported in the result metadata so the order can be reproduced.
     *
     * @return this builder
     */
    public SELF shuffle() {
        this.shuffleRequested = true;
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
    public SELF tags(final String... values) {
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
    public SELF timeout(final Duration timeout) {
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
     * cancellation or assumption abort. A retried {@code forInstance} task creates a fresh instance per attempt.
     * A task may not be both retried and repeated.
     *
     * @param policy the policy; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code policy} is {@code null}
     */
    public SELF retry(final RetryPolicy policy) {
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
    public SELF isolated(final String lockName) {
        Objects.requireNonNull(lockName, "lockName is null");
        if (!VALID_RESOURCE_NAME.matcher(lockName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid lock name '" + lockName + "': must match [A-Za-z][A-Za-z0-9_.-]*");
        }
        this.exclusiveLock = lockName;
        return self();
    }

    /**
     * Declares an externally owned secondary resource acquired lazily when this task is admitted.
     *
     * @param <R> the resource type
     * @param key the typed key; must not be {@code null}
     * @param factory the lazy factory; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the key is already declared on this task
     */
    public <R> SELF resource(final Key<R> key, final ThrowingSupplier<? extends R> factory) {
        Objects.requireNonNull(factory, "factory is null");
        return resource(key, (ResourceFactory) context -> factory.get(), null);
    }

    /**
     * Declares a secondary resource with a release callback acquired lazily when this task is admitted.
     *
     * @param <R> the resource type
     * @param key the typed key; must not be {@code null}
     * @param factory the lazy factory; must not be {@code null}
     * @param release the release callback, run in reverse acquisition order; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the key is already declared on this task
     */
    public <R> SELF resource(
            final Key<R> key, final ThrowingSupplier<? extends R> factory, final ThrowingConsumer<? super R> release) {
        Objects.requireNonNull(factory, "factory is null");
        Objects.requireNonNull(release, "release is null");
        final ResourceReleaser releaser = (resource, context) -> {
            @SuppressWarnings("unchecked")
            final R typed = (R) resource;
            release.accept(typed);
        };
        return resource(key, (ResourceFactory) context -> factory.get(), releaser);
    }

    /**
     * Declares a secondary resource whose factory may read resources acquired earlier in the same scope.
     *
     * @param <R> the resource type
     * @param key the typed key; must not be {@code null}
     * @param factory the lazy, context-aware factory; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the key is already declared on this task
     */
    public <R> SELF resource(final Key<R> key, final ResourceFactory factory) {
        return resource(key, factory, null);
    }

    /**
     * Declares a secondary resource with a context-aware factory and release callback.
     *
     * @param <R> the resource type
     * @param key the typed key; must not be {@code null}
     * @param factory the lazy, context-aware factory; must not be {@code null}
     * @param release the release callback, or {@code null} when the resource is externally owned
     * @return this builder
     * @throws NullPointerException if {@code key} or {@code factory} is {@code null}
     * @throws IllegalArgumentException if the key is already declared on this task
     */
    public <R> SELF resource(final Key<R> key, final ResourceFactory factory, final ResourceReleaser release) {
        Objects.requireNonNull(key, "key is null");
        Objects.requireNonNull(factory, "factory is null");
        for (ResourceDeclaration declaration : resources) {
            if (declaration.key().equals(key)) {
                throw new IllegalArgumentException("Duplicate resource key on task '" + name + "': " + key);
            }
        }
        resources.add(ResourceDeclaration.of(key, factory, release));
        return self();
    }

    /**
     * Builds an immutable, deeply frozen snapshot of this definition.
     *
     * <p>Building is side-effect free. The builder may be built repeatedly; each build returns an independent
     * definition, and later mutation of the builder does not alter an earlier snapshot.
     *
     * @return the immutable task; never {@code null}
     */
    public Task build() {
        final Long effectiveSeed;
        if (shuffleRequested) {
            if (shuffleSeed == null) {
                shuffleSeed = ThreadLocalRandom.current().nextLong();
            }
            effectiveSeed = shuffleSeed;
        } else {
            effectiveSeed = null;
        }
        return new Task(
                name,
                mode,
                pool,
                dependent,
                children,
                before,
                after,
                body,
                instanceFactory,
                requiresPrimaryInstance,
                tags,
                effectiveSeed,
                condition,
                timeout,
                retry,
                repeat,
                exclusiveLock,
                resources);
    }

    final String builderName() {
        return name;
    }

    final void setDependent(final boolean dependent) {
        this.dependent = dependent;
    }

    final void setInstanceFactory(final TaskFactory factory) {
        this.instanceFactory = Objects.requireNonNull(factory, "factory is null");
        this.requiresPrimaryInstance = true;
    }

    final void setBody(final TaskCallback body, final boolean requiresInstance) {
        this.body = Objects.requireNonNull(body, "body is null");
        this.requiresPrimaryInstance = requiresPrimaryInstance || requiresInstance;
    }

    final void setRepeat(final RepeatPolicy repeat) {
        this.repeat = repeat;
    }

    private Task stepTask(final String name, final TaskCallback operation, final boolean requiresInstance) {
        final var step = new SequentialBuilder<T>(name);
        step.setBody(operation, requiresInstance);
        return step.build();
    }

    private Task groupWithBody(
            final String name,
            final TaskCallback operation,
            final boolean requiresInstance,
            final Consumer<SequentialBuilder<T>> configureChildren) {
        final var group = new SequentialBuilder<T>(name);
        group.setBody(operation, requiresInstance);
        configureChildren.accept(group);
        return group.build();
    }
}

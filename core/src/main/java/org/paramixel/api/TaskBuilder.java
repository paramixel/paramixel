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
import java.util.IdentityHashMap;
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
 * Authors an immutable {@link Task} node with identifier-bound hooks.
 *
 * <p>A builder is a mutable, single-thread-confined authoring surface; it is never a runtime node. {@link #build()}
 * returns a deeply immutable snapshot, so mutating a builder afterwards cannot change a task that was already built.
 *
 * <p>Every node follows one grammar:
 *
 * <pre>
 * bind*  &rarr;  before?  &rarr;  step+  &rarr;  after?
 * </pre>
 *
 * <ul>
 *   <li>{@link #bind(String, Object)} seeds a value that already exists when the plan is built. It may be called any
 *       number of times and never accepts a supplier or lambda.</li>
 *   <li>{@link #before(String) before} is a single slot: an annotation id, a {@link Task}, or a {@link TaskBuilder}.
 *       A value that only exists while the test runs is created there and stored with
 *       {@link Context#put(String, Object)}.</li>
 *   <li>{@link #step(String) step} adds children in declaration order.</li>
 *   <li>{@link #after(String) after} is a single slot with the same three forms; it runs once the node has started,
 *       whatever failed before it.</li>
 * </ul>
 *
 * <p>A builder passed to {@code before}, {@code step}, or {@code after} is held, not built: it is materialized when this
 * builder's {@link #build()} runs, so a change made to it after it was passed in still takes effect. Passing the same
 * builder instance more than once anywhere in one tree is an authoring error reported at build time; pass a built
 * {@link Task} instead when the same subtree must appear twice.
 *
 * <p>Hooks are {@code public static} methods annotated with {@link Paramixel.Id}; the builder refers to them by
 * identifier and resolves them against the definition's anchor class when {@link #build()} runs. There is no instance
 * type: state lives in the {@link Context} scope.
 *
 * <p>Building performs no work: no factory, lifecycle hook, body, or runtime predicate is invoked while a definition is
 * authored, built, or compiled.
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
    private final List<Object> steps = new ArrayList<>();
    private Object beforeSlot;
    private Object afterSlot;
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

    // ------------------------------------------------------------------------------------------------------------
    // Bindings
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Binds a value that already exists when the plan is built.
     *
     * <p>The value is captured now, at authoring time, and is in the node's scope before its {@code before} slot runs,
     * so the steps, the {@code after} slot, and every descendant see it. A descendant that binds the same name shadows
     * this value inside its own subtree only.
     *
     * <p>{@code bind} never creates or computes anything: it takes an object, never a supplier, factory, or lambda.
     * An object that must be created while the test runs — a started container, a client, a temporary directory — is
     * created in the {@code before} slot and stored with {@link Context#put(String, Object)}:
     *
     * <pre>{@code
     * Sequential.builder(MyTest.class, "scenario")
     *         .bind(ENVIRONMENT, environment)
     *         .before(ID_SET_UP)
     *         .step(ID_CHECK)
     *         .after(ID_TEAR_DOWN);
     *
     * @Paramixel.Id(ID_SET_UP)
     * public static void setUp(final Context context) throws Exception {
     *     final var client = Client.start(context.require(ENVIRONMENT, Environment.class));
     *     context.put(CLIENT, client);
     * }
     * }</pre>
     *
     * <p>Bound values are reported as plan parameters; runtime context entries are never reported.
     *
     * @param bindName the entry name; must not be {@code null} or blank
     * @param value the value; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code bindName} is blank, or is already bound on this node
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

    // ------------------------------------------------------------------------------------------------------------
    // before / after: exactly one slot, three argument kinds
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Sets this node's {@code before} slot to the identified hook.
     *
     * <p>{@code before} may be called at most once per node. Several setup phases are composed into one subtree:
     * {@code .before(Sequential.builder("setup").step(setUpA).step(setUpB))}.
     *
     * @param id the hook identifier; must not be {@code null} or blank
     * @return this builder
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     * @throws IllegalStateException if this node already has a {@code before} slot
     */
    public S before(final String id) {
        requireBeforeUnset();
        beforeSlot = new PendingHook(requireId(id), IdRole.BEFORE);
        return self();
    }

    /**
     * Sets this node's {@code before} slot to a task subtree.
     *
     * <p>The subtree is materialized when this builder is built. It runs as part of this node's sequential chain for
     * {@link Context} scoping, so a value the subtree {@code put}s inside a {@link Sequential} is visible to this
     * node's steps and {@code after} slot; a value written inside a {@link Parallel} branch of the subtree stays in that
     * branch. A subtree failure counts as this node's {@code before} failing.
     *
     * @param task the subtree; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code task} is {@code null}
     * @throws IllegalStateException if this node already has a {@code before} slot
     */
    public S before(final Task task) {
        requireBeforeUnset();
        beforeSlot = Objects.requireNonNull(task, "task is null");
        return self();
    }

    /**
     * Sets this node's {@code before} slot to a builder that is materialized when this node is built.
     *
     * <p>The builder is held, not built: a change made to it after this call still takes effect. Passing the same
     * builder instance more than once anywhere in one tree is an authoring error reported at build time.
     *
     * @param builder the subtree builder; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code builder} is {@code null}
     * @throws IllegalStateException if this node already has a {@code before} slot
     */
    public S before(final TaskBuilder<?> builder) {
        requireBeforeUnset();
        beforeSlot = Objects.requireNonNull(builder, "builder is null");
        return self();
    }

    /**
     * Sets this node's {@code after} slot to the identified hook.
     *
     * <p>{@code after} runs once the node has started, whatever failed before it. It may be called at most once per
     * node. Several cleanup phases are composed into one subtree; because a {@link Sequential} cleanup subtree is
     * {@code dependent()} by default, a failing cleanup step skips the later ones, so a cleanup that must always
     * complete declares {@code .independent()}:
     * {@code .after(Sequential.builder("cleanup").independent().step(tearDownB).step(tearDownA))}.
     *
     * @param id the hook identifier; must not be {@code null} or blank
     * @return this builder
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     * @throws IllegalStateException if this node already has an {@code after} slot
     */
    public S after(final String id) {
        requireAfterUnset();
        afterSlot = new PendingHook(requireId(id), IdRole.AFTER);
        return self();
    }

    /**
     * Sets this node's {@code after} slot to a task subtree.
     *
     * <p>It runs once the node has started, whatever failed before it, and it runs completely under its own
     * {@link Sequential} or {@link Parallel} semantics.
     *
     * @param task the subtree; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code task} is {@code null}
     * @throws IllegalStateException if this node already has an {@code after} slot
     */
    public S after(final Task task) {
        requireAfterUnset();
        afterSlot = Objects.requireNonNull(task, "task is null");
        return self();
    }

    /**
     * Sets this node's {@code after} slot to a builder that is materialized when this node is built.
     *
     * @param builder the subtree builder; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code builder} is {@code null}
     * @throws IllegalStateException if this node already has an {@code after} slot
     */
    public S after(final TaskBuilder<?> builder) {
        requireAfterUnset();
        afterSlot = Objects.requireNonNull(builder, "builder is null");
        return self();
    }

    // ------------------------------------------------------------------------------------------------------------
    // steps
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Adds a step whose body is the identified hook and whose name is the identifier.
     *
     * @param id the hook identifier; must not be {@code null} or blank
     * @return this builder
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public S step(final String id) {
        steps.add(new PendingChild(requireId(id)));
        return self();
    }

    /**
     * Adds a step from an immutable task definition.
     *
     * <p>The compiler materializes a distinct execution occurrence at each placement, so the same definition may be
     * reused under several parents.
     *
     * @param task the step definition; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code task} is {@code null}
     */
    public S step(final Task task) {
        steps.add(Objects.requireNonNull(task, "task is null"));
        return self();
    }

    /**
     * Adds a step from a builder that is materialized when this node is built.
     *
     * <p>The builder is held, not built: a change made to it after this call still takes effect. Passing the same
     * builder instance more than once anywhere in one tree is an authoring error reported at build time.
     *
     * @param builder the step builder; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code builder} is {@code null}
     */
    public S step(final TaskBuilder<?> builder) {
        steps.add(Objects.requireNonNull(builder, "builder is null"));
        return self();
    }

    /**
     * Sets this occurrence's body to the identified hook.
     *
     * <p>A body is an attribute of one occurrence, so a task can have both a body and steps: this is the surface that
     * keeps a body-bearing step expressible.
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

    private void requireBeforeUnset() {
        if (beforeSlot != null) {
            throw new IllegalStateException("Task '" + name + "' already has a before slot: before(...) may be called"
                    + " at most once per task. Compose several setup tasks with Sequential.builder(...) or"
                    + " Parallel.builder(...) and pass that builder to before(...).");
        }
    }

    private void requireAfterUnset() {
        if (afterSlot != null) {
            throw new IllegalStateException("Task '" + name + "' already has an after slot: after(...) may be called"
                    + " at most once per task. Compose several cleanup tasks with Sequential.builder(...) or"
                    + " Parallel.builder(...) and pass that builder to after(...).");
        }
    }

    private static String requireId(final String id) {
        Objects.requireNonNull(id, "id is null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Hook id is blank");
        }
        return id;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Package-private authoring helpers used by internal tests
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Sets this node's {@code before} slot to a callback.
     *
     * <p>Package private: internal tests author engine scenarios without declaring hook classes. Authors use
     * {@link #before(String)} or {@link #before(TaskBuilder)}.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     * @throws IllegalStateException if this node already has a {@code before} slot
     */
    S before(final ThrowingRunnable callback) {
        Objects.requireNonNull(callback, "callback is null");
        requireBeforeUnset();
        beforeSlot = CallbackAdapters.runnable(callback);
        return self();
    }

    /**
     * Sets this node's {@code after} slot to a callback.
     *
     * <p>Package private: internal tests author engine scenarios without declaring hook classes.
     *
     * @param callback the callback; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code callback} is {@code null}
     * @throws IllegalStateException if this node already has an {@code after} slot
     */
    S after(final ThrowingRunnable callback) {
        Objects.requireNonNull(callback, "callback is null");
        requireAfterUnset();
        afterSlot = CallbackAdapters.runnable(callback);
        return self();
    }

    /**
     * Adds a step whose body takes no arguments.
     *
     * <p>Package private: internal tests author engine scenarios without declaring hook classes.
     *
     * @param stepName the step name; must not be blank
     * @param operation the operation; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code operation} is {@code null}
     * @throws IllegalArgumentException if {@code stepName} is blank
     */
    S step(final String stepName, final ThrowingRunnable operation) {
        Objects.requireNonNull(operation, "operation is null");
        steps.add(stepTask(stepName, CallbackAdapters.runnable(operation)));
        return self();
    }

    /**
     * Adds a step whose body receives only the context.
     *
     * <p>Package private: internal tests author engine scenarios without declaring hook classes.
     *
     * @param stepName the step name; must not be blank
     * @param operation the operation; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code operation} is {@code null}
     * @throws IllegalArgumentException if {@code stepName} is blank
     */
    S stepWithContext(final String stepName, final ThrowingConsumer<Context> operation) {
        Objects.requireNonNull(operation, "operation is null");
        steps.add(stepTask(stepName, CallbackAdapters.contextConsumer(operation)));
        return self();
    }

    /**
     * Adds a step with a no-argument body and an explicit sequential subtree beneath it.
     *
     * <p>Package private: internal tests author engine scenarios without declaring hook classes.
     *
     * @param stepName the step name; must not be blank
     * @param operation the operation; must not be {@code null}
     * @param configureSteps configures the step's own sequential children; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code stepName} is blank
     */
    S step(final String stepName, final ThrowingRunnable operation, final Consumer<SequentialBuilder> configureSteps) {
        Objects.requireNonNull(operation, "operation is null");
        Objects.requireNonNull(configureSteps, "configureSteps is null");
        final var step = new SequentialBuilder(stepName);
        step.setBody(CallbackAdapters.runnable(operation));
        configureSteps.accept(step);
        steps.add(step);
        return self();
    }

    /**
     * Adds a named sequential group as a step.
     *
     * <p>Package private: internal tests author engine scenarios without declaring hook classes.
     *
     * @param groupName the group name; must not be blank
     * @param configure configures the group; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code configure} is {@code null}
     * @throws IllegalArgumentException if {@code groupName} is blank
     */
    S sequential(final String groupName, final Consumer<SequentialBuilder> configure) {
        Objects.requireNonNull(configure, "configure is null");
        final var group = new SequentialBuilder(groupName);
        configure.accept(group);
        steps.add(group);
        return self();
    }

    /**
     * Adds a named parallel group as a step.
     *
     * <p>Package private: internal tests author engine scenarios without declaring hook classes.
     *
     * @param groupName the group name; must not be blank
     * @param pool the concurrency budget; must not be {@code null}
     * @param configure configures the group; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code groupName} is blank
     */
    S parallel(final String groupName, final Pool pool, final Consumer<ParallelBuilder> configure) {
        Objects.requireNonNull(pool, "pool is null");
        Objects.requireNonNull(configure, "configure is null");
        final var group = new ParallelBuilder(groupName, pool);
        configure.accept(group);
        steps.add(group);
        return self();
    }

    /**
     * Adds a named repetition task as a step.
     *
     * <p>Package private: internal tests author engine scenarios without declaring hook classes.
     *
     * @param taskName the task name; must not be blank
     * @param configure configures the repetition; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code configure} is {@code null}
     * @throws IllegalArgumentException if {@code taskName} is blank
     */
    S repeat(final String taskName, final Consumer<RepeatBuilder> configure) {
        Objects.requireNonNull(configure, "configure is null");
        final var repeatBuilder = new RepeatBuilder(taskName);
        configure.accept(repeatBuilder);
        steps.add(repeatBuilder);
        return self();
    }

    /**
     * Gates this task's normal work behind a runtime predicate.
     *
     * <p>Package private: authors use {@link #onlyIf(String)} with an identified predicate hook, so the predicate is
     * resolved against the anchor like every other hook.
     *
     * @param predicate the runtime condition; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code predicate} is {@code null}
     */
    S onlyIf(final Predicate<Context> predicate) {
        this.condition = Objects.requireNonNull(predicate, "condition is null");
        return self();
    }

    /**
     * Starts a conditional sequential builder.
     *
     * @param taskName the task name; must not be blank
     * @param predicate the runtime condition; must not be {@code null}
     * @return a sequential builder gated by {@code predicate}; never {@code null}
     */
    static SequentialBuilder when(final String taskName, final Predicate<Context> predicate) {
        Objects.requireNonNull(predicate, "condition is null");
        final var builder = new SequentialBuilder(taskName);
        builder.onlyIf(predicate);
        return builder;
    }

    /**
     * Expands the supplied items into steps eagerly, at definition time, preserving iteration order.
     *
     * <p>Package private: internal tests author engine scenarios without declaring hook classes. The mapper runs while
     * the definition is authored: it may build detached definitions and capture application objects, but it must not
     * run test bodies, create fixtures, or acquire runtime resources.
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
            steps.add(mapped);
            index++;
        }
        return self();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Policies
    // ------------------------------------------------------------------------------------------------------------

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
     * interruption is delivered to hooks that are actually running. Cleanup of started owners runs only after their
     * hooks have quiesced; a hook that ignores interruption leaves the run non-quiescent, which the result reports
     * explicitly.
     *
     * @param value the timeout; must be positive
     * @return this builder
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is zero or negative
     */
    public S timeout(final Duration value) {
        Objects.requireNonNull(value, "timeout is null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive: " + value);
        }
        this.timeout = value;
        return self();
    }

    /**
     * Sets a bounded retry policy for this task.
     *
     * <p>Retry re-attempts a non-success outcome named by the policy, never concurrently, and never for run
     * cancellation or assumption abort. A task may not be both retried and repeated.
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

    // ------------------------------------------------------------------------------------------------------------
    // Build
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Builds an immutable, deeply frozen snapshot of this definition.
     *
     * <p>The builder may be built repeatedly; each build returns a separate immutable definition, and later mutation of
     * the builder does not alter an earlier snapshot. Building has one deliberate effect on the builder: when
     * {@link #shuffle()} was requested without a seed, the first build chooses the seed and keeps it, so every later
     * build from this builder reproduces the same order.
     *
     * @return the immutable task; never {@code null}
     * @throws CompilationException if an identifier is unknown, ambiguous, malformed, or has no anchor, or if one
     *     builder is attached more than once in this tree
     */
    public Task build() {
        final var context = new BuildContext();
        final var task = materialize(null, context);
        if (!context.problems().isEmpty()) {
            throw new CompilationException(
                    context.problems().size()
                            + (context.problems().size() == 1 ? " problem in " : " problems in ")
                            + name,
                    List.copyOf(context.problems()),
                    null);
        }
        return task;
    }

    /**
     * Materializes this definition against an anchor, appending every problem to the supplied context.
     *
     * <p>Subclasses override this rather than {@link #build()} so that a deferred subtree still passes through their own
     * policy handling.
     *
     * @param inheritedAnchor the anchor of the parent builder, or {@code null} for a root
     * @param context the build context collecting problems and builder placements
     * @return the immutable task; never {@code null}
     */
    Task materialize(final Class<?> inheritedAnchor, final BuildContext context) {
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

        final var resolvedSteps = new ArrayList<Task>(steps.size());
        for (final Object step : steps) {
            if (step instanceof Task task) {
                resolvedSteps.add(task);
            } else if (step instanceof TaskBuilder<?> deferred) {
                resolvedSteps.add(materializeChild(deferred, effectiveAnchor, context, "a step"));
            } else {
                final var pending = (PendingChild) step;
                final var single = new SequentialBuilder(pending.id);
                single.setBody(new PendingHook(pending.id, IdRole.STEP));
                resolvedSteps.add(single.materialize(effectiveAnchor, context));
            }
        }

        final var resolvedBefore = materializePhase(beforeSlot, effectiveAnchor, context, IdRole.BEFORE);
        final var resolvedAfter = materializePhase(afterSlot, effectiveAnchor, context, IdRole.AFTER);
        final TaskCallback resolvedBody;
        if (bodyId != null) {
            resolvedBody = IdResolver.resolveHook(bodyId, effectiveAnchor, IdRole.BODY, context.problems());
        } else if (body instanceof PendingHook pendingBody) {
            resolvedBody =
                    IdResolver.resolveHook(pendingBody.id, effectiveAnchor, pendingBody.role, context.problems());
        } else {
            resolvedBody = body;
        }
        final Predicate<Context> resolvedCondition = conditionId == null
                ? condition
                : IdResolver.resolveCondition(conditionId, effectiveAnchor, context.problems());

        final var parameters = new LinkedHashMap<String, String>();
        bindings.forEach((bindName, value) -> parameters.put(bindName, TaskState.renderParameter(value)));

        return newTask(new TaskState(
                new TaskState.Structure(name, mode, pool, dependent, resolvedSteps, effectiveAnchor, bindings),
                new TaskState.Lifecycle(resolvedBefore, resolvedAfter, resolvedBody),
                new TaskState.Policies(tags, effectiveSeed, resolvedCondition, timeout, retry, repeat, exclusiveLock),
                parameters));
    }

    /**
     * Creates the built node for this builder kind.
     *
     * @param state the immutable node state; must not be {@code null}
     * @return the built node; never {@code null}
     */
    abstract Task newTask(TaskState state);

    private PhaseHook materializePhase(
            final Object slot, final Class<?> effectiveAnchor, final BuildContext context, final IdRole role) {
        if (slot == null) {
            return null;
        }
        if (slot instanceof PendingHook pending) {
            final var callback = IdResolver.resolveHook(pending.id, effectiveAnchor, pending.role, context.problems());
            return callback == null ? null : new PhaseHook.Callback(callback);
        }
        if (slot instanceof Task task) {
            return new PhaseHook.Subtree(task);
        }
        if (slot instanceof TaskBuilder<?> deferred) {
            final var placement = role == IdRole.BEFORE ? "the before slot" : "the after slot";
            return new PhaseHook.Subtree(materializeChild(deferred, effectiveAnchor, context, placement));
        }
        return new PhaseHook.Callback((TaskCallback) slot);
    }

    /**
     * Materializes one attached builder, recording its placement.
     *
     * @param child the attached builder; must not be {@code null}
     * @param effectiveAnchor the anchor inherited from this builder
     * @param context the build context
     * @param slot the human-readable slot the builder was attached to
     * @return the materialized subtree; never {@code null}
     */
    private Task materializeChild(
            final TaskBuilder<?> child, final Class<?> effectiveAnchor, final BuildContext context, final String slot) {
        final var nested = context.attach(child, placement(child, slot)) ? context : context.isolated();
        return child.materialize(effectiveAnchor, nested);
    }

    private String placement(final TaskBuilder<?> child, final String slot) {
        return "task '" + name + "' " + slot + " (builder '" + child.builderName() + "')";
    }

    /**
     * Sets the anchor class that hook identifiers in this definition resolve against.
     *
     * @param value the anchor class; must not be {@code null}
     * @return this builder
     */
    final S anchor(final Class<?> value) {
        this.anchor = Objects.requireNonNull(value, "anchor is null");
        return self();
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

    final String builderName() {
        return name;
    }

    final void setDependent(final boolean value) {
        this.dependent = value;
    }

    final void setBody(final TaskCallback value) {
        this.body = Objects.requireNonNull(value, "body is null");
    }

    final void setRepeat(final RepeatPolicy value) {
        this.repeat = value;
    }

    private SequentialBuilder stepTask(final String stepName, final TaskCallback operation) {
        final var step = new SequentialBuilder(stepName);
        step.setBody(operation);
        return step;
    }

    /**
     * The state collected while one tree is built: every problem found, and every builder already attached.
     *
     * <p>Placements are compared by identity, because two distinct builders may describe the same subtree while one
     * builder object must never be attached twice: a second attachment would silently duplicate a mutable subtree.
     */
    static final class BuildContext {

        private final List<String> problems;
        private final Map<TaskBuilder<?>, String> placements;

        private BuildContext(final List<String> problems, final Map<TaskBuilder<?>, String> placements) {
            this.problems = problems;
            this.placements = placements;
        }

        BuildContext() {
            this(new ArrayList<>(), new IdentityHashMap<>());
        }

        List<String> problems() {
            return problems;
        }

        /**
         * Records that a builder was attached at a placement.
         *
         * @param builder the attached builder; must not be {@code null}
         * @param placement the human-readable placement; must not be {@code null}
         * @return {@code true} when this is the first attachment of the builder in this tree
         */
        boolean attach(final TaskBuilder<?> builder, final String placement) {
            final var previous = placements.putIfAbsent(builder, placement);
            if (previous == null) {
                return true;
            }
            problems.add("Builder '" + builder.builderName() + "' is attached more than once in one tree, at "
                    + previous + " and at " + placement
                    + "; build it once and pass the resulting Task where the same subtree must appear twice");
            return false;
        }

        /**
         * Returns a context that reports into the same problems but tracks placements separately.
         *
         * <p>It is used to materialize a subtree whose root is already a reported duplicate: its own builders were
         * checked by the first materialization, so re-checking them would only repeat the same finding.
         *
         * @return the isolated context; never {@code null}
         */
        BuildContext isolated() {
            return new BuildContext(problems, new IdentityHashMap<>());
        }
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
}

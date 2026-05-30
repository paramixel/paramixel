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

package org.paramixel.api.action;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import nonapi.org.paramixel.FrameworkException;
import nonapi.org.paramixel.InstanceHolder;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.StatusAccumulator;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.ThrowingConsumer;

/**
 * An action that manages the lifecycle of a test fixture instance, including its creation,
 * before-action, body actions, after-action, and destruction.
 *
 * <p>The instance is created by a {@link Supplier} at the beginning of execution and is
 * available to all child actions via {{@link Context#instance(Class)}}. If the
 * instance implements {@link AutoCloseable}, it is closed in the after phase.
 *
 * <p>When configured as <em>dependent</em> (the default), a failure in any body child
 * causes remaining body children to be skipped. When configured as
 * <em>independent</em>, all body children run regardless of individual outcomes.
 *
 * @param <T> the type of the test fixture
 */
public final class Instance<T> implements Action<T> {

    private static final String KIND = "Instance";

    private final String name;
    private final Instantiate instantiate;
    private final List<Action<?>> children;
    private final Destroy destroy;
    private final boolean dependent;

    private Instance(
            final String name,
            final Instantiate instantiate,
            final List<Action<?>> children,
            final Destroy destroy,
            final boolean dependent) {
        Objects.requireNonNull(name, "name is null");
        this.name = Arguments.requireNonBlank(name, "name is blank");
        this.instantiate = Objects.requireNonNull(instantiate);
        this.children = List.copyOf(children);
        this.destroy = Objects.requireNonNull(destroy);
        this.dependent = dependent;
    }

    /**
     * Creates a new spec for an {@code Instance} that uses the supplied factory to create
     * the test fixture.
     *
     * @param <T> the type of the test fixture
     * @param name the action name; must not be {@code null} or blank
     * @param factory the supplier that creates the test fixture; must not be {@code null}
     * @return a new spec
     * @throws NullPointerException if {@code name} or {@code factory} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static <T> Spec<T> of(final String name, final Supplier<? extends T> factory) {
        Objects.requireNonNull(name, "name is null");
        Arguments.requireNonBlank(name, "name is blank");
        Objects.requireNonNull(factory, "factory is null");
        return new Spec<>(name, factory);
    }

    /**
     * Creates a new spec for an {@code Instance} that uses the class's simple name as the
     * action name and its default constructor as the factory.
     *
     * @param <T> the type of the test fixture
     * @param type the class of the test fixture; must have a public no-argument constructor
     * @return a new spec
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws IllegalArgumentException if {@code type} does not have a public no-argument constructor
     */
    public static <T> Spec<T> of(final Class<T> type) {
        return of(type.getSimpleName(), type);
    }

    /**
     * Creates a new spec for an {@code Instance} with the given name that uses the class's
     * default constructor as the factory.
     *
     * @param <T> the type of the test fixture
     * @param name the action name; must not be {@code null} or blank
     * @param type the class of the test fixture; must have a public no-argument constructor
     * @return a new spec
     * @throws NullPointerException if {@code name} or {@code type} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code type} does not have a
     *     public no-argument constructor
     */
    public static <T> Spec<T> of(final String name, final Class<T> type) {
        Objects.requireNonNull(name, "name is null");
        Arguments.requireNonBlank(name, "name is blank");
        Objects.requireNonNull(type, "type is null");
        return new Spec<>(name, defaultConstructorSupplier(type));
    }

    private static <T> Supplier<T> defaultConstructorSupplier(final Class<T> type) {
        try {
            var constructor = type.getConstructor();
            return () -> {
                try {
                    return constructor.newInstance();
                } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException("Failed to instantiate " + type.getName(), e);
                }
            };
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Class " + type.getName() + " does not have a public no-argument constructor", e);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Optional<Action<?>> before() {
        return Optional.of(instantiate);
    }

    @Override
    public List<Action<?>> children() {
        return children;
    }

    @Override
    public Optional<Action<?>> after() {
        return Optional.of(destroy);
    }

    /**
     * Returns whether body children are dependent — i.e., a failure in one child causes
     * remaining children to be skipped.
     *
     * @return {@code true} if body children are dependent
     */
    public boolean isDependent() {
        return dependent;
    }

    /**
     * Returns whether body children are independent — i.e., all run regardless of
     * individual outcomes.
     *
     * @return {@code true} if body children are independent
     */
    public boolean isIndependent() {
        return !dependent;
    }

    @Override
    public void execute(Context context) {
        Objects.requireNonNull(context, "context is null");
        context = context instanceof ConcreteContext concrete
                ? concrete.withInstanceHolder(new InstanceHolder())
                : context;
        var descriptor = context.descriptor();
        var listener = context.listener();
        listener.onBeforeExecution(descriptor);
        context.setStatus(Status.RUNNING);
        try {
            var mode = descriptor.metadata().mode();
            if (mode != Mode.RUN) {
                runChildren(context, mode);
                context.setStatus(mode.toStatus());
            } else {
                context.setStatus(run(context));
            }
        } catch (Throwable t) {
            context.setStatus(Status.fromThrowable(FrameworkException.wrap(t)));
        }
        listener.onAfterExecution(descriptor);
    }

    private Status run(final Context context) {
        var descriptor = context.descriptor();
        var aggregated = new StatusAccumulator();
        var mode = Mode.RUN;

        try {
            var beforeDescriptor = descriptor.before().orElse(null);
            if (beforeDescriptor != null) {
                var instantiateResult = runChild(context, beforeDescriptor, Mode.RUN);
                aggregated.include(instantiateResult);
                var instantiateStatus = instantiateResult.metadata().status();
                if (!instantiateStatus.isPassed()) {
                    mode = Mode.SKIP;
                }
            }

            for (var child : descriptor.children()) {
                var childResult = runChild(context, child, mode);
                aggregated.include(childResult);

                if (mode != Mode.RUN) {
                    continue;
                }

                var childStatus = childResult.metadata().status();
                if (dependent && !childStatus.isPassed() && !childStatus.isAborted()) {
                    mode = Mode.fromStatus(childStatus);
                }
            }
        } finally {
            descriptor
                    .after()
                    .ifPresent(afterDescriptor -> aggregated.include(runChild(context, afterDescriptor, Mode.RUN)));
        }

        return aggregated.status();
    }

    /**
     * Fluent spec for {@link Instance} actions.
     *
     * @param <T> the type of the test fixture
     */
    public static final class Spec<T> implements org.paramixel.api.action.Spec<T> {

        private final String name;
        private final Supplier<? extends T> factory;

        private final List<Action<?>> children = new ArrayList<>();
        private boolean dependent = true;
        private boolean resolved;

        private Spec(final String name, final Supplier<? extends T> factory) {
            this.name = name;
            this.factory = factory;
        }

        /**
         * Configures the instance as dependent, so that a failure in any body child causes
         * remaining body children to be skipped. This is the default.
         *
         * @return this spec
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> dependent() {
            ensureNotResolved();
            dependent = true;
            return this;
        }

        /**
         * Configures the instance as independent, so that all body children run regardless
         * of individual outcomes.
         *
         * @return this spec
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> independent() {
            ensureNotResolved();
            dependent = false;
            return this;
        }

        /**
         * Adds a body action that invokes the supplied consumer on the test fixture instance.
         *
         * @param name the action name; must not be {@code null} or blank
         * @param consumer the consumer to invoke on the instance; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name} or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} is blank
         * @throws IllegalStateException if this spec has already been resolved
         * <p>The consumer receives the fixture instance of type {@code T}.
         */
        public Spec<T> child(final String name, final ThrowingConsumer<T> consumer) {
            ensureNotResolved();
            Objects.requireNonNull(name, "name is null");
            Arguments.requireNonBlank(name, "name is blank");
            Objects.requireNonNull(consumer, "consumer is null");
            children.add(Step.of(name, consumer));
            return this;
        }

        /**
         * Adds a body action with a custom kind that invokes the supplied consumer on
         * the test fixture instance.
         *
         * @param name the action name; must not be {@code null} or blank
         * @param kind the action kind; must not be {@code null} or blank
         * @param consumer the consumer to invoke on the instance; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name}, {@code kind}, or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} or {@code kind} is blank
         * @throws IllegalStateException if this spec has already been resolved
         * <p>The consumer receives the fixture instance of type {@code T}.
         */
        public Spec<T> child(final String name, final String kind, final ThrowingConsumer<T> consumer) {
            ensureNotResolved();
            Objects.requireNonNull(name, "name is null");
            Arguments.requireNonBlank(name, "name is blank");
            Objects.requireNonNull(kind, "kind is null");
            Arguments.requireNonBlank(kind, "kind is blank");
            Objects.requireNonNull(consumer, "consumer is null");
            children.add(Step.of(name, kind, consumer));
            return this;
        }

        /**
         * Adds a body action resolved from the supplied spec.
         *
         * @param spec the spec for the child action; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code spec} is {@code null}
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> child(final org.paramixel.api.action.Spec<?> spec) {
            ensureNotResolved();
            children.add(Objects.requireNonNull(spec, "spec is null").resolve());
            return this;
        }

        /**
         * Adds a body action for each item in the iterable by applying the supplied mapper function.
         *
         * <p>This is a convenience method that produces the same tree as calling
         * {@link #child(org.paramixel.api.action.Spec) child(Spec)} in a for-loop. The mapper is called for
         * each item at spec-building time ({@link #resolve()}), not at execution time. An empty
         * iterable adds no children.</p>
         *
         * @param <U> the type of items in the iterable
         * @param items the items to iterate over; must not be {@code null}
         * @param mapper the function that maps each item to a child action spec; must not be
         *     {@code null}
         * @return this spec
         * @throws NullPointerException if {@code items} or {@code mapper} is {@code null}
         * @throws IllegalStateException if this spec has already been resolved
         */
        public <U> Spec<T> each(final Iterable<U> items, final Function<U, org.paramixel.api.action.Spec<?>> mapper) {
            ensureNotResolved();
            Objects.requireNonNull(items, "items is null");
            Objects.requireNonNull(mapper, "mapper is null");
            for (U item : items) {
                child(mapper.apply(item));
            }
            return this;
        }

        /**
         * Adds a body action for each item in the stream by applying the supplied mapper function.
         *
         * <p>The stream is materialized to a list immediately and then delegated to
         * {@link #each(Iterable, Function)}. The mapper is called for each item at spec-building time
         * ({@link #resolve()}), not at execution time. An empty stream adds no children.</p>
         *
         * @param <U> the type of items in the stream
         * @param items the items to iterate over; must not be {@code null}
         * @param mapper the function that maps each item to a child action spec; must not be
         *     {@code null}
         * @return this spec
         * @throws NullPointerException if {@code items} or {@code mapper} is {@code null}
         * @throws IllegalStateException if this spec has already been resolved
         */
        public <U> Spec<T> each(final Stream<U> items, final Function<U, org.paramixel.api.action.Spec<?>> mapper) {
            Objects.requireNonNull(items, "items is null");
            return each(items.toList(), mapper);
        }

        /**
         * Builds the instance action.
         *
         * @return a new instance action
         */
        public Instance<T> resolve() {
            ensureNotResolved();
            resolved = true;
            return new Instance<>(name, new Instantiate(factory), List.copyOf(children), new Destroy(), dependent);
        }

        private void ensureNotResolved() {
            if (resolved) {
                throw new IllegalStateException("spec already resolved");
            }
        }
    }

    private static final class Instantiate implements Action<Void> {

        private static final String NAME = "Instantiate";
        private static final String KIND = "Instantiate";
        private final Supplier<?> factory;

        private Instantiate(final Supplier<?> factory) {
            this.factory = Objects.requireNonNull(factory, "factory is null");
        }

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public String kind() {
            return KIND;
        }

        @Override
        public void execute(final Context context) {
            var descriptor = context.descriptor();
            var listener = context.listener();
            listener.onBeforeExecution(descriptor);
            context.setStatus(Status.RUNNING);
            try {
                var mode = descriptor.metadata().mode();
                if (mode != Mode.RUN) {
                    context.setStatus(mode.toStatus());
                } else {
                    var instance = factory.get();
                    if (context instanceof ConcreteContext concrete) {
                        concrete.instanceHolder().set(instance);
                    }
                    context.setStatus(Status.PASSED);
                }
            } catch (Throwable t) {
                context.setStatus(Status.fromThrowable(FrameworkException.wrap(t)));
            }
            listener.onAfterExecution(descriptor);
        }
    }

    private static final class Destroy implements Action<Void> {

        private static final String NAME = "Destroy";
        private static final String KIND = "Destroy";

        private Destroy() {
            // Intentionally empty
        }

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public String kind() {
            return KIND;
        }

        @Override
        public void execute(final Context context) {
            var descriptor = context.descriptor();
            var listener = context.listener();
            listener.onBeforeExecution(descriptor);
            context.setStatus(Status.RUNNING);
            try {
                var mode = descriptor.metadata().mode();
                if (mode != Mode.RUN) {
                    context.setStatus(mode.toStatus());
                } else {
                    try {
                        var instanceOpt = context.instance(AutoCloseable.class);
                        if (instanceOpt.isPresent()) {
                            instanceOpt.get().close();
                        }
                        context.setStatus(Status.PASSED);
                    } finally {
                        if (context instanceof ConcreteContext concrete) {
                            concrete.instanceHolder().clear();
                        }
                    }
                }
            } catch (Throwable t) {
                context.setStatus(Status.fromThrowable(FrameworkException.wrap(t)));
            }
            listener.onAfterExecution(descriptor);
        }
    }

    private static Descriptor runChild(final Context context, final Descriptor child, final Mode mode) {
        if (context instanceof ConcreteContext concrete) {
            return concrete.runChild(child, mode);
        }
        throw new IllegalArgumentException("context must be a ConcreteContext");
    }

    private static void runChildren(final Context context, final Mode mode) {
        if (context instanceof ConcreteContext concrete) {
            concrete.runChildren(mode);
        } else {
            throw new IllegalArgumentException("context must be a ConcreteContext");
        }
    }
}

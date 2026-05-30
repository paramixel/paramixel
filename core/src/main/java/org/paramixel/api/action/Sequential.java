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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import nonapi.org.paramixel.FrameworkException;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.StatusAccumulator;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.ThrowingConsumer;

/**
 * An action that executes its children sequentially in the order they were added.
 *
 * <p>When configured as <em>dependent</em> (the default), a failure in any child causes
 * remaining children to be skipped or aborted. When configured as <em>independent</em>,
 * all children run regardless of individual outcomes.
 *
 * @param <T> the type accepted by child consumers
 */
public final class Sequential<T> implements Action<T> {

    private static final String KIND = "Sequential";

    private final String name;
    private final List<Action<?>> children;
    private final boolean dependent;

    private Sequential(final String name, final List<Action<?>> children, final boolean dependent) {
        Objects.requireNonNull(name, "name is null");
        this.name = Arguments.requireNonBlank(name, "name is blank");
        this.children = validateChildren(children);
        this.dependent = dependent;
    }

    /**
     * Creates a new spec for a {@code Sequential} action with the given name.
     *
     * @param <T> the type accepted by child consumers
     * @param name the action name; must not be {@code null} or blank
     * @return a new spec
     */
    public static <T> Spec<T> of(final String name) {
        return new Spec<>(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String kind() {
        return KIND;
    }

    /**
     * Returns whether children are dependent — i.e., a failure in one child causes
     * remaining children to be skipped or aborted.
     *
     * @return {@code true} if children are dependent
     */
    public boolean isDependent() {
        return dependent;
    }

    /**
     * Returns whether children are independent — i.e., all run regardless of
     * individual outcomes.
     *
     * @return {@code true} if children are independent
     */
    public boolean isIndependent() {
        return !dependent;
    }

    @Override
    public List<Action<?>> children() {
        return children;
    }

    @Override
    public void execute(final Context context) {
        Objects.requireNonNull(context, "context is null");
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
        var descriptors = context.descriptor().children();
        var aggregated = new StatusAccumulator();
        var mode = Mode.RUN;

        for (var descriptor : descriptors) {
            var childResult = runChild(context, descriptor, mode);
            aggregated.include(childResult);

            if (mode != Mode.RUN) {
                continue;
            }

            var childStatus = childResult.metadata().status();
            if (dependent && !childStatus.isPassed() && !childStatus.isAborted()) {
                mode = Mode.fromStatus(childStatus);
            }
        }

        return aggregated.status();
    }

    private List<Action<?>> validateChildren(final List<Action<?>> children) {
        Objects.requireNonNull(children, "children is null");
        var validated = new ArrayList<Action<?>>(children.size());
        for (Action<?> child : children) {
            Objects.requireNonNull(child, "children contains null element");
            Arguments.requireTrue(child != this, "action must not add itself as a child");
            validated.add(child);
        }
        return List.copyOf(validated);
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

    /**
     * Fluent spec for {@link Sequential} actions.
     *
     * @param <T> the type accepted by child consumers
     */
    public static final class Spec<T> implements org.paramixel.api.action.Spec<T> {

        private final String name;
        private final List<Action<?>> children = new ArrayList<>();
        private boolean dependent = true;
        private boolean resolved;

        private Spec(final String name) {
            Objects.requireNonNull(name, "name is null");
            Arguments.requireNonBlank(name, "name is blank");
            this.name = name;
        }

        /**
         * Configures the sequential action as dependent, so that a failure in any child
         * causes remaining children to be skipped or aborted. This is the default.
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
         * Configures the sequential action as independent, so that all children run
         * regardless of individual outcomes.
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
         * Adds a child action resolved from the supplied spec.
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
         * Adds a child action that invokes the supplied consumer.
         *
         * @param name the action name; must not be {@code null} or blank
         * @param consumer the consumer to invoke; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name} or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} is blank
         * @throws IllegalStateException if this spec has already been resolved
         * <p>The consumer receives the fixture instance when this action is wrapped in an
         * {@link Instance}, or the execution {@link Context} when standalone.
         */
        public Spec<T> child(final String name, final ThrowingConsumer<? super T> consumer) {
            return child(Step.of(name, consumer));
        }

        /**
         * Adds a child action with a custom kind that invokes the supplied consumer.
         *
         * @param name the action name; must not be {@code null} or blank
         * @param kind the action kind; must not be {@code null} or blank
         * @param consumer the consumer to invoke; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name}, {@code kind}, or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} or {@code kind} is blank
         * @throws IllegalStateException if this spec has already been resolved
         * <p>The consumer receives the fixture instance when this action is wrapped in an
         * {@link Instance}, or the execution {@link Context} when standalone.
         */
        public Spec<T> child(final String name, final String kind, final ThrowingConsumer<? super T> consumer) {
            return child(Step.of(name, kind, consumer));
        }

        /**
         * Adds a child action for each item in the iterable by applying the supplied mapper function.
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
         * Adds a child action for each item in the stream by applying the supplied mapper function.
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
         * Builds the sequential action.
         *
         * @return a new sequential action
         */
        public Sequential<T> resolve() {
            ensureNotResolved();
            resolved = true;
            return new Sequential<>(name, List.copyOf(children), dependent);
        }

        private void ensureNotResolved() {
            if (resolved) {
                throw new IllegalStateException("spec already resolved");
            }
        }
    }
}

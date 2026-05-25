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
import java.util.Optional;
import org.paramixel.api.Status;
import org.paramixel.api.ThrowingConsumer;
import org.paramixel.api.internal.ConcreteExecutionContext;
import org.paramixel.api.internal.action.StatusAccumulator;
import org.paramixel.api.internal.support.Arguments;
import org.paramixel.spi.action.ExecutionContext;
import org.paramixel.spi.action.Mode;

/**
 * An action that executes a before-action, zero or more body children, and an after-action
 * in sequence, with lifecycle-style guarantees.
 *
 * <p>When configured as <em>dependent</em> (the default), a failure in any body child
 * causes remaining body children to be skipped or aborted. When configured as
 * <em>independent</em>, all body children run regardless of individual outcomes. The
 * after-action always runs, regardless of earlier outcomes.
 *
 * @param <T> the type accepted by before and after consumers
 */
public final class Lifecycle<T> implements Action<T> {

    private static final String KIND = "Lifecycle";

    private final String name;
    private final Action<?> before;
    private final List<Action<?>> children;
    private final Action<?> after;
    private final boolean dependent;

    private Lifecycle(
            final String name,
            final Action<?> before,
            final List<Action<?>> children,
            final Action<?> after,
            final boolean dependent) {
        this.name = Arguments.requireValidName(name);
        this.before = before;
        this.children = List.copyOf(children);
        this.after = after;
        this.dependent = dependent;
    }

    /**
     * Creates a new spec for a {@code Lifecycle} with the given name.
     *
     * @param <T> the type accepted by before and after consumers
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

    @Override
    public Optional<Action<?>> before() {
        return Optional.ofNullable(before);
    }

    @Override
    public List<Action<?>> children() {
        return children;
    }

    @Override
    public Optional<Action<?>> after() {
        return Optional.ofNullable(after);
    }

    /**
     * Returns whether body children are dependent — i.e., a failure in one child causes
     * remaining children to be skipped or aborted.
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
    public void execute(final ExecutionContext context) {
        Objects.requireNonNull(context, "context must not be null");
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
            context.setStatus(Status.fromThrowable(t));
        }
        listener.onAfterExecution(descriptor);
    }

    private Status run(final ExecutionContext context) {
        var descriptors = context.descriptor().children();
        var aggregated = new StatusAccumulator();
        var index = 0;

        if (before != null) {
            Descriptor beforeResult = runChild(context, descriptors.get(index++), Mode.RUN);
            aggregated.include(beforeResult);
            if (!beforeResult.metadata().status().isPassed()) {
                Mode propagateMode = Mode.fromStatus(beforeResult.metadata().status());
                for (var ignored : children) {
                    aggregated.include(runChild(context, descriptors.get(index++), propagateMode));
                }
                runAfter(context, descriptors, aggregated, index);
                return aggregated.status();
            }
        }

        for (int body = 0; body < children.size(); body++) {
            Descriptor childResult = runChild(context, descriptors.get(index++), Mode.RUN);
            aggregated.include(childResult);
            var childStatus = childResult.metadata().status();
            if (dependent && !childStatus.isPassed()) {
                Mode propagateMode = Mode.fromStatus(childStatus);
                for (int remaining = body + 1; remaining < children.size(); remaining++) {
                    aggregated.include(runChild(context, descriptors.get(index++), propagateMode));
                }
                break;
            }
        }

        runAfter(context, descriptors, aggregated, index);
        return aggregated.status();
    }

    private void runAfter(
            final ExecutionContext context,
            final List<Descriptor> descriptors,
            final StatusAccumulator aggregated,
            final int index) {
        if (after != null) {
            aggregated.include(runChild(context, descriptors.get(index), Mode.RUN));
        }
    }

    private static Descriptor runChild(final ExecutionContext context, final Descriptor child, final Mode mode) {
        if (context instanceof ConcreteExecutionContext concrete) {
            return concrete.runChild(child, mode);
        }
        throw new IllegalArgumentException("context must be a ConcreteExecutionContext");
    }

    private static void runChildren(final ExecutionContext context, final Mode mode) {
        if (context instanceof ConcreteExecutionContext concrete) {
            concrete.runChildren(mode);
        } else {
            throw new IllegalArgumentException("context must be a ConcreteExecutionContext");
        }
    }

    /**
     * Fluent spec for {@link Lifecycle} actions.
     *
     * @param <T> the type accepted by before and after consumers
     */
    public static final class Spec<T> implements org.paramixel.api.action.Spec<T> {

        private final String name;
        private Action<?> before;
        private final List<Action<?>> children = new ArrayList<>();
        private Action<?> after;
        private boolean dependent = true;
        private boolean resolved;

        private Spec(final String name) {
            Objects.requireNonNull(name, "name must not be null");
            Arguments.requireNonBlank(name, "name must not be blank");
            this.name = name;
        }

        /**
         * Configures the lifecycle as dependent, so that a failure in any body child causes
         * remaining body children to be skipped or aborted. This is the default.
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
         * Configures the lifecycle as independent, so that all body children run regardless
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
         * Adds a before-action resolved from the supplied spec. Calling this method
         * again overwrites the previous before-action.
         *
         * @param spec the spec for the before-action; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code spec} is {@code null}
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> before(final org.paramixel.api.action.Spec<?> spec) {
            ensureNotResolved();
            this.before = Objects.requireNonNull(spec, "spec must not be null").resolve();
            return this;
        }

        /**
         * Adds a before-action that invokes the supplied consumer. Calling this method
         * again overwrites the previous before-action.
         *
         * @param name the action name; must not be {@code null} or blank
         * @param consumer the consumer to invoke; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name} or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} is blank
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> before(final String name, final ThrowingConsumer<? super T> consumer) {
            return before(Step.of(name, consumer));
        }

        /**
         * Adds a before-action with a custom kind that invokes the supplied consumer.
         * Calling this method again overwrites the previous before-action.
         *
         * @param name the action name; must not be {@code null} or blank
         * @param kind the action kind; must not be {@code null} or blank
         * @param consumer the consumer to invoke; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name}, {@code kind}, or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} or {@code kind} is blank
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> before(final String name, final String kind, final ThrowingConsumer<? super T> consumer) {
            return before(Step.of(name, kind, consumer));
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
            children.add(Objects.requireNonNull(spec, "spec must not be null").resolve());
            return this;
        }

        /**
         * Adds a body action that invokes the supplied consumer.
         *
         * @param name the action name; must not be {@code null} or blank
         * @param consumer the consumer to invoke; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name} or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} is blank
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> child(final String name, final ThrowingConsumer<? super T> consumer) {
            return child(Step.of(name, consumer));
        }

        /**
         * Adds a body action with a custom kind that invokes the supplied consumer.
         *
         * @param name the action name; must not be {@code null} or blank
         * @param kind the action kind; must not be {@code null} or blank
         * @param consumer the consumer to invoke; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name}, {@code kind}, or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} or {@code kind} is blank
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> child(final String name, final String kind, final ThrowingConsumer<? super T> consumer) {
            return child(Step.of(name, kind, consumer));
        }

        /**
         * Adds an after-action resolved from the supplied spec. Calling this method
         * again overwrites the previous after-action.
         *
         * @param spec the spec for the after-action; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code spec} is {@code null}
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> after(final org.paramixel.api.action.Spec<?> spec) {
            ensureNotResolved();
            this.after = Objects.requireNonNull(spec, "spec must not be null").resolve();
            return this;
        }

        /**
         * Adds an after-action that invokes the supplied consumer. Calling this method
         * again overwrites the previous after-action.
         *
         * @param name the action name; must not be {@code null} or blank
         * @param consumer the consumer to invoke; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name} or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} is blank
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> after(final String name, final ThrowingConsumer<? super T> consumer) {
            return after(Step.of(name, consumer));
        }

        /**
         * Adds an after-action with a custom kind that invokes the supplied consumer.
         * Calling this method again overwrites the previous after-action.
         *
         * @param name the action name; must not be {@code null} or blank
         * @param kind the action kind; must not be {@code null} or blank
         * @param consumer the consumer to invoke; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name}, {@code kind}, or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} or {@code kind} is blank
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> after(final String name, final String kind, final ThrowingConsumer<? super T> consumer) {
            return after(Step.of(name, kind, consumer));
        }

        /**
         * Builds the lifecycle action.
         *
         * @return a new lifecycle action
         */
        public Lifecycle<T> resolve() {
            ensureNotResolved();
            resolved = true;
            return new Lifecycle<>(name, before, List.copyOf(children), after, dependent);
        }

        private void ensureNotResolved() {
            if (resolved) {
                throw new IllegalStateException("spec already resolved");
            }
        }
    }
}

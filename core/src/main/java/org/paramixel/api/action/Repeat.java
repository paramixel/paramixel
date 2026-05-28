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
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.StatusAccumulator;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.ThrowingConsumer;

/**
 * An action that executes a single child action a configurable number of times.
 *
 * <p>The child action is discovered once per repetition, producing distinct
 * descriptor occurrences for each repetition. When configured as
 * <em>dependent</em> (the default), a failure in any repetition causes
 * remaining repetitions to be skipped or aborted. When configured as
 * <em>independent</em>, all repetitions run regardless of individual outcomes.
 *
 * @param <T> the type accepted by child consumers
 */
public final class Repeat<T> implements Action<T> {

    private static final String KIND = "Repeat";

    private final String name;
    private final Action<?> child;
    private final int repeatCount;
    private final List<Action<?>> childrenList;
    private final boolean dependent;

    private Repeat(final String name, final Action<?> child, final int repeatCount, final boolean dependent) {
        Objects.requireNonNull(name, "name is null");
        this.name = Arguments.requireNonBlank(name, "name is blank");
        this.child = Objects.requireNonNull(child, "child is null");
        Arguments.requireTrue(child != this, "action must not add itself as a child");
        this.repeatCount = validateRepeatCount(repeatCount);
        var list = new ArrayList<Action<?>>(this.repeatCount);
        for (int i = 0; i < this.repeatCount; i++) {
            list.add(child);
        }
        this.childrenList = List.copyOf(list);
        this.dependent = dependent;
    }

    /**
     * Creates a new spec for a {@code Repeat} action with the given name.
     *
     * @param <T> the type accepted by child consumers
     * @param name the action name; must not be {@code null} or blank
     * @return a new spec
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static <T> Spec<T> of(final String name) {
        Objects.requireNonNull(name, "name is null");
        Arguments.requireNonBlank(name, "name is blank");
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
     * Returns the child action being repeated.
     *
     * @return the child action; never {@code null}
     */
    public Action<?> child() {
        return child;
    }

    /**
     * Returns the number of repetitions.
     *
     * @return the repeat count
     */
    public int repeatCount() {
        return repeatCount;
    }

    /**
     * Returns whether repetitions are dependent — i.e., a failure in one repetition
     * causes remaining repetitions to be skipped or aborted.
     *
     * @return {@code true} if repetitions are dependent
     */
    public boolean isDependent() {
        return dependent;
    }

    /**
     * Returns whether repetitions are independent — i.e., all run regardless of
     * individual outcomes.
     *
     * @return {@code true} if repetitions are independent
     */
    public boolean isIndependent() {
        return !dependent;
    }

    @Override
    public List<Action<?>> children() {
        return childrenList;
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
            context.setStatus(Status.fromThrowable(t));
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

    private static int validateRepeatCount(final int repeatCount) {
        Arguments.requireTrue(repeatCount > 0, "repeatCount must be positive, was: " + repeatCount);
        return repeatCount;
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
     * Fluent spec for {@link Repeat} actions.
     *
     * @param <T> the type accepted by child consumers
     */
    public static final class Spec<T> implements org.paramixel.api.action.Spec<T> {

        private final String name;
        private Action<?> child;
        private int repeatCount = 1;
        private boolean dependent = true;
        private boolean resolved;

        private Spec(final String name) {
            this.name = name;
        }

        /**
         * Configures the repeat action as dependent, so that a failure in any
         * repetition causes remaining repetitions to be skipped or aborted.
         * This is the default.
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
         * Configures the repeat action as independent, so that all repetitions
         * run regardless of individual outcomes.
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
         * Sets the number of repetitions.
         *
         * @param repeatCount the number of times to repeat the child; must be positive
         * @return this spec
         * @throws IllegalArgumentException if {@code repeatCount} is not positive
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> count(final int repeatCount) {
            ensureNotResolved();
            Arguments.requireTrue(repeatCount > 0, "repeatCount must be positive, was: " + repeatCount);
            this.repeatCount = repeatCount;
            return this;
        }

        /**
         * Sets the child action resolved from the supplied spec. Calling this method
         * again overwrites the previous child.
         *
         * @param spec the spec for the child action; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code spec} is {@code null}
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec<T> child(final org.paramixel.api.action.Spec<?> spec) {
            ensureNotResolved();
            this.child = Objects.requireNonNull(spec, "spec is null").resolve();
            return this;
        }

        /**
         * Sets a child action that invokes the supplied consumer. Calling this method
         * again overwrites the previous child.
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
         * Sets a child action with a custom kind that invokes the supplied consumer.
         * Calling this method again overwrites the previous child.
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
         * Builds the repeat action.
         *
         * @return a new repeat action
         * @throws IllegalStateException if this spec has already been resolved or no child is configured
         */
        public Repeat<T> resolve() {
            ensureNotResolved();
            if (child == null) {
                throw new IllegalStateException("repeat must contain a child action");
            }
            resolved = true;
            return new Repeat<>(name, child, repeatCount, dependent);
        }

        private void ensureNotResolved() {
            if (resolved) {
                throw new IllegalStateException("spec already resolved");
            }
        }
    }
}

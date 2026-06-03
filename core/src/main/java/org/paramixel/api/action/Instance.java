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
import java.util.Objects;
import java.util.function.Supplier;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Context;

/**
 * An action that manages the lifecycle of a test fixture instance, including its creation,
 * a body action, and destruction.
 *
 * <p>The instance is created by a {@link Supplier} at the beginning of execution and is
 * available to all child actions via {@link Context#instance(Class)}. If the
 * instance implements {@link AutoCloseable}, it is closed in the after phase.
 *
 * <p>To execute multiple body actions, wrap a {@link Sequence} or {@link Parallel}
 * in {@link Builder#body(Action)}.
 */
public final class Instance implements Action {

    private final String displayName;
    private final Action instantiate;
    private final Action body;
    private final Action destroy;

    private Instance(final String displayName, final Action instantiate, final Action body, final Action destroy) {
        Objects.requireNonNull(displayName, "displayName is null");
        this.displayName = Arguments.requireNonBlank(displayName, "displayName is blank");
        this.instantiate = Objects.requireNonNull(instantiate);
        this.body = Objects.requireNonNull(body, "body is null");
        this.destroy = Objects.requireNonNull(destroy);
    }

    /**
     * Creates a new builder for an {@code Instance} that uses the supplied factory to create
     * the test fixture.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @param factory the supplier that creates the test fixture; must not be {@code null} and must not return
     *     {@code null}
     * @return a new builder
     * @throws NullPointerException if {@code displayName} or {@code factory} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static Builder builder(final String displayName, final Supplier<?> factory) {
        Objects.requireNonNull(displayName, "displayName is null");
        Arguments.requireNonBlank(displayName, "displayName is blank");
        Objects.requireNonNull(factory, "factory is null");
        return new Builder(displayName, factory);
    }

    /**
     * Creates a new builder for an {@code Instance} that uses the class's simple name as the
     * action display name and its default constructor as the factory.
     *
     * @param type the class of the test fixture; must have a public no-argument constructor
     * @return a new builder
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws IllegalArgumentException if {@code type} does not have a public no-argument constructor
     */
    public static Builder builder(final Class<?> type) {
        return builder(type.getSimpleName(), type);
    }

    /**
     * Creates a new builder for an {@code Instance} with the given display name that uses the class's
     * default constructor as the factory.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @param type the class of the test fixture; must have a public no-argument constructor
     * @return a new builder
     * @throws NullPointerException if {@code displayName} or {@code type} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank or {@code type} does not have a
     *     public no-argument constructor
     */
    public static Builder builder(final String displayName, final Class<?> type) {
        Objects.requireNonNull(displayName, "displayName is null");
        Arguments.requireNonBlank(displayName, "displayName is blank");
        Objects.requireNonNull(type, "type is null");
        return new Builder(displayName, defaultConstructorSupplier(type));
    }

    private static Supplier<?> defaultConstructorSupplier(final Class<?> type) {
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
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the synthetic instantiation action.
     *
     * @return the instantiation action
     */
    public Action instantiate() {
        return instantiate;
    }

    /**
     * Returns the body child action.
     *
     * @return the body child action; never {@code null}
     */
    public Action body() {
        return body;
    }

    /**
     * Returns the synthetic destroy action.
     *
     * @return the destroy action
     */
    public Action destroy() {
        return destroy;
    }

    /**
     * Fluent builder for {@link Instance} actions.
     */
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final String displayName;
        private final Supplier<?> factory;

        private Action body;

        private Builder(final String displayName, final Supplier<?> factory) {
            this.displayName = displayName;
            this.factory = factory;
        }

        /**
         * Sets the body action. Calling this method
         * again overwrites the previous body action.
         *
         * @param action the body action; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder body(final Action action) {
            this.body = Objects.requireNonNull(action, "action is null");
            return this;
        }

        /**
         * Sets the body action from a builder. The builder is built immediately
         * and the resulting action snapshot is stored.
         *
         * @param builder the body action builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code builder} is {@code null}
         */
        public Builder body(final org.paramixel.api.action.Builder builder) {
            Objects.requireNonNull(builder, "builder is null");
            this.body = Objects.requireNonNull(builder.build(), "builder.build() returned null");
            return this;
        }

        /**
         * Builds the instance action.
         *
         * @return a new instance action
         */
        @Override
        public Instance build() {
            if (body == null) {
                throw new IllegalStateException("body action must be configured");
            }
            var instantiate = Step.of("[instantiate]", context -> {
                var instance = Objects.requireNonNull(factory.get(), "factory returned null");
                ConcreteContext.require(context).instanceHolder().set(instance);
            });
            var destroy = Step.of("[destroy]", context -> {
                try {
                    var instanceOpt = context.instance(AutoCloseable.class);
                    if (instanceOpt.isPresent()) {
                        instanceOpt.get().close();
                    }
                } finally {
                    ConcreteContext.require(context).instanceHolder().clear();
                }
            });
            return new Instance(displayName, instantiate, body, destroy);
        }
    }
}

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
import nonapi.org.paramixel.action.FixtureConsumer;
import nonapi.org.paramixel.action.FixtureCreator;
import nonapi.org.paramixel.action.FixtureDestroyer;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.InstanceConsumer;

/**
 * A typed fixture: one object created for one subtree and shared by every step in it.
 *
 * <p>{@code Instance} is not an action type of its own. {@link Builder#build()} returns an ordinary {@link Scope}
 * whose {@code before} creates the fixture, binds it under its declared type, and runs the optional
 * {@link Builder#before(String, InstanceConsumer) before} step, and whose {@code after} runs the optional
 * {@link Builder#after(String, InstanceConsumer) after} step and closes the fixture.
 *
 * <pre>{@code
 * Instance.of("metrics", MetricsTest.class, () -> new MetricsTest(environment))
 *         .before("setUp()", MetricsTest::setUp)
 *         .body(sequential("tests")
 *                 .child(step("testHealthy()", MetricsTest.class, MetricsTest::testHealthy))
 *                 .child(step("testMetrics()", MetricsTest.class, MetricsTest::testMetrics)))
 *         .after("tearDown()", MetricsTest::tearDown);
 * }</pre>
 *
 * <p><strong>Creation is lazy and node-local.</strong> The factory runs inside the node's own {@code before} step,
 * on the thread executing that node, and nowhere else: defining, validating and compiling the tree never call it.
 * A node that is skipped (dependent sibling failed, {@code Conditional} false, fail-fast), aborted, or cancelled by
 * an enclosing {@link Timeout} before it starts never calls the factory, and its {@code after} does not run. Once
 * creation has been attempted, {@code after} always runs and releases whatever the fixture holds; a failed
 * creation binds nothing, so the destroy step is a no-op.
 *
 * <p><strong>Declared type.</strong> The fixture is bound under the {@code type} given to {@link #of} and under its
 * runtime class, and every typed step in the subtree resolves to it by declared type. A typed step whose type no
 * enclosing {@code Instance} provides is rejected before any user code runs, as is a level that declares two
 * fixtures of the same type.
 *
 * <p><strong>Bounding live fixtures</strong> uses the existing pool model: a fixture is created under whichever
 * {@link Parallel} admitted its branch, so that pool already bounds it. Name the pool for the resource it limits,
 * for example {@code Parallel.parallel("Metrics", "docker").each(environments, env -> Instance.of(...))}, and note
 * that heavy fixtures at two nested levels multiply rather than share a budget.
 *
 * <p><strong>Thread safety.</strong> All steps in one {@code Instance} share one fixture object, so running them
 * under a {@code Parallel} is safe only if the fixture is thread-safe. When each test needs its own fixture, use one
 * {@code Instance} per test inside {@code Parallel.parallel(name, pool).each(...)}.
 */
public final class Instance {

    private static final String INSTANTIATE_NAME = "[instantiate]";
    private static final String DESTROY_NAME = "[destroy]";

    private Instance() {
        // Intentionally empty
    }

    /**
     * Creates a builder for a fixture created by the supplied factory.
     *
     * @param <T> the fixture type
     * @param displayName the scope display name; must not be {@code null} or blank
     * @param type the fixture type the fixture is declared and bound as; must not be {@code null}
     * @param factory the supplier that creates the fixture; must not be {@code null} and must not return
     *     {@code null}
     * @return a new builder
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static <T> Builder<T> of(
            final String displayName, final Class<T> type, final Supplier<? extends T> factory) {
        return new Builder<>(displayName, type, factory);
    }

    /**
     * Creates a builder for a fixture created with the type's public no-argument constructor, displayed with the
     * type's simple name.
     *
     * @param <T> the fixture type
     * @param type the fixture type; must have a public no-argument constructor
     * @return a new builder
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws IllegalArgumentException if {@code type} does not have a public no-argument constructor
     */
    public static <T> Builder<T> of(final Class<T> type) {
        Objects.requireNonNull(type, "type is null");
        return of(type.getSimpleName(), type, defaultConstructorSupplier(type));
    }

    /**
     * Creates a builder for a fixture created with the type's public no-argument constructor.
     *
     * @param <T> the fixture type
     * @param displayName the scope display name; must not be {@code null} or blank
     * @param type the fixture type; must have a public no-argument constructor
     * @return a new builder
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank or {@code type} does not have a public
     *     no-argument constructor
     */
    public static <T> Builder<T> of(final String displayName, final Class<T> type) {
        Objects.requireNonNull(displayName, "displayName is null");
        Arguments.requireNonBlank(displayName, "displayName is blank");
        Objects.requireNonNull(type, "type is null");
        return of(displayName, type, defaultConstructorSupplier(type));
    }

    private static <T> Supplier<T> defaultConstructorSupplier(final Class<T> type) {
        try {
            var constructor = type.getConstructor();
            return () -> {
                try {
                    return constructor.newInstance();
                } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                    throw new IllegalStateException("Failed to instantiate " + type.getName(), e);
                }
            };
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Class " + type.getName() + " does not have a public no-argument constructor", e);
        }
    }

    /**
     * Fluent builder for a typed fixture {@link Scope}.
     *
     * @param <T> the fixture type
     */
    public static final class Builder<T> implements ActionBuilder {

        private final String displayName;
        private final Class<T> type;
        private final Supplier<? extends T> factory;

        private String beforeName;
        private InstanceConsumer<? super T> beforeConsumer;
        private InstanceConsumer<? super T> afterConsumer;
        private String afterName;
        private ActionBuilder body;

        private Builder(final String displayName, final Class<T> type, final Supplier<? extends T> factory) {
            Objects.requireNonNull(displayName, "displayName is null");
            Arguments.requireNonBlank(displayName, "displayName is blank");
            this.type = Objects.requireNonNull(type, "type is null");
            this.factory = Objects.requireNonNull(factory, "factory is null");
            this.displayName = displayName;
        }

        /**
         * Sets the lifecycle step that runs against the fixture before the body.
         *
         * <p>It runs after the fixture is created, as part of the scope's before phase, so a failure skips the body
         * and still runs the after phase. Calling this method again replaces the previous step.
         *
         * @param name the step display name; must not be {@code null} or blank
         * @param consumer the action to perform on the fixture; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if {@code name} is blank
         */
        public Builder<T> before(final String name, final InstanceConsumer<? super T> consumer) {
            this.beforeName = requireName(name);
            this.beforeConsumer = Objects.requireNonNull(consumer, "consumer is null");
            return this;
        }

        /**
         * Sets the body action. A builder argument is built immediately and the resulting action snapshot is
         * stored; an action argument is used as is. Calling this method again replaces the previous body.
         *
         * @param action the body action or builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder<T> body(final ActionBuilder action) {
            Objects.requireNonNull(action, "action is null");
            this.body = Objects.requireNonNull(action.build(), "action.build() returned null");
            return this;
        }

        /**
         * Sets the lifecycle step that runs against the fixture after the body.
         *
         * <p>It runs as part of the scope's after phase, before the fixture is closed, and only when the after phase
         * runs at all. The fixture is closed even if this step fails.
         *
         * @param name the step display name; must not be {@code null} or blank
         * @param consumer the action to perform on the fixture; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if {@code name} is blank
         */
        public Builder<T> after(final String name, final InstanceConsumer<? super T> consumer) {
            this.afterName = requireName(name);
            this.afterConsumer = Objects.requireNonNull(consumer, "consumer is null");
            return this;
        }

        /**
         * Builds the fixture scope.
         *
         * @return a {@link Scope} whose before creates and binds the fixture and whose after closes it; never
         *     {@code null}
         * @throws IllegalStateException if no body action has been configured
         */
        @Override
        public Scope build() {
            if (body == null) {
                throw new IllegalStateException("body action must be configured");
            }
            var instantiate = Step.step(INSTANTIATE_NAME, new FixtureCreator(type, factory));
            var destroy = Step.step(DESTROY_NAME, new FixtureDestroyer(type));

            var scope = Scope.scope(displayName);
            if (beforeConsumer == null) {
                scope.before(instantiate);
            } else {
                var beforeStep = Step.step(beforeName, new FixtureConsumer<>(type, beforeConsumer));
                scope.before(Sequential.sequential(beforeName + " phase")
                        .child(instantiate)
                        .child(beforeStep));
            }
            scope.body(body);
            if (afterConsumer == null) {
                scope.after(destroy);
            } else {
                var afterStep = Step.step(afterName, new FixtureConsumer<>(type, afterConsumer));
                // Independent so the fixture is closed even when the user's after step fails.
                scope.after(Sequential.sequential(afterName + " phase")
                        .independent()
                        .child(afterStep)
                        .child(destroy));
            }
            return scope.build();
        }

        private static String requireName(final String name) {
            Objects.requireNonNull(name, "name is null");
            return Arguments.requireNonBlank(name, "name is blank");
        }
    }
}

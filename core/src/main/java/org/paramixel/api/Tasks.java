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

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Static entry points for authoring task definitions.
 *
 * <p>The canonical grammatical pair is {@code sequential} and {@code parallel}; {@code task} is the default sequential
 * container. Every method returns a mutable builder and performs no work: no factory, lifecycle callback, body, or
 * runtime predicate runs until a runner executes the resulting {@link Task}.
 *
 * <pre>{@code
 * Task definition = parallel("Environments", Pool.named("environments"))
 *         .each(environments, environment -> forInstance(environment.name(), () -> new Scenario(environment))
 *                 .before(Scenario::start)
 *                 .step("Verify", Scenario::verify)
 *                 .after(Scenario::stop))
 *         .build();
 * }</pre>
 */
public final class Tasks {

    private Tasks() {
        // Intentionally empty
    }

    /**
     * Starts the default sequential container.
     *
     * @param name the task name; must not be blank
     * @return a sequential builder with no instance binding; never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static SequentialBuilder<Void> task(final String name) {
        return new SequentialBuilder<>(name);
    }

    /**
     * Starts a sequential group.
     *
     * @param name the task name; must not be blank
     * @return a sequential builder with no instance binding; never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static SequentialBuilder<Void> sequential(final String name) {
        return new SequentialBuilder<>(name);
    }

    /**
     * Starts a parallel group.
     *
     * @param name the task name; must not be blank
     * @param pool the concurrency budget of the direct children; must not be {@code null}
     * @return a parallel builder with no instance binding; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static ParallelBuilder<Void> parallel(final String name, final Pool pool) {
        return new ParallelBuilder<>(name, Objects.requireNonNull(pool, "pool is null"));
    }

    /**
     * Starts a task that owns one lazily created primary instance per execution attempt.
     *
     * <p>{@code T} is inferred from the supplier, so lifecycle callbacks and steps bind to the instance without a class
     * token: {@code forInstance("scenario", () -> new Scenario(environment)).before(Scenario::start)}. The supplier
     * runs once per attempt of this occurrence, on an execution worker, after admission.
     *
     * @param <T> the instance type
     * @param name the task name; must not be blank
     * @param factory the instance supplier; must not be {@code null}, and must not return {@code null}
     * @return a sequential builder bound to {@code T}; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static <T> SequentialBuilder<T> forInstance(final String name, final ThrowingSupplier<? extends T> factory) {
        Objects.requireNonNull(factory, "factory is null");
        final var builder = new SequentialBuilder<T>(name);
        builder.setInstanceFactory(factory::get);
        return builder;
    }

    /**
     * Starts a bounded repetition task with no instance binding.
     *
     * @param name the task name; must not be blank
     * @return a repeat builder; configure {@code maxIterations} before building; never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static RepeatBuilder<Void> repeat(final String name) {
        return new RepeatBuilder<>(name);
    }

    /**
     * Starts a conditional sequential group with no instance binding.
     *
     * @param name the task name; must not be blank
     * @param condition the runtime condition; must not be {@code null}
     * @return a sequential builder gated by {@code condition}; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static SequentialBuilder<Void> when(final String name, final Predicate<Context> condition) {
        Objects.requireNonNull(condition, "condition is null");
        final var builder = new SequentialBuilder<Void>(name);
        builder.onlyIf(condition);
        return builder;
    }
}

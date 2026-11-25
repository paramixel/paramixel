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
 *         .each(environments, environment -> instance(environment.name(), () -> new Scenario(environment))
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
     * <p>This is a deliberate alias of {@link #sequential(String)} with identical behavior. Use {@code task} for a
     * root or a plain named node, and {@code sequential} where the contrast with {@link #parallel(String, Pool)} should
     * be explicit.
     *
     * @param name the task name; must not be blank
     * @return a sequential builder never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    static SequentialBuilder task(final String name) {
        return new SequentialBuilder(name);
    }

    /**
     * Starts a sequential group.
     *
     * @param name the task name; must not be blank
     * @return a sequential builder never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static SequentialBuilder sequential(final String name) {
        return new SequentialBuilder(name);
    }

    /**
     * Starts a sequential group whose hook identifiers resolve against an anchor class.
     *
     * <p>One class literal per definition root; every builder attached below it inherits the anchor, so hooks are
     * written {@code .before("setUp")} without repeating the class. Referencing an identifier in a definition with no
     * anchor is an authoring error.
     *
     * @param anchor the class that declares the {@code @Paramixel.Id} methods; must not be {@code null}
     * @param name the task name; must not be blank
     * @return a sequential builder anchored for id resolution; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static SequentialBuilder sequential(final Class<?> anchor, final String name) {
        final var builder = new SequentialBuilder(name);
        builder.anchor(Objects.requireNonNull(anchor, "anchor is null"));
        return builder;
    }

    /**
     * Starts a parallel group.
     *
     * @param name the task name; must not be blank
     * @param pool the concurrency budget of the direct children; must not be {@code null}
     * @return a parallel builder never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static ParallelBuilder parallel(final String name, final Pool pool) {
        return new ParallelBuilder(name, Objects.requireNonNull(pool, "pool is null"));
    }

    /**
     * Starts a parallel group whose hook identifiers resolve against an anchor class.
     *
     * @param anchor the class that declares the {@code @Paramixel.Id} methods; must not be {@code null}
     * @param name the task name; must not be blank
     * @param pool the concurrency budget of the direct children; must not be {@code null}
     * @return a parallel builder anchored for id resolution; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static ParallelBuilder parallel(final Class<?> anchor, final String name, final Pool pool) {
        final var builder = new ParallelBuilder(name, Objects.requireNonNull(pool, "pool is null"));
        builder.anchor(Objects.requireNonNull(anchor, "anchor is null"));
        return builder;
    }

    /**
     * Starts a bounded repetition task.
     *
     * @param name the task name; must not be blank
     * @return a repeat builder; configure {@code maxIterations} before building; never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static RepeatBuilder repeat(final String name) {
        return new RepeatBuilder(name);
    }

    /**
     * Starts a bounded repetition task whose hook identifiers resolve against an anchor class.
     *
     * @param anchor the class that declares the {@code @Paramixel.Id} methods; must not be {@code null}
     * @param name the task name; must not be blank
     * @return a repeat builder anchored for id resolution; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static RepeatBuilder repeat(final Class<?> anchor, final String name) {
        final var builder = new RepeatBuilder(name);
        builder.anchor(Objects.requireNonNull(anchor, "anchor is null"));
        return builder;
    }

    /**
     * Starts a conditional sequential group.
     *
     * @param name the task name; must not be blank
     * @param condition the runtime condition; must not be {@code null}
     * @return a sequential builder gated by {@code condition}; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    static SequentialBuilder when(final String name, final Predicate<Context> condition) {
        Objects.requireNonNull(condition, "condition is null");
        final var builder = new SequentialBuilder(name);
        builder.onlyIf(condition);
        return builder;
    }
}

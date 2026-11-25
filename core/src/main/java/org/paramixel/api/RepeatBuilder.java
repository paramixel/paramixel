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
import java.util.function.BiPredicate;
import nonapi.org.paramixel.authoring.IdResolver;
import org.paramixel.api.support.Backoff;
import org.paramixel.api.support.RepeatPolicy;
import org.paramixel.api.support.RetryPolicy;

/**
 * Authors a {@link Repeat} task.
 *
 * <p>The iteration bound is mandatory: {@link #maxIterations(int)} must be called before the definition can be built.
 * Each iteration is a distinct execution attempt with its own status and timestamps. A repeated task owns its context
 * scope, so every iteration starts with an empty scope and state written by one iteration never reaches the next.
 *
 * @see RepeatPolicy
 * @see RetryPolicy
 */
public final class RepeatBuilder extends TaskBuilder<RepeatBuilder> {

    private final RepeatPolicy.Builder policy = RepeatPolicy.builder();

    private String untilId;

    RepeatBuilder(final String name) {
        super(name, ExecutionMode.SEQUENTIAL, null);
    }

    @Override
    protected RepeatBuilder self() {
        return this;
    }

    @Override
    Repeat newTask(final TaskState state) {
        return new Repeat(state);
    }

    /**
     * Builds the immutable repetition task.
     *
     * @return the immutable task; never {@code null}
     * @throws IllegalStateException if no iteration bound was declared
     * @throws org.paramixel.api.exception.CompilationException if an identifier is unknown, ambiguous, malformed, or has
     *     no anchor, or if one builder is attached more than once in this tree
     */
    @Override
    public Repeat build() {
        return (Repeat) super.build();
    }

    /**
     * Sets the finite iteration bound.
     *
     * @param maxIterations the number of iterations; between 1 and {@value RepeatPolicy#MAX_ITERATIONS}
     * @return this builder
     * @throws IllegalArgumentException if the bound is outside the accepted range
     */
    public RepeatBuilder maxIterations(final int maxIterations) {
        policy.maxIterations(maxIterations);
        return this;
    }

    /**
     * Stops iterating as soon as one iteration passes.
     *
     * @return this builder
     */
    public RepeatBuilder untilPassed() {
        policy.untilPassed();
        return this;
    }

    /**
     * Stops iterating when the supplied predicate returns {@code true}.
     * Replaces any predicate set with either {@code until} overload.
     *
     * @param untilPredicate the predicate; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code untilPredicate} is {@code null}
     */
    public RepeatBuilder until(final BiPredicate<Context, Status> untilPredicate) {
        policy.until(untilPredicate);
        untilId = null;
        return this;
    }

    /**
     * Stops iterating when the identified hook returns {@code true}.
     *
     * <p>The hook takes a {@link Context} and the current iteration's {@link Status}. It returns {@code boolean} or
     * {@code Boolean} and can declare unchecked exceptions. This replaces any predicate set with either
     * {@code until} overload.
     *
     * @param id the hook identifier; must be nonblank and at most 64 UTF-16 code units
     * @return this builder
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank or longer than 64 UTF-16 code units
     */
    public RepeatBuilder until(final String id) {
        this.untilId = requireId(id);
        return this;
    }

    /**
     * Sets the delay schedule between iterations.
     *
     * @param delay the schedule; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code delay} is {@code null}
     */
    public RepeatBuilder delay(final Backoff delay) {
        Objects.requireNonNull(delay, "delay is null");
        policy.delay(delay);
        return this;
    }

    @Override
    RepeatPolicy materializeRepeat(final Class<?> effectiveAnchor, final BuildContext context) {
        final var settings = policy.build();
        if (untilId == null) {
            return settings;
        }
        final var predicate = IdResolver.resolveUntil(untilId, effectiveAnchor, context.problems());
        if (predicate == null) {
            return settings;
        }
        final var resolved = RepeatPolicy.builder()
                .maxIterations(settings.maxIterations())
                .delay(settings.delay())
                .until(predicate);
        if (settings.untilPassed()) {
            resolved.untilPassed();
        }
        return resolved.build();
    }
}

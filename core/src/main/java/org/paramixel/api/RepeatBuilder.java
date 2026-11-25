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
     *
     * @param untilPredicate the predicate; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code untilPredicate} is {@code null}
     */
    public RepeatBuilder until(final BiPredicate<Context, Status> untilPredicate) {
        Objects.requireNonNull(untilPredicate, "until is null");
        policy.until(untilPredicate);
        return this;
    }

    /**
     * Stops iterating when the identified hook returns {@code true}.
     *
     * <p>The hook takes two parameters — {@code boolean m(Context, Status)} — because the decision depends on the
     * outcome of the iteration that just finished.
     *
     * @param id the hook identifier; must not be {@code null} or blank
     * @return this builder
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public RepeatBuilder until(final String id) {
        Objects.requireNonNull(id, "id is null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Hook id is blank");
        }
        this.untilId = id;
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
    Task materialize(final Class<?> inheritedAnchor, final BuildContext context) {
        if (untilId != null) {
            final var predicate =
                    IdResolver.resolveUntil(untilId, effectiveAnchor(inheritedAnchor), context.problems());
            if (predicate != null) {
                policy.until(predicate);
            }
            untilId = null;
        }
        setRepeat(policy.build());
        return super.materialize(inheritedAnchor, context);
    }
}

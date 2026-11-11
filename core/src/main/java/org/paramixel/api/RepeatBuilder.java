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
import org.paramixel.api.support.Backoff;
import org.paramixel.api.support.RepeatPolicy;

/**
 * Authors a bounded repetition task.
 *
 * <p>The iteration bound is mandatory: {@link #maxIterations(int)} must be called before the definition can be built.
 * Each iteration is a distinct execution attempt with its own status and timestamps, and any {@code forInstance} task
 * inside the repeated subtree is re-instantiated per iteration while an inherited ancestor instance is reused.
 *
 * @param <T> the inherited primary instance type; {@link Void} when the task has no instance binding
 * @see org.paramixel.api.support.RetryPolicy
 */
public final class RepeatBuilder<T> extends TaskBuilder<T, RepeatBuilder<T>> {

    private final RepeatPolicy.Builder policy = RepeatPolicy.builder();

    RepeatBuilder(final String name) {
        super(name, ExecutionMode.SEQUENTIAL, null);
    }

    @Override
    protected RepeatBuilder<T> self() {
        return this;
    }

    /**
     * Sets the finite iteration bound.
     *
     * @param maxIterations the number of iterations; between 1 and {@value RepeatPolicy#MAX_ITERATIONS}
     * @return this builder
     * @throws IllegalArgumentException if the bound is outside the accepted range
     */
    public RepeatBuilder<T> maxIterations(final int maxIterations) {
        policy.maxIterations(maxIterations);
        return this;
    }

    /**
     * Stops iterating as soon as one iteration passes.
     *
     * @return this builder
     */
    public RepeatBuilder<T> untilPassed() {
        policy.untilPassed();
        return this;
    }

    /**
     * Stops iterating when the supplied predicate returns {@code true}.
     *
     * @param until the predicate; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code until} is {@code null}
     */
    public RepeatBuilder<T> until(final BiPredicate<Context, Status> until) {
        Objects.requireNonNull(until, "until is null");
        policy.until(until);
        return this;
    }

    /**
     * Sets the delay schedule between iterations.
     *
     * @param delay the schedule; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code delay} is {@code null}
     */
    public RepeatBuilder<T> delay(final Backoff delay) {
        Objects.requireNonNull(delay, "delay is null");
        policy.delay(delay);
        return this;
    }

    @Override
    public Task build() {
        setRepeat(policy.build());
        return super.build();
    }
}

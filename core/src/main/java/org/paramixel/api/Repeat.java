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

/**
 * A built repetition task: one iteration body replayed a bounded number of times.
 *
 * <p>This is the immutable result of {@link RepeatBuilder#build()}. Repetition is intentional replay, not retry: each
 * iteration has its own attempt index, status, and timestamps, and the node owns its context scope, so every iteration
 * starts with an empty scope and state written by one iteration never reaches the next.
 *
 * @see RepeatBuilder
 */
public final class Repeat extends AbstractTask implements Task {

    Repeat(final TaskState state) {
        super(state);
    }

    /**
     * Starts a repeat builder whose hook identifiers resolve against an anchor class.
     *
     * @param anchor the class that declares the {@code @Paramixel.Id} methods; must not be {@code null}
     * @param name the task name; must not be blank
     * @return a repeat builder anchored for id resolution; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or longer than 512 characters
     */
    public static RepeatBuilder builder(final Class<?> anchor, final String name) {
        final var builder = new RepeatBuilder(name);
        builder.anchor(Objects.requireNonNull(anchor, "anchor is null"));
        return builder;
    }

    /**
     * Starts a repeat builder.
     *
     * <p>The iteration bound is mandatory: {@link RepeatBuilder#maxIterations(int)} must be called before the
     * definition can be built.
     *
     * @param name the task name; must not be blank
     * @return a repeat builder; never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or longer than 512 characters
     */
    public static RepeatBuilder builder(final String name) {
        return new RepeatBuilder(name);
    }
}

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
 * A built parallel task: its direct children are eligible concurrently, bounded by a {@link Pool}.
 *
 * <p>This is the immutable result of {@link ParallelBuilder#build()}. A parallel node has no
 * {@code dependent}/{@code independent} policy: it is always <em>attempt-all-unless-explicitly-canceled</em>. Each
 * direct child owns its own context scope, so siblings never see each other's entries. A fixture shared by several
 * branches is published with {@code bind} on this node, and Paramixel cannot make it thread-safe: guard shared state in
 * the fixture, give each child its own entry, or declare an exclusive lock.
 *
 * @see ParallelBuilder
 */
public final class Parallel extends AbstractTask implements Task {

    Parallel(final TaskState state) {
        super(state);
    }

    /**
     * Starts a parallel builder whose hook identifiers resolve against an anchor class.
     *
     * @param anchor the class that declares the {@code @Paramixel.Id} methods; must not be {@code null}
     * @param name the task name; must not be blank
     * @param pool the concurrency budget of the direct children; must not be {@code null}
     * @return a parallel builder anchored for id resolution; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or longer than 512 characters
     */
    public static ParallelBuilder builder(final Class<?> anchor, final String name, final Pool pool) {
        final var builder = new ParallelBuilder(name, Objects.requireNonNull(pool, "pool is null"));
        builder.anchor(Objects.requireNonNull(anchor, "anchor is null"));
        return builder;
    }

    /**
     * Starts a parallel builder.
     *
     * @param name the task name; must not be blank
     * @param pool the concurrency budget of the direct children; must not be {@code null}
     * @return a parallel builder; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or longer than 512 characters
     */
    public static ParallelBuilder builder(final String name, final Pool pool) {
        return new ParallelBuilder(name, Objects.requireNonNull(pool, "pool is null"));
    }
}

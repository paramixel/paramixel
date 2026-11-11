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
 * Authors a parallel task: direct children are eligible concurrently, bounded by a {@link Pool}.
 *
 * <p>A parallel task has no {@code dependent}/{@code independent} policy: it is always
 * <em>attempt-all-unless-explicitly-canceled</em>. A failed sibling does not cancel other siblings; the group
 * aggregates the most severe outcome.
 *
 * <p>Parallel children that share an inherited primary instance are concurrent calls on the same object. Paramixel
 * cannot make {@code T} thread-safe and deliberately does not synchronize typed callbacks, because that would defeat
 * the point of a parallel group. Guard shared state in the fixture, give each child its own scope, or declare an
 * exclusive lock.
 *
 * @param <T> the inherited primary instance type; {@link Void} when the task has no instance binding
 */
public final class ParallelBuilder<T> extends TaskBuilder<T, ParallelBuilder<T>> {

    ParallelBuilder(final String name, final Pool pool) {
        super(name, ExecutionMode.PARALLEL, Objects.requireNonNull(pool, "pool is null"));
    }

    @Override
    protected ParallelBuilder<T> self() {
        return this;
    }
}

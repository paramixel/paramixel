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
 * A built sequential task: its direct children run one at a time in resolved order.
 *
 * <p>This is the immutable result of {@link SequentialBuilder#build()}. Only a sequential node carries the
 * failure-continuation policy: {@link #dependent()} is the default, and {@link SequentialBuilder#independent()} selects
 * attempt-every-child instead.
 *
 * @see SequentialBuilder
 */
public final class Sequential extends AbstractTask implements Task {

    Sequential(final TaskState state) {
        super(state);
    }

    /**
     * Starts a sequential builder whose hook identifiers resolve against an anchor class.
     *
     * <p>One class literal per definition root; every builder attached below it inherits the anchor, so hooks are
     * written {@code .before("setUp")} without repeating the class. Referencing an identifier in a definition with no
     * anchor is an authoring error.
     *
     * @param anchor the class that declares the {@code @Paramixel.Id} methods; must not be {@code null}
     * @param name the task name; must not be blank
     * @return a sequential builder anchored for id resolution; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or longer than 512 characters
     */
    public static SequentialBuilder builder(final Class<?> anchor, final String name) {
        final var builder = new SequentialBuilder(name);
        builder.anchor(Objects.requireNonNull(anchor, "anchor is null"));
        return builder;
    }

    /**
     * Starts a sequential builder.
     *
     * @param name the task name; must not be blank
     * @return a sequential builder; never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or longer than 512 characters
     */
    public static SequentialBuilder builder(final String name) {
        return new SequentialBuilder(name);
    }
}

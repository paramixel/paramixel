/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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
import org.jspecify.annotations.NonNull;

/**
 * Simple {@link Named} implementation that pairs a display name with a value.
 *
 * @param <T> type of the wrapped value
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public final class NamedValue<T> implements Named {

    /**
     * Stores the name.
     */
    private final String name;

    /**
     * Stores the value.
     */
    private final T value;

    /**
     * Creates a new instance.
     *
     * @param name the name
     * @param value the value
     */
    private NamedValue(final @NonNull String name, final T value) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.value = value;
    }

    /**
     * Creates a new {@code NamedValue} with the provided name and value.
     *
     * @param name  display name to use
     * @param value value to wrap
     * @param <T>   type of the wrapped value
     * @return a new {@code NamedValue}
     * @since 0.0.1
     */
    public static <T> NamedValue<T> of(final @NonNull String name, final T value) {
        return new NamedValue<>(name, value);
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the wrapped value.
     *
     * @return the wrapped value (may be {@code null})
     * @since 0.0.1
     */
    public T getValue() {
        return value;
    }

    /**
     * Returns the wrapped value, cast to the specified type.
     *
     * @param type class token for the expected type
     * @return the wrapped value cast to {@code type}
     * @throws NullPointerException     if {@code type} is {@code null}
     * @throws ClassCastException       if the value cannot be cast to {@code type}
     * @since 0.0.1
     */
    public <V> V getValue(final @NonNull Class<V> type) {
        return value == null ? null : type.cast(value);
    }
}

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

package org.paramixel.core;

import java.util.Objects;

/**
 * Wraps a non-null object stored in a {@link Store}.
 *
 * <p>A {@code Value} centralizes runtime type conversion so callers can keep store operations
 * uniform while still retrieving strongly typed objects when needed.
 */
public final class Value {

    private final Object value;

    private Value(final Object value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Creates a store value that wraps the supplied object.
     *
     * @param value the object to wrap
     * @return a new store value wrapping {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static Value of(final Object value) {
        return new Value(value);
    }

    /**
     * Returns the wrapped object.
     *
     * @return the wrapped object
     */
    public Object get() {
        return value;
    }

    /**
     * Returns whether the wrapped object is assignable to the requested type.
     *
     * @param type the expected runtime type
     * @return {@code true} when the wrapped object is compatible with {@code type}
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public boolean isType(final Class<?> type) {
        Objects.requireNonNull(type, "type must not be null");
        return type.isInstance(value);
    }

    /**
     * Returns the wrapped object cast to the requested type.
     *
     * @param <T> the requested type
     * @param type the expected runtime type
     * @return the wrapped object cast to {@code type}
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws ClassCastException if the wrapped object is not compatible with {@code type}
     */
    public <T> T cast(final Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        return type.cast(value);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Value other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

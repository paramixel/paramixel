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
import java.util.regex.Pattern;

/**
 * A typed, explicitly named key for a scoped resource.
 *
 * <p>Keys let a task declare secondary objects beyond its single primary instance. Two resources of the same Java
 * class can be bound under different keys, and lookup never falls back to reflective class matching.
 *
 * <p>Equality is by namespace, name, and type. The {@code type} is retained for diagnostics and for the compile-time
 * duplicate-key check; it is never used to discover a resource dynamically.
 *
 * @param <T> the resource type
 * @see TaskBuilder#resource(Key, ThrowingSupplier)
 * @see Context#require(Key)
 */
public final class Key<T> {

    private static final Pattern VALID_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]*");

    private final String namespace;
    private final String name;
    private final Class<T> type;

    private Key(final String namespace, final String name, final Class<T> type) {
        this.namespace = namespace;
        this.name = name;
        this.type = type;
    }

    /**
     * Creates a key.
     *
     * @param namespace the logical namespace, for example a class or feature name; must be non-blank and match
     *     {@code [A-Za-z][A-Za-z0-9_.-]*}
     * @param name the key name within the namespace; must be non-blank and match
     *     {@code [A-Za-z][A-Za-z0-9_.-]*}
     * @param type the resource type; must not be {@code null}
     * @param <T> the resource type
     * @return the key; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the namespace or name is invalid
     */
    public static <T> Key<T> named(final String namespace, final String name, final Class<T> type) {
        Objects.requireNonNull(namespace, "namespace is null");
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(type, "type is null");
        requireToken(namespace, "namespace");
        requireToken(name, "name");
        return new Key<>(namespace, name, type);
    }

    private static void requireToken(final String value, final String label) {
        if (!VALID_TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid " + label + " '" + value + "': must match [A-Za-z][A-Za-z0-9_.-]*");
        }
    }

    /**
     * Returns the namespace.
     *
     * @return the namespace; never {@code null}
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Returns the key name within the namespace.
     *
     * @return the name; never {@code null}
     */
    public String name() {
        return name;
    }

    /**
     * Returns the declared resource type.
     *
     * @return the type; never {@code null}
     */
    public Class<T> type() {
        return type;
    }

    /**
     * Returns the qualified display form of this key.
     *
     * @return {@code namespace:name}; never {@code null}
     */
    public String qualifiedName() {
        return namespace + ":" + name;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Key<?> other)) {
            return false;
        }
        return namespace.equals(other.namespace) && name.equals(other.name) && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name, type);
    }

    @Override
    public String toString() {
        return "Key[" + qualifiedName() + " (" + type.getName() + ")]";
    }
}

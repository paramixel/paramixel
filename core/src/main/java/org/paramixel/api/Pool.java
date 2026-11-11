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
 * The immutable logical name of a concurrency budget.
 *
 * <p>A {@code Pool} is a <strong>value object</strong>. It creates no threads, registers no global state, allocates
 * no permits, and is not an {@link java.util.concurrent.ExecutorService}. Capacity is configured on the runner with
 * {@code Runner.Builder.pool(Pool, int)} or with the {@code paramixel.parallelism.<name>} property, and the scheduler
 * enforces it.
 *
 * <p>Names are case-sensitive and must match {@code [A-Za-z][A-Za-z0-9_-]*}; blank names and names with leading or
 * trailing whitespace are rejected at construction time rather than being trimmed. Equal names produce equal values,
 * so two independently constructed references to {@code "environments"} are interchangeable.
 *
 * @see org.paramixel.api.Tasks#parallel(String, Pool)
 */
public final class Pool {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    private final String name;

    private Pool(final String name) {
        this.name = name;
    }

    /**
     * Returns the pool with the supplied logical name.
     *
     * @param name the pool name; must not be {@code null}, blank, or invalid
     * @return the pool value; never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or does not match {@code [A-Za-z][A-Za-z0-9_-]*}
     */
    public static Pool named(final String name) {
        Objects.requireNonNull(name, "name is null");
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid pool name '" + name + "': must match [A-Za-z][A-Za-z0-9_-]*");
        }
        return new Pool(name);
    }

    /**
     * Returns the logical name of this pool.
     *
     * @return the name; never {@code null}
     */
    public String name() {
        return name;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof Pool other && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "Pool[" + name + "]";
    }
}

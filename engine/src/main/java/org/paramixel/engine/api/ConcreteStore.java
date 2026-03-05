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

package org.paramixel.engine.api;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.Store;

/**
 * Concrete, thread-safe {@link Store} implementation.
 *
 * <p>This implementation is optimized for low-overhead concurrent access and is backed by a
 * {@link ConcurrentHashMap}. Keys are {@link String}s and values are {@link Object}s.
 *
 * <p>Null values are not stored. A {@code put(key, null)} behaves like {@link #remove(String)}.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public final class ConcreteStore implements Store {

    /**
     * The underlying concurrent map storing the key-value pairs. Keys are non-null strings, and values are objects.
     */
    private final ConcurrentMap<String, Object> map;

    /**
     * Creates an empty store.
     *
     * @since 0.0.1
     */
    public ConcreteStore() {
        this(10);
    }

    /**
     * Creates an empty store with an expected capacity.
     *
     * @param initialCapacity the expected number of entries in the store; used to optimize internal data structures
     * @since 0.0.1
     */
    public ConcreteStore(final int initialCapacity) {
        this.map = new ConcurrentHashMap<>(Math.max(0, initialCapacity));
    }

    @Override
    public Object put(final @NonNull String key, final Object value) {
        Objects.requireNonNull(key, "key must not be null");
        if (value == null) {
            return map.remove(key);
        }
        return map.put(key, value);
    }

    @Override
    public <T> T put(final @NonNull String key, final @NonNull Class<T> type, final T value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (value != null && !type.isInstance(value)) {
            throw new ClassCastException("Store value for key '" + key + "' is "
                    + value.getClass().getName() + ", not assignable to " + type.getName());
        }
        final Object previous = put(key, value);
        if (previous == null) {
            return null;
        }
        if (!type.isInstance(previous)) {
            throw new ClassCastException("Store previous value for key '" + key + "' is "
                    + previous.getClass().getName() + ", not assignable to " + type.getName());
        }
        return type.cast(previous);
    }

    @Override
    public Object get(final @NonNull String key) {
        Objects.requireNonNull(key, "key must not be null");
        return map.get(key);
    }

    @Override
    public <T> T get(final @NonNull String key, final @NonNull Class<T> type) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        final Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException("Store value for key '" + key + "' is "
                    + value.getClass().getName() + ", not assignable to " + type.getName());
        }
        return type.cast(value);
    }

    @Override
    public boolean contains(final @NonNull String key) {
        Objects.requireNonNull(key, "key must not be null");
        return map.containsKey(key);
    }

    @Override
    public Object remove(final @NonNull String key) {
        Objects.requireNonNull(key, "key must not be null");
        return map.remove(key);
    }

    @Override
    public <T> T remove(final @NonNull String key, final @NonNull Class<T> type) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        final Object[] removed = new Object[1];
        map.compute(key, (k, existing) -> {
            if (existing == null) {
                return null;
            }
            if (!type.isInstance(existing)) {
                throw new ClassCastException("Store value for key '" + key + "' is "
                        + existing.getClass().getName() + ", not assignable to " + type.getName());
            }
            removed[0] = existing;
            return null;
        });

        if (removed[0] == null) {
            return null;
        }
        return type.cast(removed[0]);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public Object computeIfAbsent(final @NonNull String key, final @NonNull Supplier<?> supplier) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        return map.computeIfAbsent(key, k -> {
            return supplier.get(); // may be null; CHM treats null as "no mapping"
        });
    }

    @Override
    public <T> Optional<T> find(final @NonNull String key, final @NonNull Class<T> type) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        return Optional.ofNullable(get(key, type));
    }

    @Override
    public <T> T computeIfAbsent(
            final @NonNull String key, final @NonNull Class<T> type, final @NonNull Supplier<? extends T> supplier) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        final Object value = map.compute(key, (k, existing) -> {
            if (existing != null) {
                if (!type.isInstance(existing)) {
                    throw new ClassCastException("Store value for key '" + key + "' is "
                            + existing.getClass().getName() + ", not assignable to " + type.getName());
                }
                return existing;
            }

            final T created = supplier.get();
            if (created == null) {
                return null;
            }
            if (!type.isInstance(created)) {
                throw new ClassCastException("Supplier for key '" + key + "' produced "
                        + created.getClass().getName() + ", not assignable to " + type.getName());
            }
            return created;
        });

        if (value == null) {
            return null;
        }

        return type.cast(value);
    }

    @Override
    public Iterator<String> keyIterator() {
        return map.keySet().iterator();
    }
}

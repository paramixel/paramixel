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

import java.util.Iterator;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;

/**
 * A key/value store for sharing state within an execution scope.
 *
 * <p>{@code Store} provides a small, stable API for associating arbitrary objects with
 * string keys. It is primarily intended for framework and test-engine use cases where
 * state must be shared across callbacks within a defined scope (engine, class, argument,
 * invocation, etc.).
 *
 * <h2>Typed Access</h2>
 * <p>The typed methods (for example {@link #get(String, Class)} and
 * {@link #computeIfAbsent(String, Class, Supplier)}) perform a runtime type check
 * using {@link Class#isInstance(Object)}. If the stored value is present and not
 * assignable to the requested type, a {@link ClassCastException} is thrown.
 *
 * <h2>Null Semantics</h2>
 * <ul>
 *   <li>Absent mappings are represented by {@code null} return values from
 *       {@link #get(String)} and {@link #remove(String)}.</li>
 *   <li>{@code put(key, null)} behaves like {@link #remove(String)}.</li>
 *   <li>Suppliers passed to {@code computeIfAbsent} may return {@code null}; in that
 *       case, implementations may leave the key unmapped.</li>
 * </ul>
 *
 * <h2>Concurrency</h2>
 * <p>Implementations are expected to be compatible with the concurrency model of the
 * hosting engine (for example, backed by {@code ConcurrentHashMap}). This interface
 * does not mandate specific atomicity guarantees beyond what each method documents.
 * In particular, callers should assume suppliers may be invoked more than once under
 * contention.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public interface Store {

    /**
     * Stores {@code value} under {@code key}.
     *
     * <p>If {@code value} is {@code null}, this method behaves like {@link #remove(String)}.
     *
     * @param key the key under which to store the value; must not be {@code null}
     * @param value the value to store; may be {@code null}
     * @return the previous value, or {@code null} if none
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 0.0.1
     */
    Object put(final @NonNull String key, final Object value);

    /**
     * Stores {@code value} under {@code key} and returns the previous value cast to {@code type}.
     *
     * <p>If a previous value exists under {@code key} and is not assignable to {@code type}, this
     * method throws {@link ClassCastException}.
     *
     * <p>Atomicity note: this interface does not require the type check against the previous value
     * to be atomic with respect to the write. Implementations may choose whether the new value is
     * stored when a {@code ClassCastException} is thrown.
     *
     * @param key the key under which to store the value; must not be {@code null}
     * @param type the expected type of the previous value; must not be {@code null}
     * @param value the value to store; may be {@code null} (implementation-defined)
     * @return the previous value cast to {@code type}, or {@code null} if none
     * @throws ClassCastException if a non-null previous value is not assignable to {@code type}
     * @throws NullPointerException if {@code key} or {@code type} is {@code null}
     * @since 0.0.1
     */
    <T> T put(final @NonNull String key, final @NonNull Class<T> type, final T value);

    /**
     * Returns the raw value stored under {@code key}, or {@code null} if absent.
     *
     * <p>This is the most direct access method and mirrors {@code Map#get} semantics.</p>
     *
     * @param key the key whose associated value is to be returned; must not be {@code null}
     * @return the value stored under {@code key}, or {@code null} if none
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 0.0.1
     */
    Object get(final @NonNull String key);

    /**
     * Returns the value stored under {@code key}, cast to {@code type}.
     *
     * @param key the key whose associated value is to be returned; must not be {@code null}
     * @param type the expected type of the stored value; must not be {@code null}
     * @return the value cast to {@code type}, or {@code null} if none
     * @throws ClassCastException if a non-null value is present but not assignable to {@code type}
     * @throws NullPointerException if {@code key} or {@code type} is {@code null}
     * @since 0.0.1
     */
    <T> T get(final @NonNull String key, final @NonNull Class<T> type);

    /**
     * Returns {@code true} if a value is present for {@code key}.
     *
     * @param key the key to test; must not be {@code null}
     * @return {@code true} if a value is present for {@code key}, {@code false} otherwise
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 0.0.1
     */
    boolean contains(final @NonNull String key);

    /**
     * Removes {@code key}.
     *
     * @param key the key to remove; must not be {@code null}
     * @return the removed value, or {@code null} if none
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 0.0.1
     */
    Object remove(final @NonNull String key);

    /**
     * Removes {@code key} and returns the removed value cast to {@code type}.
     *
     * <p>If a value is removed and is not assignable to {@code type}, this method throws
     * {@link ClassCastException}.
     *
     * @param key the key to remove; must not be {@code null}
     * @param type the expected type of the removed value; must not be {@code null}
     * @return the removed value cast to {@code type}, or {@code null} if none
     * @throws ClassCastException if a non-null value was removed but is not assignable to {@code type}
     * @throws NullPointerException if {@code key} or {@code type} is {@code null}
     * @since 0.0.1
     */
    <T> T remove(final @NonNull String key, final @NonNull Class<T> type);

    /**
     * Removes all entries from this store.
     *
     * @implNote Implementations should document whether this operation is atomic with respect to
     *     concurrent access.
     * @since 0.0.1
     */
    void clear();

    /**
     * Returns the number of entries in this store.
     *
     * @return the current number of mappings
     * @since 0.0.1
     */
    int size();

    /**
     * Returns the existing value for {@code key}, or stores and returns the value
     * produced by {@code supplier} when absent.
     *
     * <p>Concurrency note: under contention, implementations may invoke {@code supplier} more than
     * once; callers should ensure the supplier is side-effect free or otherwise safe to call
     * multiple times.
     *
     * @param key the key whose value should be computed; must not be {@code null}
     * @param supplier the supplier used to create a value when absent; must not be {@code null}
     * @return the current (existing or newly computed) value for {@code key}, or {@code null} if the
     *     supplier returns {@code null} and the implementation chooses to leave the key unmapped
     * @throws NullPointerException if {@code key} or {@code supplier} is {@code null}
     * @since 0.0.1
     */
    Object computeIfAbsent(final @NonNull String key, final @NonNull Supplier<?> supplier);

    /**
     * Returns the value stored under {@code key} as an {@link Optional}.
     *
     * @param key the key whose associated value is to be returned; must not be {@code null}
     * @param type the expected type of the stored value; must not be {@code null}
     * @return an {@link Optional} describing the stored value (when present), otherwise {@link Optional#empty()}
     * @throws ClassCastException if a non-null value is present but not assignable to {@code type}
     * @throws NullPointerException if {@code key} or {@code type} is {@code null}
     * @since 0.0.1
     */
    <T> Optional<T> find(final @NonNull String key, final @NonNull Class<T> type);

    /**
     * Returns the existing typed value for {@code key}, or stores and returns the value
     * produced by {@code supplier} when absent.
     *
     * <p>If a value is present and is not assignable to {@code type}, this method throws
     * {@link ClassCastException}.
     *
     * <p>Concurrency note: under contention, implementations may invoke {@code supplier} more than
     * once; callers should ensure the supplier is side-effect free or otherwise safe to call
     * multiple times.
     *
     * @param key the key whose value should be computed; must not be {@code null}
     * @param type the expected type of the stored value; must not be {@code null}
     * @param supplier the supplier used to create a value when absent; must not be {@code null}
     * @return the current (existing or newly computed) typed value for {@code key}, or {@code null} if the
     *     supplier returns {@code null} and the implementation chooses to leave the key unmapped
     * @throws ClassCastException if a non-null existing value is present but not assignable to {@code type}
     * @throws ClassCastException if the supplier produces a non-null value that is not assignable to {@code type}
     * @throws NullPointerException if {@code key}, {@code type}, or {@code supplier} is {@code null}
     * @since 0.0.1
     */
    <T> T computeIfAbsent(
            final @NonNull String key, final @NonNull Class<T> type, final @NonNull Supplier<? extends T> supplier);

    /**
     * Returns an iterator over the keys in this store. The order of keys is not
     * specified and may vary between implementations.
     *
     * @return an iterator over the keys in this store
     * @since 0.0.1
     */
    Iterator<String> keyIterator();
}

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

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Stores local context values by string key.
 *
 * <p>{@code Store} mirrors the instance-oriented behavior of {@link java.util.Map} while keeping a
 * Paramixel-specific API surface that uses {@link String} keys throughout.
 *
 * <p>Implementations must be thread-safe and must reject {@code null} keys and
 *     {@code null} values.
 */
public interface Store {

    /**
     * Associates a string key with a value in a {@link Store}.
     */
    interface Entry {

        /**
         * Returns the entry key.
         *
         * @return the string key that identifies this entry
         */
        String getKey();

        /**
         * Returns the current entry value.
         *
         * @return the object currently associated with the key
         */
        Object getValue();

        /**
         * Replaces the entry value.
         *
         * @param value the new value to associate with the entry key
         * @return the previous value
         * @throws NullPointerException if {@code value} is {@code null}
         */
        Object setValue(Object value);
    }

    /**
     * Returns the number of entries in this store.
     *
     * @return the number of stored entries
     */
    int size();

    /**
     * Returns whether this store has no entries.
     *
     * @return {@code true} when this store is empty
     */
    boolean isEmpty();

    /**
     * Returns whether this store contains the supplied key.
     *
     * @param key the key to test
     * @return {@code true} when the key is present
     * @throws NullPointerException if {@code key} is {@code null}
     */
    boolean containsKey(String key);

    /**
     * Returns whether this store contains the supplied value.
     *
     * @param value the value to test
     * @return {@code true} when the value is present
     * @throws NullPointerException if {@code value} is {@code null}
     */
    boolean containsValue(Object value);

    /**
     * Returns the value associated with the supplied key.
     *
     * @param key the key to resolve
     * @return an {@link Optional} containing the associated value, or an empty {@link Optional}
     *     when the key is absent
     * @throws NullPointerException if {@code key} is {@code null}
     */
    Optional<Object> get(String key);

    /**
     * Returns the value associated with the supplied key, cast to the requested type.
     *
     * @param <T> the requested type
     * @param key the key to resolve
     * @param type the expected runtime type
     * @return an {@link Optional} containing the associated value cast to {@code type}, or an empty
     *     {@link Optional} when the key is absent
     * @throws NullPointerException if {@code key} or {@code type} is {@code null}
     * @throws ClassCastException if the associated value is not compatible with {@code type}
     */
    default <T> Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        return get(key).map(type::cast);
    }

    /**
     * Associates the supplied value with the supplied key.
     *
     * @param key the key to write
     * @param value the value to store
     * @return an {@link Optional} containing the previous value associated with {@code key}, or an
     *     empty {@link Optional} when the key was not present
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     */
    Optional<Object> put(String key, Object value);

    /**
     * Removes the value associated with the supplied key.
     *
     * @param key the key to remove
     * @return an {@link Optional} containing the removed value, or an empty {@link Optional} when
     *     the key was absent
     * @throws NullPointerException if {@code key} is {@code null}
     */
    Optional<Object> remove(String key);

    /**
     * Removes the value associated with the supplied key, cast to the requested type.
     *
     * @param <T> the requested type
     * @param key the key to remove
     * @param type the expected runtime type
     * @return an {@link Optional} containing the removed value cast to {@code type}, or an empty
     *     {@link Optional} when the key was absent
     * @throws NullPointerException if {@code key} or {@code type} is {@code null}
     * @throws ClassCastException if the removed value is not compatible with {@code type}
     */
    default <T> Optional<T> remove(String key, Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        return remove(key).map(type::cast);
    }

    /**
     * Copies every entry from the supplied store into this store.
     *
     * @param store the source store
     * @throws NullPointerException if {@code store} is {@code null}
     */
    void putAll(Store store);

    /**
     * Removes every entry from this store.
     */
    void clear();

    /**
     * Returns a live view of the keys contained in this store.
     *
     * @return the keys contained in this store a live view of the keys; changes to the store are reflected in the returned set
     */
    Set<String> keySet();

    /**
     * Returns a live view of the values contained in this store.
     *
     * @return the values contained in this store a live view of the values; changes to the store are reflected in the returned collection
     */
    Collection<Object> values();

    /**
     * Returns a live view of the entries contained in this store.
     *
     * @return the entries contained in this store a live view of the entries; changes to the store are reflected in the returned set
     */
    Set<Entry> entrySet();

    /**
     * Returns the value associated with the supplied key, or the default value when the key is
     * absent.
     *
     * @param key the key to resolve
     * @param defaultValue the value to return when the key is absent
     * @return the associated value or {@code defaultValue} when absent
     * @throws NullPointerException if {@code key} or {@code defaultValue} is {@code null}
     */
    Object getOrDefault(String key, Object defaultValue);

    /**
     * Returns the value associated with the supplied key cast to the requested type, or the
     * default value when the key is absent.
     *
     * @param <T> the requested type
     * @param key the key to resolve
     * @param type the expected runtime type
     * @param defaultValue the value to return when the key is absent
     * @return the associated value cast to {@code type}, or {@code defaultValue} when absent
     * @throws NullPointerException if {@code key}, {@code type}, or {@code defaultValue} is {@code null}
     * @throws ClassCastException if the associated value is not compatible with {@code type}
     */
    default <T> T getOrDefault(String key, Class<T> type, T defaultValue) {
        Objects.requireNonNull(type, "type must not be null");
        return get(key, type).orElse(defaultValue);
    }

    /**
     * Performs the supplied action for each entry in this store.
     *
     * @param action the action to perform
     * @throws NullPointerException if {@code action} is {@code null}
     */
    void forEach(BiConsumer<? super String, ? super Object> action);

    /**
     * Replaces each entry value with the result of the supplied remapping function.
     *
     * @param function the remapping function
     * @throws NullPointerException if {@code function} is {@code null} or produces a
     *     {@code null} value
     */
    void replaceAll(BiFunction<? super String, ? super Object, ?> function);

    /**
     * Associates the supplied value with the supplied key when the key is currently absent.
     *
     * @param key the key to write
     * @param value the value to store when absent
     * @return an {@link Optional} containing the current value associated with {@code key}, or an
     *     empty {@link Optional} when the new value was stored
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     */
    Optional<Object> putIfAbsent(String key, Object value);

    /**
     * Removes the entry only when the current value matches the supplied value.
     *
     * @param key the key to remove
     * @param value the expected current value
     * @return {@code true} when the entry was removed
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     */
    boolean remove(String key, Object value);

    /**
     * Replaces the entry value only when the current value matches the supplied old value.
     *
     * @param key the key to update
     * @param oldValue the expected current value
     * @param newValue the replacement value
     * @return {@code true} when the replacement succeeded
     * @throws NullPointerException if any argument is {@code null}
     */
    boolean replace(String key, Object oldValue, Object newValue);

    /**
     * Replaces the entry value only when the key is currently present.
     *
     * @param key the key to update
     * @param value the replacement value
     * @return an {@link Optional} containing the previous value associated with {@code key}, or an
     *     empty {@link Optional} when the key was not present
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     */
    Optional<Object> replace(String key, Object value);

    /**
     * Computes a value for the supplied key when the key is currently absent.
     *
     * @param key the key to compute
     * @param mappingFunction the function used to create a value for an absent key
     * @return an {@link Optional} containing the current or computed value associated with
     *     {@code key}, or an empty {@link Optional} when the mapping function returns {@code null}
     * @throws NullPointerException if {@code key} or {@code mappingFunction} is {@code null}
     */
    Optional<Object> computeIfAbsent(String key, Function<? super String, ?> mappingFunction);

    /**
     * Computes a replacement value for the supplied key when the key is currently present.
     *
     * @param key the key to compute
     * @param remappingFunction the function used to compute the new value
     * @return an {@link Optional} containing the new value associated with {@code key}, or an empty
     *     {@link Optional} when the computation removes the entry or the key was absent
     * @throws NullPointerException if {@code key} or {@code remappingFunction} is {@code null}
     */
    Optional<Object> computeIfPresent(String key, BiFunction<? super String, ? super Object, ?> remappingFunction);

    /**
     * Computes a value for the supplied key regardless of whether the key is currently present.
     *
     * @param key the key to compute
     * @param remappingFunction the function used to compute the new value
     * @return an {@link Optional} containing the new value associated with {@code key}, or an empty
     *     {@link Optional} when the computation removes the entry
     * @throws NullPointerException if {@code key} or {@code remappingFunction} is {@code null}
     */
    Optional<Object> compute(String key, BiFunction<? super String, ? super Object, ?> remappingFunction);

    /**
     * Merges the supplied value into the entry associated with the supplied key.
     *
     * @param key the key to update
     * @param value the non-null value to merge
     * @param remappingFunction the function used when a current value is already present
     * @return an {@link Optional} containing the new value associated with {@code key}, or an empty
     *     {@link Optional} when the remapping removes the entry
     * @throws NullPointerException if {@code key}, {@code value}, or {@code remappingFunction} is
     *     {@code null}
     */
    Optional<Object> merge(String key, Object value, BiFunction<? super Object, ? super Object, ?> remappingFunction);

    /**
     * Returns whether the value associated with the supplied key is assignable to the requested type.
     *
     * <p>Returns {@code false} when the key is absent.
     *
     * @param key the key to test
     * @param type the expected runtime type
     * @return {@code true} when the key is present and the associated value is compatible with
     *     {@code type}
     * @throws NullPointerException if {@code key} or {@code type} is {@code null}
     */
    default boolean isType(String key, Class<?> type) {
        Objects.requireNonNull(type, "type must not be null");
        return get(key).map(type::isInstance).orElse(false);
    }
}

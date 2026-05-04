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

package org.paramixel.core.spi;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.paramixel.core.Store;
import org.paramixel.core.Value;

/**
 * Default thread-safe {@link Store} implementation backed by a {@link ConcurrentHashMap}.
 *
 * <p>This implementation enforces Paramixel store invariants by rejecting {@code null} keys and
 * {@code null} values through the underlying concurrent map.
 */
public final class DefaultStore implements Store {

    private final ConcurrentMap<String, Value> delegate = new ConcurrentHashMap<>();

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(final Value value) {
        Objects.requireNonNull(value, "value must not be null");
        return delegate.containsValue(value);
    }

    @Override
    public Optional<Value> get(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        return Optional.ofNullable(delegate.get(key));
    }

    @Override
    public Optional<Value> put(final String key, final Value value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return Optional.ofNullable(delegate.put(key, value));
    }

    @Override
    public Optional<Value> remove(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        return Optional.ofNullable(delegate.remove(key));
    }

    @Override
    public void putAll(final Store store) {
        Objects.requireNonNull(store, "store must not be null");
        store.forEach(delegate::put);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public Set<String> keySet() {
        return delegate.keySet();
    }

    @Override
    public Collection<Value> values() {
        return delegate.values();
    }

    @Override
    public Set<Entry> entrySet() {
        return new EntrySetView();
    }

    @Override
    public Value getOrDefault(final String key, final Value defaultValue) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(defaultValue, "defaultValue must not be null");
        return delegate.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(final BiConsumer<? super String, ? super Value> action) {
        Objects.requireNonNull(action, "action must not be null");
        delegate.forEach(action);
    }

    @Override
    public void replaceAll(final BiFunction<? super String, ? super Value, ? extends Value> function) {
        Objects.requireNonNull(function, "function must not be null");
        delegate.replaceAll(function);
    }

    @Override
    public Optional<Value> putIfAbsent(final String key, final Value value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return Optional.ofNullable(delegate.putIfAbsent(key, value));
    }

    @Override
    public boolean remove(final String key, final Value value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return delegate.remove(key, value);
    }

    @Override
    public boolean replace(final String key, final Value oldValue, final Value newValue) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(oldValue, "oldValue must not be null");
        Objects.requireNonNull(newValue, "newValue must not be null");
        return delegate.replace(key, oldValue, newValue);
    }

    @Override
    public Optional<Value> replace(final String key, final Value value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return Optional.ofNullable(delegate.replace(key, value));
    }

    @Override
    public Optional<Value> computeIfAbsent(
            final String key, final Function<? super String, ? extends Value> mappingFunction) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(mappingFunction, "mappingFunction must not be null");
        return Optional.ofNullable(delegate.computeIfAbsent(key, mappingFunction));
    }

    @Override
    public Optional<Value> computeIfPresent(
            final String key, final BiFunction<? super String, ? super Value, ? extends Value> remappingFunction) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(remappingFunction, "remappingFunction must not be null");
        return Optional.ofNullable(delegate.computeIfPresent(key, remappingFunction));
    }

    @Override
    public Optional<Value> compute(
            final String key, final BiFunction<? super String, ? super Value, ? extends Value> remappingFunction) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(remappingFunction, "remappingFunction must not be null");
        return Optional.ofNullable(delegate.compute(key, remappingFunction));
    }

    @Override
    public Optional<Value> merge(
            final String key,
            final Value value,
            final BiFunction<? super Value, ? super Value, ? extends Value> remappingFunction) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(remappingFunction, "remappingFunction must not be null");
        return Optional.ofNullable(delegate.merge(key, value, remappingFunction));
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DefaultStore other)) {
            return false;
        }
        return delegate.equals(other.delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    private final class EntrySetView extends AbstractSet<Entry> {

        @Override
        public Iterator<Entry> iterator() {
            Iterator<Map.Entry<String, Value>> iterator = delegate.entrySet().iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Entry next() {
                    return new EntryView(iterator.next());
                }

                @Override
                public void remove() {
                    iterator.remove();
                }
            };
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public boolean contains(final Object object) {
            if (!(object instanceof Entry entry)) {
                return false;
            }
            Value value = delegate.get(entry.getKey());
            return Objects.equals(value, entry.getValue());
        }

        @Override
        public boolean remove(final Object object) {
            if (!(object instanceof Entry entry)) {
                return false;
            }
            return delegate.remove(entry.getKey(), entry.getValue());
        }
    }

    private static final class EntryView implements Entry {

        private final Map.Entry<String, Value> delegate;

        private EntryView(final Map.Entry<String, Value> delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getKey() {
            return delegate.getKey();
        }

        @Override
        public Value getValue() {
            return delegate.getValue();
        }

        @Override
        public Value setValue(final Value value) {
            Objects.requireNonNull(value, "value must not be null");
            return delegate.setValue(value);
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Entry other)) {
                return false;
            }
            return Objects.equals(getKey(), other.getKey()) && Objects.equals(getValue(), other.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getKey(), getValue());
        }

        @Override
        public String toString() {
            return getKey() + "=" + getValue();
        }
    }
}

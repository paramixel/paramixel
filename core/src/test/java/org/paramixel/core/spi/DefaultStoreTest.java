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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Store;
import org.paramixel.core.Value;

@DisplayName("DefaultStore")
class DefaultStoreTest {

    @Test
    @DisplayName("put and get round trip value")
    void putAndGetRoundTripValue() {
        Store store = new DefaultStore();

        store.put("key", Value.of("value"));

        assertThat(store.get("key").orElseThrow().cast(String.class)).isEqualTo("value");
    }

    @Test
    @DisplayName("get returns empty when key is absent")
    void getReturnsEmptyWhenKeyIsAbsent() {
        Store store = new DefaultStore();

        assertThat(store.get("missing")).isEmpty();
    }

    @Test
    @DisplayName("put rejects null key and value")
    void putRejectsNullKeyAndValue() {
        Store store = new DefaultStore();

        assertThatThrownBy(() -> store.put(null, Value.of("value")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key must not be null");
        assertThatThrownBy(() -> store.put("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value must not be null");
    }

    @Test
    @DisplayName("put returns previous value when replacing existing entry")
    void putReturnsPreviousValueWhenReplacingExistingEntry() {
        Store store = new DefaultStore();
        store.put("key", Value.of("before"));

        var previous = store.put("key", Value.of("after"));

        assertThat(previous).isPresent();
        assertThat(previous.orElseThrow().cast(String.class)).isEqualTo("before");
        assertThat(store.get("key").orElseThrow().cast(String.class)).isEqualTo("after");
    }

    @Test
    @DisplayName("remove returns optional value when present and empty when absent")
    void removeReturnsOptionalValueWhenPresentAndEmptyWhenAbsent() {
        Store store = new DefaultStore();
        store.put("key", Value.of("value"));

        var removed = store.remove("key");

        assertThat(removed).isPresent();
        assertThat(removed.orElseThrow().cast(String.class)).isEqualTo("value");
        assertThat(store.remove("key")).isEmpty();
    }

    @Test
    @DisplayName("entrySet setValue updates backing store")
    void entrySetSetValueUpdatesBackingStore() {
        Store store = new DefaultStore();
        store.put("key", Value.of("before"));

        Store.Entry entry = store.entrySet().iterator().next();
        Value previous = entry.setValue(Value.of("after"));

        assertThat(previous.cast(String.class)).isEqualTo("before");
        assertThat(store.get("key").orElseThrow().cast(String.class)).isEqualTo("after");
    }

    @Test
    @DisplayName("computeIfAbsent stores computed value")
    void computeIfAbsentStoresComputedValue() {
        Store store = new DefaultStore();

        Value value =
                store.computeIfAbsent("key", key -> Value.of(key + "-value")).orElseThrow();

        assertThat(value.cast(String.class)).isEqualTo("key-value");
        assertThat(store.get("key").orElseThrow().cast(String.class)).isEqualTo("key-value");
    }

    @Test
    @DisplayName("putIfAbsent returns existing value when key is present")
    void putIfAbsentReturnsExistingValueWhenKeyIsPresent() {
        Store store = new DefaultStore();
        store.put("key", Value.of("value"));

        var current = store.putIfAbsent("key", Value.of("other"));

        assertThat(current).isPresent();
        assertThat(current.orElseThrow().cast(String.class)).isEqualTo("value");
        assertThat(store.get("key").orElseThrow().cast(String.class)).isEqualTo("value");
    }

    @Test
    @DisplayName("map-like public methods reject null arguments")
    void mapLikePublicMethodsRejectNullArguments() {
        DefaultStore store = new DefaultStore();
        store.put("key", Value.of("value"));

        assertThatThrownBy(() -> store.containsKey(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key must not be null");
        assertThatThrownBy(() -> store.containsValue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value must not be null");
        assertThatThrownBy(() -> store.get(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key must not be null");
        assertThatThrownBy(() -> store.remove((String) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key must not be null");
        assertThatThrownBy(() -> store.getOrDefault("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("defaultValue must not be null");
        assertThatThrownBy(() -> store.forEach(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action must not be null");
        assertThatThrownBy(() -> store.replaceAll(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("function must not be null");
        assertThatThrownBy(() -> store.putIfAbsent("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value must not be null");
        assertThatThrownBy(() -> store.remove("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value must not be null");
        assertThatThrownBy(() -> store.replace("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value must not be null");
        assertThatThrownBy(() -> store.computeIfAbsent("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mappingFunction must not be null");
        assertThatThrownBy(() -> store.computeIfPresent("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("remappingFunction must not be null");
        assertThatThrownBy(() -> store.compute("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("remappingFunction must not be null");
        assertThatThrownBy(() -> store.merge("key", Value.of("x"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("remappingFunction must not be null");
    }

    @Test
    @DisplayName("entrySet setValue rejects null value")
    void entrySetSetValueRejectsNullValue() {
        Store store = new DefaultStore();
        store.put("key", Value.of("before"));

        Store.Entry entry = store.entrySet().iterator().next();

        assertThatThrownBy(() -> entry.setValue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value must not be null");
    }

    @Test
    @DisplayName("putAll copies entries from another store")
    void putAllCopiesEntriesFromAnotherStore() {
        Store source = new DefaultStore();
        source.put("one", Value.of(1));
        source.put("two", Value.of(2));

        Store target = new DefaultStore();
        target.putAll(source);

        assertThat(target.get("one").orElseThrow().cast(Integer.class)).isEqualTo(1);
        assertThat(target.get("two").orElseThrow().cast(Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("concurrent writes remain visible")
    void concurrentWritesRemainVisible() throws InterruptedException {
        Store store = new DefaultStore();
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        var executor = Executors.newFixedThreadPool(workers);
        try {
            for (int i = 0; i < workers; i++) {
                final int index = i;
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    store.put("key-" + index, Value.of(index));
                    done.countDown();
                    return null;
                });
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(store.size()).isEqualTo(workers);
        for (int i = 0; i < workers; i++) {
            assertThat(store.get("key-" + i).orElseThrow().cast(Integer.class)).isEqualTo(i);
        }
    }
}

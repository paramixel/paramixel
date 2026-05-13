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

package org.paramixel.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Store;

@DisplayName("DefaultStore")
class DefaultStoreTest {

    @Test
    @DisplayName("put and get round trip value")
    void putAndGetRoundTripValue() {
        Store store = new DefaultStore();

        store.put("key", "value");

        assertThat(store.get("key", String.class).orElseThrow()).isEqualTo("value");
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

        assertThatThrownBy(() -> store.put(null, "value"))
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
        store.put("key", "before");

        var previous = store.put("key", "after");

        assertThat(previous).isPresent();
        assertThat((String) previous.orElseThrow()).isEqualTo("before");
        assertThat(store.get("key", String.class).orElseThrow()).isEqualTo("after");
    }

    @Test
    @DisplayName("remove returns optional value when present and empty when absent")
    void removeReturnsOptionalValueWhenPresentAndEmptyWhenAbsent() {
        Store store = new DefaultStore();
        store.put("key", "value");

        var removed = store.remove("key");

        assertThat(removed).isPresent();
        assertThat((String) removed.orElseThrow()).isEqualTo("value");
        assertThat(store.remove("key")).isEmpty();
    }

    @Test
    @DisplayName("entrySet setValue updates backing store")
    void entrySetSetValueUpdatesBackingStore() {
        Store store = new DefaultStore();
        store.put("key", "before");

        Store.Entry entry = store.entrySet().iterator().next();
        Object previous = entry.setValue("after");

        assertThat((String) previous).isEqualTo("before");
        assertThat(store.get("key", String.class).orElseThrow()).isEqualTo("after");
    }

    @Test
    @DisplayName("computeIfAbsent stores computed value")
    void computeIfAbsentStoresComputedValue() {
        Store store = new DefaultStore();

        Object value = store.computeIfAbsent("key", key -> key + "-value").orElseThrow();

        assertThat((String) value).isEqualTo("key-value");
        assertThat(store.get("key", String.class).orElseThrow()).isEqualTo("key-value");
    }

    @Test
    @DisplayName("putIfAbsent returns existing value when key is present")
    void putIfAbsentReturnsExistingValueWhenKeyIsPresent() {
        Store store = new DefaultStore();
        store.put("key", "value");

        var current = store.putIfAbsent("key", "other");

        assertThat(current).isPresent();
        assertThat((String) current.orElseThrow()).isEqualTo("value");
        assertThat(store.get("key", String.class).orElseThrow()).isEqualTo("value");
    }

    @Test
    @DisplayName("map-like public methods reject null arguments")
    void mapLikePublicMethodsRejectNullArguments() {
        DefaultStore store = new DefaultStore();
        store.put("key", "value");

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
        assertThatThrownBy(() -> store.putIfAbsent(null, "value"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key must not be null");
        assertThatThrownBy(() -> store.putIfAbsent("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value must not be null");
        assertThatThrownBy(() -> store.remove(null, "value"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key must not be null");
        assertThatThrownBy(() -> store.remove("key", (Class<?>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("type must not be null");
        assertThatThrownBy(() -> store.replace("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value must not be null");
        assertThatThrownBy(() -> store.replace(null, "value"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key must not be null");
        assertThatThrownBy(() -> store.computeIfAbsent("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mappingFunction must not be null");
        assertThatThrownBy(() -> store.computeIfAbsent(null, k -> "value"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key must not be null");
        assertThatThrownBy(() -> store.computeIfPresent("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("remappingFunction must not be null");
        assertThatThrownBy(() -> store.compute("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("remappingFunction must not be null");
        assertThatThrownBy(() -> store.merge("key", "x", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("remappingFunction must not be null");
        assertThatThrownBy(() -> store.merge(null, "x", (v1, v2) -> v1))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key must not be null");
    }

    @Test
    @DisplayName("entrySet setValue rejects null value")
    void entrySetSetValueRejectsNullValue() {
        Store store = new DefaultStore();
        store.put("key", "before");

        Store.Entry entry = store.entrySet().iterator().next();

        assertThatThrownBy(() -> entry.setValue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value must not be null");
    }

    @Test
    @DisplayName("putAll copies entries from another store")
    void putAllCopiesEntriesFromAnotherStore() {
        Store source = new DefaultStore();
        source.put("one", 1);
        source.put("two", 2);

        Store target = new DefaultStore();
        target.putAll(source);

        assertThat(target.get("one", Integer.class).orElseThrow()).isEqualTo(1);
        assertThat(target.get("two", Integer.class).orElseThrow()).isEqualTo(2);
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
                    store.put("key-" + index, index);
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
            assertThat(store.get("key-" + i, Integer.class).orElseThrow()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("computeIfPresent remaps existing key")
    void computeIfPresentRemapsExistingKey() {
        Store store = new DefaultStore();
        store.put("key", "before");

        var result = store.computeIfPresent("key", (k, v) -> "after");

        assertThat(result).isPresent();
        assertThat((String) result.orElseThrow()).isEqualTo("after");
        assertThat(store.get("key", String.class).orElseThrow()).isEqualTo("after");
    }

    @Test
    @DisplayName("computeIfPresent returns empty for absent key")
    void computeIfPresentReturnsEmptyForAbsentKey() {
        Store store = new DefaultStore();

        var result = store.computeIfPresent("absent", (k, v) -> "value");

        assertThat(result).isEmpty();
        assertThat(store.get("absent")).isEmpty();
    }

    @Test
    @DisplayName("computeIfPresent rejects null key")
    void computeIfPresentRejectsNullKey() {
        Store store = new DefaultStore();
        assertThatThrownBy(() -> store.computeIfPresent(null, (k, v) -> "x")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("compute adds new key when absent")
    void computeAddsNewKeyWhenAbsent() {
        Store store = new DefaultStore();

        var result = store.compute("key", (k, v) -> "computed");

        assertThat(result).isPresent();
        assertThat((String) result.orElseThrow()).isEqualTo("computed");
        assertThat(store.get("key", String.class).orElseThrow()).isEqualTo("computed");
    }

    @Test
    @DisplayName("compute replaces existing key when present")
    void computeReplacesExistingKeyWhenPresent() {
        Store store = new DefaultStore();
        store.put("key", "before");

        var result = store.compute("key", (k, v) -> "after");

        assertThat(result).isPresent();
        assertThat((String) result.orElseThrow()).isEqualTo("after");
    }

    @Test
    @DisplayName("compute removes key when remapping function returns null")
    void computeRemovesKeyWhenRemappingFunctionReturnsNull() {
        Store store = new DefaultStore();
        store.put("key", "before");

        var result = store.compute("key", (k, v) -> null);

        assertThat(result).isEmpty();
        assertThat(store.get("key")).isEmpty();
    }

    @Test
    @DisplayName("compute rejects null key and null function")
    void computeRejectsNullKeyAndNullFunction() {
        Store store = new DefaultStore();
        assertThatThrownBy(() -> store.compute(null, (k, v) -> "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.compute("key", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("merge adds value for absent key")
    void mergeAddsValueForAbsentKey() {
        Store store = new DefaultStore();

        var result = store.merge("key", "value", (oldVal, newVal) -> "merged");

        assertThat(result).isPresent();
        assertThat((String) result.orElseThrow()).isEqualTo("value");
    }

    @Test
    @DisplayName("merge applies remapping function for present key")
    void mergeAppliesRemappingFunctionForPresentKey() {
        Store store = new DefaultStore();
        store.put("key", "old");

        var result = store.merge("key", "new", (oldVal, newVal) -> (String) oldVal + "-" + (String) newVal);

        assertThat(result).isPresent();
        assertThat((String) result.orElseThrow()).isEqualTo("old-new");
    }

    @Test
    @DisplayName("merge rejects null key, value, and function")
    void mergeRejectsNullKeyValueAndFunction() {
        Store store = new DefaultStore();
        assertThatThrownBy(() -> store.merge(null, "x", (o, n) -> "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.merge("key", null, (o, n) -> "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.merge("key", "x", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("replace(key, oldValue, newValue) succeeds when value matches")
    void replaceConditionalSucceedsWhenValueMatches() {
        Store store = new DefaultStore();
        store.put("key", "before");

        assertThat(store.replace("key", "before", "after")).isTrue();
        assertThat(store.get("key", String.class).orElseThrow()).isEqualTo("after");
    }

    @Test
    @DisplayName("replace(key, oldValue, newValue) fails when value does not match")
    void replaceConditionalFailsWhenValueDoesNotMatch() {
        Store store = new DefaultStore();
        store.put("key", "before");

        assertThat(store.replace("key", "wrong", "after")).isFalse();
        assertThat(store.get("key", String.class).orElseThrow()).isEqualTo("before");
    }

    @Test
    @DisplayName("replace(key, value) returns previous value when present")
    void replaceReturnsPreviousWhenPresent() {
        Store store = new DefaultStore();
        store.put("key", "before");

        var previous = store.replace("key", "after");

        assertThat(previous).isPresent();
        assertThat((String) previous.orElseThrow()).isEqualTo("before");
        assertThat(store.get("key", String.class).orElseThrow()).isEqualTo("after");
    }

    @Test
    @DisplayName("replace(key, value) returns empty when key is absent")
    void replaceReturnsEmptyWhenAbsent() {
        Store store = new DefaultStore();

        var previous = store.replace("absent", "after");

        assertThat(previous).isEmpty();
        assertThat(store.containsKey("absent")).isFalse();
    }

    @Test
    @DisplayName("remove(key, value) succeeds when value matches")
    void removeConditionalSucceedsWhenValueMatches() {
        Store store = new DefaultStore();
        store.put("key", "value");

        assertThat(store.remove("key", "value")).isTrue();
        assertThat(store.containsKey("key")).isFalse();
    }

    @Test
    @DisplayName("remove(key, value) fails when value does not match")
    void removeConditionalFailsWhenValueDoesNotMatch() {
        Store store = new DefaultStore();
        store.put("key", "value");

        assertThat(store.remove("key", "wrong")).isFalse();
        assertThat(store.containsKey("key")).isTrue();
    }

    @Test
    @DisplayName("keySet returns all keys")
    void keySetReturnsAllKeys() {
        Store store = new DefaultStore();
        store.put("a", 1);
        store.put("b", 2);

        assertThat(store.keySet()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("values returns all values")
    void valuesReturnsAllValues() {
        Store store = new DefaultStore();
        store.put("a", 1);
        store.put("b", 2);

        assertThat(store.values()).hasSize(2);
    }

    @Test
    @DisplayName("getOrDefault returns value when key is present")
    void getOrDefaultReturnsValueWhenKeyIsPresent() {
        Store store = new DefaultStore();
        store.put("key", "actual");

        Object result = store.getOrDefault("key", "default");
        assertThat((String) result).isEqualTo("actual");
    }

    @Test
    @DisplayName("getOrDefault returns default when key is absent")
    void getOrDefaultReturnsDefaultWhenKeyIsAbsent() {
        Store store = new DefaultStore();

        Object result = store.getOrDefault("absent", "default");
        assertThat((String) result).isEqualTo("default");
    }

    @Test
    @DisplayName("replaceAll replaces all values")
    void replaceAllReplacesAllValues() {
        Store store = new DefaultStore();
        store.put("a", "1");
        store.put("b", "2");

        store.replaceAll((key, value) -> (String) value + "-updated");

        assertThat(store.get("a", String.class).orElseThrow()).isEqualTo("1-updated");
        assertThat(store.get("b", String.class).orElseThrow()).isEqualTo("2-updated");
    }

    @Test
    @DisplayName("equals returns true for same content stores")
    void equalsReturnsTrueForSameContentStores() {
        Store store1 = new DefaultStore();
        store1.put("key", "value");
        Store store2 = new DefaultStore();
        store2.put("key", "value");

        assertThat(store1).isEqualTo(store2);
    }

    @Test
    @DisplayName("equals returns false for different content stores")
    void equalsReturnsFalseForDifferentContentStores() {
        Store store1 = new DefaultStore();
        store1.put("key", "value1");
        Store store2 = new DefaultStore();
        store2.put("key", "value2");

        assertThat(store1).isNotEqualTo(store2);
    }

    @Test
    @DisplayName("equals returns false for non-DefaultStore type")
    void equalsReturnsFalseForNonDefaultStoreType() {
        Store store = new DefaultStore();
        store.put("key", "value");

        assertThat(store).isNotEqualTo("not a store");
        assertThat(store).isNotEqualTo(null);
    }

    @Test
    @DisplayName("hashCode is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
        Store store1 = new DefaultStore();
        store1.put("key", "value");
        Store store2 = new DefaultStore();
        store2.put("key", "value");

        assertThat(store1.hashCode()).isEqualTo(store2.hashCode());
    }

    @Test
    @DisplayName("toString returns map representation")
    void toStringReturnsMapRepresentation() {
        Store store = new DefaultStore();
        store.put("key", "value");

        assertThat(store.toString()).contains("key").contains("value");
    }

    @Test
    @DisplayName("putAll rejects null store")
    void putAllRejectsNullStore() {
        Store store = new DefaultStore();
        assertThatThrownBy(() -> store.putAll(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("getOrDefault rejects null key and null default")
    void getOrDefaultRejectsNullKeyAndNullDefault() {
        Store store = new DefaultStore();
        assertThatThrownBy(() -> store.getOrDefault(null, "default")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.getOrDefault("key", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("replace(key, oldValue, newValue) rejects null arguments")
    void replaceConditionalRejectsNullArguments() {
        Store store = new DefaultStore();
        store.put("key", "value");
        assertThatThrownBy(() -> store.replace(null, "old", "new")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.replace("key", null, "new")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.replace("key", "old", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("computeIfPresent rejects null remappingFunction")
    void computeIfPresentRejectsNullRemappingFunction() {
        Store store = new DefaultStore();
        store.put("key", "value");
        assertThatThrownBy(() -> store.computeIfPresent("key", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("computeIfPresent rejects null key even when absent")
    void computeIfPresentRejectsNullKeyWhenAbsent() {
        Store store = new DefaultStore();
        assertThatThrownBy(() -> store.computeIfPresent(null, (k, v) -> "x")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("containsValue returns true for present value")
    void containsValueReturnsTrueForPresentValue() {
        Store store = new DefaultStore();
        store.put("key", "test");

        assertThat(store.containsValue("test")).isTrue();
    }

    @Test
    @DisplayName("containsValue returns false for absent value")
    void containsValueReturnsFalseForAbsentValue() {
        Store store = new DefaultStore();

        assertThat(store.containsValue("missing")).isFalse();
    }

    @Test
    @DisplayName("size and isEmpty work correctly")
    void sizeAndIsEmptyWorkCorrectly() {
        Store store = new DefaultStore();

        assertThat(store.isEmpty()).isTrue();
        assertThat(store.size()).isZero();

        store.put("key", "value");

        assertThat(store.isEmpty()).isFalse();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("clear removes all entries")
    void clearRemovesAllEntries() {
        Store store = new DefaultStore();
        store.put("a", 1);
        store.put("b", 2);

        store.clear();

        assertThat(store.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("entrySet contains entry and supports contains and remove by identity")
    void entrySetSupportsContainsAndRemove() {
        Store store = new DefaultStore();
        store.put("key", "value");

        assertThat(store.entrySet()).hasSize(1);
        assertThat(store.entrySet().iterator().next().getKey()).isEqualTo("key");
    }

    @Test
    @DisplayName("computeIfAbsent returns empty when function returns null")
    void computeIfAbsentReturnsEmptyWhenFunctionReturnsNull() {
        Store store = new DefaultStore();

        var result = store.computeIfAbsent("key", k -> null);

        assertThat(result).isEmpty();
        assertThat(store.get("key")).isEmpty();
    }

    @Test
    @DisplayName("forEach iterates over all entries")
    void forEachIteratesOverAllEntries() {
        Store store = new DefaultStore();
        store.put("a", 1);
        store.put("b", 2);

        AtomicInteger count = new AtomicInteger(0);
        store.forEach((k, v) -> count.incrementAndGet());

        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("get(String, Class<T>) returns Optional with correct type")
    void getWithTypeReturnsCorrectType() {
        Store store = new DefaultStore();
        store.put("key", "value");

        assertThat(store.get("key", String.class)).contains("value");
    }

    @Test
    @DisplayName("get(String, Class<T>) throws ClassCastException for wrong type")
    void getWithTypeThrowsClassCastExceptionForWrongType() {
        Store store = new DefaultStore();
        store.put("key", "value");

        assertThatThrownBy(() -> store.get("key", Integer.class)).isInstanceOf(ClassCastException.class);
    }

    @Test
    @DisplayName("remove(String, Class<T>) returns Optional with correct type")
    void removeWithTypeReturnsCorrectType() {
        Store store = new DefaultStore();
        store.put("key", "value");

        assertThat(store.remove("key", String.class)).contains("value");
    }

    @Test
    @DisplayName("getOrDefault(String, Class<T>, T) returns typed value when present")
    void getOrDefaultWithTypeReturnsValueWhenPresent() {
        Store store = new DefaultStore();
        store.put("key", "actual");

        assertThat(store.getOrDefault("key", String.class, "default")).isEqualTo("actual");
    }

    @Test
    @DisplayName("getOrDefault(String, Class<T>, T) returns default when absent")
    void getOrDefaultWithTypeReturnsDefaultWhenAbsent() {
        Store store = new DefaultStore();

        assertThat(store.getOrDefault("absent", String.class, "default")).isEqualTo("default");
    }

    @Test
    @DisplayName("isType returns true when key present and type matches")
    void isTypeReturnsTrueWhenKeyPresentAndTypeMatches() {
        Store store = new DefaultStore();
        store.put("key", "value");

        assertThat(store.isType("key", String.class)).isTrue();
    }

    @Test
    @DisplayName("isType returns false when key present but type does not match")
    void isTypeReturnsFalseWhenKeyPresentButTypeDoesNotMatch() {
        Store store = new DefaultStore();
        store.put("key", "value");

        assertThat(store.isType("key", Integer.class)).isFalse();
    }

    @Test
    @DisplayName("isType returns false when key absent")
    void isTypeReturnsFalseWhenKeyAbsent() {
        Store store = new DefaultStore();

        assertThat(store.isType("absent", String.class)).isFalse();
    }

    @Test
    @DisplayName("isType rejects null key and null type")
    void isTypeRejectsNullKeyAndNullType() {
        Store store = new DefaultStore();
        store.put("key", "value");

        assertThatThrownBy(() -> store.isType(null, String.class)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.isType("key", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("get(String, Class<T>) rejects null key and null type")
    void getWithTypeRejectsNullKeyAndNullType() {
        Store store = new DefaultStore();
        store.put("key", "value");

        assertThatThrownBy(() -> store.get(null, String.class)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.get("key", null)).isInstanceOf(NullPointerException.class);
    }
}

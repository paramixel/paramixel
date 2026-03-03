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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Store;

public class ConcreteStoreTest {

    @Test
    public void constructor_withInitialCapacity_acceptsNegativeAndBehavesNormally() {
        final Store store = new ConcreteStore(-5);

        assertThat(store.put("k", "v")).isNull();
        assertThat(store.get("k", String.class)).isEqualTo("v");
    }

    @Test
    public void putGetRemove_roundTrip() {
        final Store store = new ConcreteStore();

        assertThat(store.get("missing")).isNull();
        assertThat(store.contains("missing")).isFalse();
        assertThat(store.size()).isZero();

        assertThat(store.put("k1", "v1")).isNull();
        assertThat(store.get("k1")).isEqualTo("v1");
        assertThat(store.contains("k1")).isTrue();
        assertThat(store.size()).isEqualTo(1);

        assertThat(store.put("k1", "v2")).isEqualTo("v1");
        assertThat(store.get("k1")).isEqualTo("v2");
        assertThat(store.size()).isEqualTo(1);

        assertThat(store.remove("k1")).isEqualTo("v2");
        assertThat(store.get("k1")).isNull();
        assertThat(store.contains("k1")).isFalse();
        assertThat(store.size()).isZero();
    }

    @Test
    public void putNull_behavesLikeRemove() {
        final Store store = new ConcreteStore();

        store.put("k", "v");
        assertThat(store.put("k", null)).isEqualTo("v");
        assertThat(store.get("k")).isNull();
        assertThat(store.contains("k")).isFalse();
    }

    @Test
    public void getTyped_returnsNullWhenAbsent_andThrowsOnMismatch() {
        final Store store = new ConcreteStore();

        assertThat(store.get("missing", String.class)).isNull();

        store.put("k", 123);
        assertThat(store.get("k", Integer.class)).isEqualTo(123);

        assertThatThrownBy(() -> store.get("k", String.class))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("not assignable");
        assertThat(store.get("k", Integer.class)).isEqualTo(123);
    }

    @Test
    public void findTyped_returnsOptional_andThrowsOnMismatch() {
        final Store store = new ConcreteStore();

        assertThat(store.find("missing", String.class)).isEmpty();

        store.put("k", "v");
        assertThat(store.find("k", String.class)).contains("v");

        assertThatThrownBy(() -> store.find("k", Integer.class)).isInstanceOf(ClassCastException.class);
        assertThat(store.find("k", String.class)).contains("v");
    }

    @Test
    public void removeTyped_returnsNullWhenAbsent_andDoesNotRemoveOnMismatch() {
        final Store store = new ConcreteStore();

        assertThat(store.remove("missing", String.class)).isNull();

        store.put("k", "v");
        assertThatThrownBy(() -> store.remove("k", Integer.class)).isInstanceOf(ClassCastException.class);
        assertThat(store.get("k", String.class)).isEqualTo("v");
        assertThat(store.remove("k", String.class)).isEqualTo("v");
        assertThat(store.get("k")).isNull();
    }

    @Test
    public void putTyped_throwsOnValueMismatch_andDoesNotStoreValue() {
        final Store store = new ConcreteStore();

        @SuppressWarnings({"rawtypes", "unchecked"})
        final Class rawNumber = Number.class;
        assertThatThrownBy(() -> store.put("k", rawNumber, "bad")).isInstanceOf(ClassCastException.class);
        assertThat(store.get("k")).isNull();
    }

    @Test
    public void putTyped_throwsWhenPreviousValueIsWrongType_andStillStoresNewValue() {
        final Store store = new ConcreteStore();

        store.put("k", 123);

        assertThatThrownBy(() -> store.put("k", String.class, "v"))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("previous value");

        assertThat(store.get("k")).isEqualTo("v");
        assertThat(store.get("k", String.class)).isEqualTo("v");
    }

    @Test
    public void putTyped_returnsNullWhenNoPreviousValue_andAllowsNullToRemove() {
        final Store store = new ConcreteStore();

        assertThat(store.put("k", String.class, "v")).isNull();
        assertThat(store.get("k", String.class)).isEqualTo("v");

        assertThat(store.put("k", String.class, null)).isEqualTo("v");
        assertThat(store.get("k")).isNull();
    }

    @Test
    public void computeIfAbsentRaw_storesWhenSupplierNonNull_andLeavesUnmappedWhenNull() {
        final Store store = new ConcreteStore();

        final AtomicInteger calls = new AtomicInteger(0);
        final Object created = store.computeIfAbsent("k", () -> {
            calls.incrementAndGet();
            return "v";
        });

        assertThat(created).isEqualTo("v");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(store.get("k")).isEqualTo("v");

        final Object nullCreated = store.computeIfAbsent("nullKey", () -> null);
        assertThat(nullCreated).isNull();
        assertThat(store.contains("nullKey")).isFalse();
    }

    @Test
    public void computeIfAbsentTyped_typeChecksExisting_andTypeChecksSupplierResult() {
        final Store store = new ConcreteStore();

        store.put("k", "v");
        assertThatThrownBy(() -> store.computeIfAbsent("k", Integer.class, () -> 1))
                .isInstanceOf(ClassCastException.class);
        assertThat(store.get("k", String.class)).isEqualTo("v");

        @SuppressWarnings({"rawtypes", "unchecked"})
        final Class rawInteger = Integer.class;
        assertThatThrownBy(() -> store.computeIfAbsent("k2", rawInteger, () -> "bad"))
                .isInstanceOf(ClassCastException.class);
        assertThat(store.get("k2")).isNull();

        assertThat(store.computeIfAbsent("k3", String.class, () -> null)).isNull();
        assertThat(store.contains("k3")).isFalse();

        assertThat(store.computeIfAbsent("k4", String.class, () -> "ok")).isEqualTo("ok");
        assertThat(store.get("k4", String.class)).isEqualTo("ok");
    }

    @Test
    public void computeIfAbsentTyped_concurrentCalls_returnSameInstance() throws Exception {
        final Store store = new ConcreteStore();
        final AtomicInteger supplierCalls = new AtomicInteger(0);
        final Supplier<Object> supplier = () -> {
            supplierCalls.incrementAndGet();
            return new Object();
        };

        final int threads = 32;
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);

        try {
            final List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return store.computeIfAbsent("k", Object.class, supplier);
                }));
            }

            start.countDown();

            final Object first = futures.get(0).get(10, TimeUnit.SECONDS);
            assertThat(first).isNotNull();

            for (Future<Object> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isSameAs(first);
            }

            assertThat(supplierCalls.get()).isEqualTo(1);
            assertThat(store.get("k", Object.class)).isSameAs(first);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void clear_removesAllEntries() {
        final Store store = new ConcreteStore();
        store.put("a", 1);
        store.put("b", 2);
        assertThat(store.size()).isEqualTo(2);

        store.clear();
        assertThat(store.size()).isZero();
        assertThat(store.contains("a")).isFalse();
        assertThat(store.contains("b")).isFalse();
    }

    @Test
    public void keys_iteratorIncludesCurrentKeys() {
        final Store store = new ConcreteStore();
        store.put("a", 1);
        store.put("b", 2);

        final Set<String> keys = new HashSet<>();
        store.keyIterator().forEachRemaining(keys::add);

        assertThat(keys).contains("a", "b");
    }

    @Test
    public void nullKey_throwsNullPointerException() {
        final Store store = new ConcreteStore();

        assertThatThrownBy(() -> store.get(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.contains(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.put(null, "v")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.remove(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.computeIfAbsent(null, () -> "v")).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> store.get(null, String.class)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.get("k", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.find(null, String.class)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.find("k", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.remove(null, String.class)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.remove("k", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.put(null, String.class, "v")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.put("k", null, "v")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.computeIfAbsent(null, String.class, () -> "v"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.computeIfAbsent("k", null, () -> "v")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.computeIfAbsent("k", String.class, null))
                .isInstanceOf(NullPointerException.class);
    }
}

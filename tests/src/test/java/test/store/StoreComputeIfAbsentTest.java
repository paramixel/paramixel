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

package test.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Store;

@Paramixel.TestClass
/**
 * Verifies Store computeIfAbsent behavior and type safety.
 *
 * <p>This test exercises the computeIfAbsent methods for raw and typed values,
 * verifying proper caching and type checking behavior.
 */
public class StoreComputeIfAbsentTest {

    private static final String KEY = "computed";
    private static final AtomicInteger supplierCalls = new AtomicInteger(0);
    private static final AtomicInteger testCount = new AtomicInteger(0);

    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.addArgument("test");
    }

    @Paramixel.Test
    public void testComputeIfAbsentTyped(final @NonNull ArgumentContext context) {
        Store store = context.getStore();

        // First call should invoke supplier
        String value1 = store.computeIfAbsent(KEY, String.class, () -> {
            supplierCalls.incrementAndGet();
            return "computed-value";
        });
        assertThat(value1).isEqualTo("computed-value");
        assertThat(supplierCalls.get()).isEqualTo(1);

        // Second call should return cached value
        String value2 = store.computeIfAbsent(KEY, String.class, () -> {
            supplierCalls.incrementAndGet();
            return "different-value";
        });
        assertThat(value2).isEqualTo("computed-value"); // Should be original value
        assertThat(supplierCalls.get()).isEqualTo(1); // Supplier not called again

        testCount.incrementAndGet();
    }

    @Paramixel.Test
    public void testComputeIfAbsentRaw(final @NonNull ArgumentContext context) {
        Store store = context.getStore();
        String rawKey = "raw-computed";

        // Raw computeIfAbsent
        Object value1 = store.computeIfAbsent(rawKey, () -> "raw-value");
        assertThat(value1).isEqualTo("raw-value");

        // Verify cached
        Object value2 = store.computeIfAbsent(rawKey, () -> "different");
        assertThat(value2).isEqualTo("raw-value");

        testCount.incrementAndGet();
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        assertThat(testCount.get()).isEqualTo(2);
        assertThat(supplierCalls.get()).isEqualTo(1);
    }
}

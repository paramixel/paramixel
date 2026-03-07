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

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for {@link ConcreteStore} operations.
 *
 * <p>Benchmarks measure the throughput of common Store operations including
 * put, get, contains, remove, and computeIfAbsent under various conditions.</p>
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(0)
public class ConcreteStoreBenchmark {

    /**
     * Creates a new benchmark instance.
     */
    public ConcreteStoreBenchmark() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Stores the store.
     */
    private ConcreteStore store;
    /**
     * Stores the TEST_KEY.
     */
    private static final String TEST_KEY = "test-key";
    /**
     * Stores the TEST_VALUE.
     */
    private static final String TEST_VALUE = "test-value";
    /**
     * Stores the PREPOPULATE_SIZE.
     */
    private static final int PREPOPULATE_SIZE = 1000;

    /**
     * Sets up the store before each benchmark iteration.
     */
    @Setup
    public void setup() {
        store = new ConcreteStore(PREPOPULATE_SIZE);
    }

    /**
     * Cleans up the store after each benchmark iteration.
     */
    @TearDown
    public void tearDown() {
        store.clear();
    }

    /**
     * Benchmarks simple put operation.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void putOperation(final Blackhole blackhole) {
        final Object previous = store.put(TEST_KEY, TEST_VALUE);
        blackhole.consume(previous);
    }

    /**
     * Benchmarks put with type checking.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void putTypedOperation(final Blackhole blackhole) {
        final String previous = store.put(TEST_KEY, String.class, TEST_VALUE);
        blackhole.consume(previous);
    }

    /**
     * Benchmarks get operation on existing key.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void getExistingKey(final Blackhole blackhole) {
        store.put(TEST_KEY, TEST_VALUE);
        final Object value = store.get(TEST_KEY);
        blackhole.consume(value);
    }

    /**
     * Benchmarks get with type checking on existing key.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void getTypedExistingKey(final Blackhole blackhole) {
        store.put(TEST_KEY, TEST_VALUE);
        final String value = store.get(TEST_KEY, String.class);
        blackhole.consume(value);
    }

    /**
     * Benchmarks contains operation on existing key.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void containsExistingKey(final Blackhole blackhole) {
        store.put(TEST_KEY, TEST_VALUE);
        final boolean exists = store.contains(TEST_KEY);
        blackhole.consume(exists);
    }

    /**
     * Benchmarks remove operation.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void removeOperation(final Blackhole blackhole) {
        store.put(TEST_KEY, TEST_VALUE);
        final Object removed = store.remove(TEST_KEY);
        blackhole.consume(removed);
    }

    /**
     * Benchmarks remove with type checking.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void removeTypedOperation(final Blackhole blackhole) {
        store.put(TEST_KEY, TEST_VALUE);
        final String removed = store.remove(TEST_KEY, String.class);
        blackhole.consume(removed);
    }

    /**
     * Benchmarks computeIfAbsent when key is absent.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void computeIfAbsentKeyAbsent(final Blackhole blackhole) {
        final Object value = store.computeIfAbsent(TEST_KEY, () -> TEST_VALUE);
        blackhole.consume(value);
    }

    /**
     * Benchmarks computeIfAbsent when key already exists.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void computeIfAbsentKeyExists(final Blackhole blackhole) {
        store.put(TEST_KEY, TEST_VALUE);
        final Object value = store.computeIfAbsent(TEST_KEY, () -> "new-value");
        blackhole.consume(value);
    }

    /**
     * Benchmarks size operation.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void sizeOperation(final Blackhole blackhole) {
        final int size = store.size();
        blackhole.consume(size);
    }

    /**
     * Benchmarks mixed workload simulating realistic usage.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void mixedWorkload(final Blackhole blackhole) {
        final String key = "key-" + System.nanoTime() % 100;

        // 60% reads
        final Object value = store.get(key);
        blackhole.consume(value);

        // 20% writes
        if (System.nanoTime() % 5 == 0) {
            final Object previous = store.put(key, TEST_VALUE);
            blackhole.consume(previous);
        }

        // 10% contains checks
        if (System.nanoTime() % 10 == 0) {
            final boolean exists = store.contains(key);
            blackhole.consume(exists);
        }

        // 10% removals
        if (System.nanoTime() % 10 == 1) {
            final Object removed = store.remove(key);
            blackhole.consume(removed);
        }
    }
}

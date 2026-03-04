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

package org.paramixel.engine.util;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for {@link FastIdUtil} ID generation utility.
 *
 * <p>Benchmarks measure the throughput and latency of FastIdUtil.getId()
 * under various ID length configurations.</p>
 *
 * @since 0.0.1
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class FastIdBenchmark {

    /**
     * Benchmarks generation of 6-character IDs (typical thread name suffix).
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void generateId6Characters(final Blackhole blackhole) {
        final String id = FastIdUtil.getId(6);
        blackhole.consume(id);
    }

    /**
     * Benchmarks generation of 8-character IDs.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void generateId8Characters(final Blackhole blackhole) {
        final String id = FastIdUtil.getId(8);
        blackhole.consume(id);
    }

    /**
     * Benchmarks generation of 12-character IDs.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void generateId12Characters(final Blackhole blackhole) {
        final String id = FastIdUtil.getId(12);
        blackhole.consume(id);
    }

    /**
     * Benchmarks generation of 16-character IDs.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void generateId16Characters(final Blackhole blackhole) {
        final String id = FastIdUtil.getId(16);
        blackhole.consume(id);
    }

    /**
     * Benchmarks generation of 32-character IDs (long IDs).
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void generateId32Characters(final Blackhole blackhole) {
        final String id = FastIdUtil.getId(32);
        blackhole.consume(id);
    }
}

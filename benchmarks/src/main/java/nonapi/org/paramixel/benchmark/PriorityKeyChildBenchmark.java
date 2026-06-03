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

package nonapi.org.paramixel.benchmark;

import java.util.concurrent.TimeUnit;
import nonapi.org.paramixel.action.SchedulerPriorityKey;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Micro-benchmark measuring throughput of {@link SchedulerPriorityKey#child(int)}.
 *
 * <p>Isolates the cost of ConcurrentHashMap lookup with Integer boxing vs. array-based cache.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@Threads(1)
public class PriorityKeyChildBenchmark {

    @Benchmark
    public void childLookup(PriorityKeyState state, Blackhole blackhole) {
        for (var i = 0; i < state.callCount; i++) {
            blackhole.consume(state.parentKey.child(i % state.maxChildIndex));
        }
    }

    @State(Scope.Benchmark)
    public static class PriorityKeyState {

        @Param({"15", "63", "255"})
        private int maxChildIndex;

        @Param({"1000", "10000"})
        private int callCount;

        private SchedulerPriorityKey parentKey;

        @Setup(Level.Trial)
        public void setup() {
            parentKey = SchedulerPriorityKey.root();
            // Pre-populate cache for all indices we'll access
            for (var i = 0; i < maxChildIndex; i++) {
                parentKey.child(i);
            }
        }
    }
}

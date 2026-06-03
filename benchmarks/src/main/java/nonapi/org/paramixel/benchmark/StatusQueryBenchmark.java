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
import org.paramixel.api.Status;

/**
 * Micro-benchmark measuring throughput of {@link Status} query methods.
 *
 * <p>Isolates the cost of string comparison ({@code "PASSED".equals(statusName)})
 * vs. identity comparison ({@code this == PASSED}).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@Threads(1)
public class StatusQueryBenchmark {

    @Benchmark
    public void queryConstants(QueryState state, Blackhole blackhole) {
        for (var i = 0; i < state.queryCount; i++) {
            blackhole.consume(Status.PASSED.isPassed());
            blackhole.consume(Status.FAILED.isFailed());
            blackhole.consume(Status.SKIPPED.isSkipped());
            blackhole.consume(Status.ABORTED.isAborted());
            blackhole.consume(Status.PENDING.isPending());
        }
    }

    @Benchmark
    public void queryDynamic(QueryState state, Blackhole blackhole) {
        for (var i = 0; i < state.queryCount; i++) {
            blackhole.consume(state.dynamicPassed.isPassed());
            blackhole.consume(state.dynamicFailed.isFailed());
            blackhole.consume(state.dynamicSkipped.isSkipped());
            blackhole.consume(state.dynamicAborted.isAborted());
            blackhole.consume(state.dynamicPending.isPending());
        }
    }

    @State(Scope.Benchmark)
    public static class QueryState {

        @Param({"10000", "100000"})
        private int queryCount;

        private Status dynamicPassed;
        private Status dynamicFailed;
        private Status dynamicSkipped;
        private Status dynamicAborted;
        private Status dynamicPending;

        @Setup(Level.Trial)
        public void setup() {
            dynamicPassed = Status.PASSED;
            dynamicFailed = Status.failed("test");
            dynamicSkipped = Status.skipped("test");
            dynamicAborted = Status.aborted("test");
            dynamicPending = Status.PENDING;
        }
    }
}

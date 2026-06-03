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
import nonapi.org.paramixel.action.ConcreteDescriptor;
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
import org.paramixel.api.action.Step;

/**
 * Micro-benchmark measuring throughput of ConcreteDescriptor status read methods.
 *
 * <p>Isolates the synchronization overhead of {@code status()}, {@code isPassed()},
 * {@code isFailed()}, {@code isCompleted()} on a descriptor with a pre-set terminal status.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@Threads(1)
public class DescriptorStatusReadBenchmark {

    @Benchmark
    public void statusReads(StatusReadState state, Blackhole blackhole) {
        var d = state.descriptor;
        for (var i = 0; i < state.readCount; i++) {
            blackhole.consume(d.status());
            blackhole.consume(d.isPassed());
            blackhole.consume(d.isFailed());
            blackhole.consume(d.isCompleted());
        }
    }

    @State(Scope.Benchmark)
    public static class StatusReadState {

        @Param({"1000", "10000", "100000"})
        private int readCount;

        private ConcreteDescriptor descriptor;

        @Setup(Level.Trial)
        public void setup() {
            var action = Step.of("bench", ctx -> {});
            descriptor = new ConcreteDescriptor(action);
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.PASSED);
        }
    }
}

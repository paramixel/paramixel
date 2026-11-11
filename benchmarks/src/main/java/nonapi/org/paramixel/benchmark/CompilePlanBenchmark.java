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

import java.util.Map;
import java.util.concurrent.TimeUnit;
import nonapi.org.paramixel.compiler.PlanCompiler;
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
import org.paramixel.api.Configuration;
import org.paramixel.api.Pool;
import org.paramixel.api.Task;
import org.paramixel.api.Tasks;

/**
 * Measures definition build and plan compilation throughput for a fixed tree shape.
 *
 * <p>Compilation is pure and must stay side-effect free, so this benchmark can compile the same definition repeatedly
 * without invoking user code. A near-linear cost in the number of occurrences is the documented target.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@Threads(1)
public class CompilePlanBenchmark {

    @Benchmark
    public void buildTree(final BuilderState state, final Blackhole blackhole) {
        blackhole.consume(state.definition);
    }

    @Benchmark
    public void compilePlan(final BuilderState state, final Blackhole blackhole) {
        blackhole.consume(state.compiler.compile(state.definition));
    }

    @State(Scope.Benchmark)
    public static class BuilderState {

        @Param({"10", "100", "1000"})
        private int size;

        private Task definition;
        private PlanCompiler compiler;

        @Setup(Level.Trial)
        public void setup() {
            var builder = Tasks.parallel("root", Pool.named("p"));
            for (var i = 0; i < size; i++) {
                builder.step("step-" + i, () -> {});
            }
            definition = builder.build();
            compiler = new PlanCompiler(Configuration.of(Map.of("paramixel.parallelism.p", "8")), Map.of());
        }
    }
}

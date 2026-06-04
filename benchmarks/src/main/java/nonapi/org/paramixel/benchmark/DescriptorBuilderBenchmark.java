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
import nonapi.org.paramixel.action.DescriptorBuilder;
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
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Step;

/**
 * Benchmark measuring descriptor tree construction time with varying depths and widths.
 *
 * <p>Isolates the cost of {@link DescriptorBuilder#discover} including cycle detection
 * and SchedulerPriorityKey assignment.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@Threads(1)
public class DescriptorBuilderBenchmark {

    @Benchmark
    public void buildTree(BuilderState state, Blackhole blackhole) {
        for (var i = 0; i < state.iterations; i++) {
            blackhole.consume(state.builder.discover(state.rootAction));
        }
    }

    @State(Scope.Benchmark)
    public static class BuilderState {

        @Param({"5", "10", "20"})
        private int treeDepth;

        @Param({"10", "50"})
        private int childrenPerNode;

        @Param({"10", "100"})
        private int iterations;

        private DescriptorBuilder builder;
        private Action rootAction;

        @Setup(Level.Trial)
        public void setup() {
            builder = new DescriptorBuilder();
            rootAction = buildTree(treeDepth, childrenPerNode);
        }

        private Action buildTree(final int depth, final int width) {
            if (depth == 0) {
                return Step.of("leaf", ctx -> {});
            }
            var parallel = Parallel.builder("node-" + depth).parallelism(width);
            for (var i = 0; i < width; i++) {
                parallel.child(buildTree(depth - 1, width));
            }
            return parallel.build();
        }
    }
}

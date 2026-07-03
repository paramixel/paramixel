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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.listener.Listeners;
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
import org.paramixel.api.Descriptor;
import org.paramixel.api.action.Step;

/**
 * Micro-benchmark measuring throughput of {@link Listeners#formatIdPath(Descriptor)}.
 *
 * <p>Measures the cost of walking the parent chain and joining id strings for
 * descriptor trees of varying depth and leaf count.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@Threads(1)
public class FormatIdPathBenchmark {

    @Benchmark
    public void formatIdPathFirstCall(DescriptorTreeState state, Blackhole blackhole) {
        for (var leaf : state.leaves) {
            blackhole.consume(Listeners.formatIdPath(leaf));
        }
    }

    @Benchmark
    public void formatIdPathCachedCall(DescriptorTreeState state, Blackhole blackhole) {
        // First call populates cache; subsequent calls hit cache
        // This simulates the scheduler pattern: same descriptors called repeatedly
        for (var i = 0; i < state.repeatCount; i++) {
            for (var leaf : state.leaves) {
                blackhole.consume(Listeners.formatIdPath(leaf));
            }
        }
    }

    @State(Scope.Benchmark)
    public static class DescriptorTreeState {

        @Param({"100", "1000"})
        private int leafCount;

        @Param({"3", "5", "10"})
        private int depth;

        @Param({"10"})
        private int repeatCount;

        List<Descriptor> leaves;

        @Setup(Level.Trial)
        public void setup() {
            leaves = new ArrayList<>();
            var root = new ConcreteDescriptor(Step.of("root", context -> {}));
            buildTree(root, 1, leafCount, depth);
            root.freeze();
        }

        private void buildTree(
                final ConcreteDescriptor parent, final int currentDepth, final int totalLeaves, final int maxDepth) {
            if (currentDepth >= maxDepth) {
                // This level becomes leaves
                for (var i = 0; i < totalLeaves; i++) {
                    var leaf = new ConcreteDescriptor(parent, Step.of("leaf-" + i, context -> {}));
                    parent.addChild(leaf);
                    leaves.add(leaf);
                }
                return;
            }

            // Distribute leaves across children at this level
            // Use branching factor that creates a balanced tree
            var branchingFactor = Math.max(2, (int) Math.ceil(Math.pow(totalLeaves, 1.0 / (maxDepth - currentDepth))));
            var leavesPerChild = totalLeaves / branchingFactor;
            var remainder = totalLeaves % branchingFactor;

            for (var i = 0; i < branchingFactor; i++) {
                var childLeaves = leavesPerChild + (i < remainder ? 1 : 0);
                if (childLeaves <= 0) {
                    continue;
                }
                var child = new ConcreteDescriptor(parent, Step.of("node-" + currentDepth + "-" + i, context -> {}));
                parent.addChild(child);
                buildTree(child, currentDepth + 1, childLeaves, maxDepth);
            }
        }
    }
}

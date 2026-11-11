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
import org.paramixel.api.Listener;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;
import org.paramixel.api.Tasks;

/**
 * JMH benchmarks for the Paramixel scheduler.
 *
 * <p>Each benchmark runs a complete plan through the public runner with a no-op listener, so the measured cost is
 * compilation, scheduling, synchronization, and result assembly rather than console output.
 *
 * <pre>{@code
 * java -jar benchmarks/target/benchmarks.jar SchedulerBenchmark
 * }</pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@Threads(1)
public class SchedulerBenchmark {

    /**
     * Measures parallel throughput with heterogeneous child durations.
     *
     * @param state the benchmark state
     * @param blackhole the JMH sink
     */
    @Benchmark
    public void runParallelHeterogeneousDurations(final ParallelHeterogeneousState state, final Blackhole blackhole) {
        blackhole.consume(state.runner.run(state.root));
    }

    /**
     * Measures parallel throughput with uniform child durations.
     *
     * @param state the benchmark state
     * @param blackhole the JMH sink
     */
    @Benchmark
    public void runParallelUniformDurations(final ParallelUniformState state, final Blackhole blackhole) {
        blackhole.consume(state.runner.run(state.root));
    }

    /**
     * Measures scheduling overhead for many trivial steps.
     *
     * @param state the benchmark state
     * @param blackhole the JMH sink
     */
    @Benchmark
    public void scheduleManySmallTasks(final ManySmallTasksState state, final Blackhole blackhole) {
        blackhole.consume(state.runner.run(state.root));
    }

    private static Runner runner(final int capacity) {
        return Runner.builder()
                .configuration(Configuration.of(Map.of("paramixel.ansi", "false")))
                .listener(Listener.NOOP)
                .pool(Pool.named("bench"), capacity)
                .build();
    }

    /**
     * State for the heterogeneous-duration benchmark.
     */
    @State(Scope.Benchmark)
    public static class ParallelHeterogeneousState {

        @Param({"4", "8", "16"})
        private int parallelism;

        @Param({"8", "32", "128"})
        private int childCount;

        private Runner runner;
        private Task root;

        @Setup(Level.Trial)
        public void setup() {
            runner = SchedulerBenchmark.runner(parallelism);
            final var builder = Tasks.parallel("root", Pool.named("bench"));
            for (var i = 0; i < childCount; i++) {
                final long sleepMillis = i % 4 == 0 ? 5L : 1L;
                builder.step("child-" + i, () -> {
                    Blackhole.consumeCPU(1);
                    try {
                        Thread.sleep(sleepMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            root = builder.build();
        }
    }

    /**
     * State for the uniform-duration benchmark.
     */
    @State(Scope.Benchmark)
    public static class ParallelUniformState {

        @Param({"4", "8", "16"})
        private int parallelism;

        @Param({"8", "32", "128"})
        private int childCount;

        private Runner runner;
        private Task root;

        @Setup(Level.Trial)
        public void setup() {
            runner = SchedulerBenchmark.runner(parallelism);
            final var builder = Tasks.parallel("root", Pool.named("bench"));
            for (var i = 0; i < childCount; i++) {
                builder.step("child-" + i, () -> Thread.sleep(1L));
            }
            root = builder.build();
        }
    }

    /**
     * State for the many-small-tasks benchmark.
     */
    @State(Scope.Benchmark)
    public static class ManySmallTasksState {

        @Param({"100", "1000", "10000"})
        private int childCount;

        private Runner runner;
        private Task root;

        @Setup(Level.Trial)
        public void setup() {
            runner = SchedulerBenchmark.runner(8);
            final var builder = Tasks.parallel("root", Pool.named("bench"));
            for (var i = 0; i < childCount; i++) {
                builder.step("step-" + i, () -> {});
            }
            root = builder.build();
        }
    }
}

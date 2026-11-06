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
import nonapi.org.paramixel.ExecutionMode;
import nonapi.org.paramixel.InstanceHolder;
import nonapi.org.paramixel.Scheduler;
import nonapi.org.paramixel.action.ConcreteContext;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * JMH benchmarks for the Paramixel scheduler.
 *
 * <p>Run with:
 *
 * <pre>{@code
 * java -jar benchmarks/target/benchmarks.jar
 * }</pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@Threads(1)
public class SchedulerBenchmark {

    /**
     * Benchmark parallel throughput with heterogeneous child durations.
     *
     * <p>Measures how efficiently the scheduler utilizes parallelism when children have varying
     * execution times. This is the key scenario where the rolling window optimization provides
     * improvement over batch processing.
     */
    @Benchmark
    public void runParallelHeterogeneousDurations(ParallelHeterogeneousState state, Blackhole blackhole) {
        var result = state.runner().run(state.root());
        blackhole.consume(result);
    }

    /**
     * Benchmark scheduling admission throughput with many small tasks.
     *
     * <p>Measures the overhead of the scheduling path itself (lock acquisition, permit handling,
     * task submission) when actions are trivial.
     */
    @Benchmark
    public void scheduleManySmallTasks(ManySmallTasksState state, Blackhole blackhole) {
        var result = state.runner().run(state.root());
        blackhole.consume(result);
    }

    /**
     * Benchmark parallel throughput with uniform child durations.
     *
     * <p>Baseline scenario — no benefit from rolling window, should show no regression.
     */
    @Benchmark
    public void runParallelUniformDurations(ParallelUniformState state, Blackhole blackhole) {
        var result = state.runner().run(state.root());
        blackhole.consume(result);
    }

    /**
     * Benchmark a single sequential chain of trivial steps.
     *
     * <p>Only one descriptor is ready at a time, so this shape measures the per-child hand-off:
     * continuation submission, thread hand-off, and the park/wakeup path. It is the shape where
     * inline continuation and waiter unparking are expected to show up.
     */
    @Benchmark
    public void runSequentialChain(SequentialChainState state, Blackhole blackhole) {
        blackhole.consume(state.run());
    }

    /**
     * Benchmark a nested parallel/sequential tree of trivial steps.
     *
     * <p>Exercises admission across depths: parallel windows refilling, sequential child
     * advancement, continuation routing, and aggregating status at every level.
     */
    @Benchmark
    public void runNestedTree(NestedTreeState state, Blackhole blackhole) {
        blackhole.consume(state.run());
    }

    /**
     * State for heterogeneous duration benchmark.
     */
    @State(Scope.Benchmark)
    public static class ParallelHeterogeneousState {

        @Param({"4", "8", "16"})
        private int parallelism;

        @Param({"8", "32", "128"})
        private int childCount;

        private Runner runner;
        private Parallel root;

        @Setup(Level.Trial)
        public void setup() {
            runner = createRunner(parallelism);
            var parallel = Parallel.builder("root").parallelism(parallelism);
            for (var i = 0; i < childCount; i++) {
                final int index = i;
                // Mix of fast (1ms) and slow (20ms) children
                long sleepMs = (index % 4 == 0) ? 20 : 1;
                parallel.child(Step.of("child-" + index, context -> {
                    Blackhole.consumeCPU(1);
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            root = parallel.build();
        }

        Runner runner() {
            return runner;
        }

        Parallel root() {
            return root;
        }
    }

    /**
     * State for many small tasks benchmark.
     */
    @State(Scope.Benchmark)
    public static class ManySmallTasksState {

        @Param({"4", "8"})
        private int parallelism;

        @Param({"256", "1024"})
        private int taskCount;

        private Runner runner;
        private Parallel root;

        @Setup(Level.Trial)
        public void setup() {
            runner = createRunner(parallelism);
            var parallel = Parallel.builder("root").parallelism(parallelism);
            for (var i = 0; i < taskCount; i++) {
                parallel.child(Step.of("task-" + i, context -> Blackhole.consumeCPU(1)));
            }
            root = parallel.build();
        }

        Runner runner() {
            return runner;
        }

        Parallel root() {
            return root;
        }
    }

    /**
     * State for uniform duration benchmark.
     */
    @State(Scope.Benchmark)
    public static class ParallelUniformState {

        @Param({"4", "8", "16"})
        private int parallelism;

        @Param({"32", "64", "128"})
        private int childCount;

        private Runner runner;
        private Parallel root;

        @Setup(Level.Trial)
        public void setup() {
            runner = createRunner(parallelism);
            var parallel = Parallel.builder("root").parallelism(parallelism);
            for (var i = 0; i < childCount; i++) {
                parallel.child(Step.of("child-" + i, context -> {
                    Blackhole.consumeCPU(1);
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            root = parallel.build();
        }

        Runner runner() {
            return runner;
        }

        Parallel root() {
            return root;
        }
    }

    /**
     * State for the sequential-chain benchmark: one chain, one ready descriptor at a time.
     *
     * <p>Drives the scheduler directly. {@code Runner.run(action)} performs a classpath scan to
     * discover hooks before every run, which costs milliseconds and would swamp the scheduling
     * cost these shapes are meant to measure.
     */
    @State(Scope.Benchmark)
    public static class SequentialChainState {

        @Param({"4"})
        private int parallelism;

        @Param({"64", "256", "1024"})
        private int stepCount;

        private Configuration configuration;
        private Scheduler scheduler;
        private Sequential action;

        @Setup(Level.Trial)
        public void setup() {
            configuration = Configuration.defaultConfiguration();
            scheduler = new Scheduler(parallelism);
            var sequential = Sequential.builder("chain");
            for (var i = 0; i < stepCount; i++) {
                sequential.child(Step.of("step-" + i, context -> Blackhole.consumeCPU(1)));
            }
            action = sequential.build();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            scheduler.close();
        }

        Status run() {
            return runDirect(action, scheduler, configuration);
        }
    }

    /**
     * State for the nested-tree benchmark: parallel fan-out, sequential chains inside, and a
     * parallel level below that, so admission and continuation routing cross several depths.
     */
    @State(Scope.Benchmark)
    public static class NestedTreeState {

        @Param({"4", "8"})
        private int parallelism;

        @Param({"2", "4"})
        private int branchCount;

        @Param({"1", "3"})
        private int depth;

        private Configuration configuration;
        private Scheduler scheduler;
        private Parallel action;

        @Setup(Level.Trial)
        public void setup() {
            configuration = Configuration.defaultConfiguration();
            scheduler = new Scheduler(parallelism);
            var parallel = Parallel.builder("root").parallelism(parallelism);
            for (var i = 0; i < branchCount; i++) {
                parallel.child(branch("branch-" + i, depth));
            }
            action = parallel.build();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            scheduler.close();
        }

        private static Action branch(final String name, final int remainingDepth) {
            var sequential = Sequential.builder(name);
            if (remainingDepth <= 1) {
                sequential.child(Step.of(name + "-leaf-a", context -> Blackhole.consumeCPU(1)));
                sequential.child(Step.of(name + "-leaf-b", context -> Blackhole.consumeCPU(1)));
                return sequential.build();
            }
            var nested = Parallel.builder(name + "-parallel").parallelism(Integer.MAX_VALUE);
            for (var i = 0; i < 2; i++) {
                nested.child(branch(name + "-" + i, remainingDepth - 1));
            }
            sequential.child(nested.build());
            sequential.child(Step.of(name + "-tail", context -> Blackhole.consumeCPU(1)));
            return sequential.build();
        }

        Status run() {
            return runDirect(action, scheduler, configuration);
        }
    }

    /**
     * Executes one action tree on a warm scheduler, bypassing runner discovery.
     *
     * @param action the reusable action definition
     * @param scheduler the scheduler to execute on
     * @param configuration the configuration to expose to actions
     * @return the root status after execution
     */
    private static Status runDirect(final Action action, final Scheduler scheduler, final Configuration configuration) {
        var descriptor = new DescriptorBuilder().discover(action);
        var context =
                new ConcreteContext(configuration, new Listener() {}, descriptor, scheduler, new InstanceHolder());
        scheduler.executeDescriptor(descriptor, context, ExecutionMode.RUN);
        return descriptor.status();
    }

    private static Runner createQuietRunner(final int parallelism) {
        return Runner.builder()
                .configuration(Configuration.of(Map.of(
                        Configuration.RUNNER_PARALLELISM,
                        String.valueOf(parallelism),
                        Configuration.SCHEDULER_QUEUE_CAPACITY,
                        "4096",
                        Configuration.ANSI,
                        "false")))
                .listener(new org.paramixel.api.Listener() {})
                .build();
    }

    private static Runner createRunner(final int parallelism) {
        var configuration = Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(parallelism),
                Configuration.SCHEDULER_QUEUE_CAPACITY,
                "4096",
                Configuration.ANSI,
                "false"));
        return Runner.builder().configuration(configuration).build();
    }
}

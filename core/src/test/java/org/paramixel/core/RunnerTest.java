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

package org.paramixel.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.action.Sequential;
import org.paramixel.core.exception.ConfigurationException;
import org.paramixel.core.exception.CycleDetectedException;
import org.paramixel.core.exception.DeadlockDetected;
import org.paramixel.core.spi.DefaultResult;
import org.paramixel.core.spi.DefaultStatus;

@DisplayName("DefaultRunner")
class RunnerTest {

    @Test
    @DisplayName("rejects sequential actions without children")
    void rejectsSequentialActionsWithoutChildren() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Sequential.of("empty", List.of()))
                .withMessage("children must not be empty");
    }

    @Test
    @DisplayName("rejects parallel actions without children")
    void rejectsParallelActionsWithoutChildren() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Parallel.of("empty", List.of()))
                .withMessage("children must not be empty");
    }

    @Test
    @DisplayName("throws contextual configuration exception for invalid parallelism")
    void throwsContextualConfigurationExceptionForInvalidParallelism() {
        assertThatThrownBy(() -> Runner.builder()
                        .configuration(Map.of(Configuration.RUNNER_PARALLELISM, "not-a-number"))
                        .build())
                .isInstanceOf(ConfigurationException.class)
                .hasMessage("Invalid configuration for '" + Configuration.RUNNER_PARALLELISM
                        + "': expected integer but was 'not-a-number'")
                .cause()
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("executes a four-layer action tree with parallel, parallel, sequential, and direct actions")
    void executesFourLayerActionTree() {
        var directExecutionCount = new AtomicInteger();
        var sequentialExecutions = new ConcurrentHashMap<String, List<String>>();
        Action root = Parallel.of(
                "root",
                16,
                List.of(
                        parallelAction("parallel-0", sequentialExecutions, directExecutionCount),
                        parallelAction("parallel-1", sequentialExecutions, directExecutionCount)));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(root.getChildren()).hasSize(2);

        for (int depth1Index = 0; depth1Index < root.getChildren().size(); depth1Index++) {
            Action depth1Action = root.getChildren().get(depth1Index);
            Result depth1Result = result.getChildren().get(depth1Index);
            assertThat(depth1Result.getStatus().isPass()).isTrue();
            assertThat(depth1Action.getName()).isEqualTo("parallel-" + depth1Index);
            assertThat(depth1Action.getChildren()).hasSize(2);

            for (int depth2Index = 0; depth2Index < depth1Action.getChildren().size(); depth2Index++) {
                Action depth2Action = depth1Action.getChildren().get(depth2Index);
                Result depth2Result = depth1Result.getChildren().get(depth2Index);
                String sequentialName = "parallel-" + depth1Index + "-sequential-" + depth2Index;

                assertThat(depth2Result.getStatus().isPass()).isTrue();
                assertThat(depth2Action.getName()).isEqualTo(sequentialName);
                assertThat(depth2Action.getChildren()).hasSize(4);

                for (int leafIndex = 0; leafIndex < depth2Action.getChildren().size(); leafIndex++) {
                    Action leafAction = depth2Action.getChildren().get(leafIndex);
                    Result leafResult = depth2Result.getChildren().get(leafIndex);

                    assertThat(leafResult.getStatus().isPass()).isTrue();
                    assertThat(leafAction.getName()).isEqualTo(sequentialName + "-direct-" + leafIndex);
                    assertThat(leafAction.getChildren()).isEmpty();
                }
            }
        }

        assertThat(directExecutionCount).hasValue(16);
        assertThat(sequentialExecutions)
                .hasSize(4)
                .allSatisfy((sequentialName, executions) -> assertThat(executions)
                        .containsExactly(
                                sequentialName + "-direct-0",
                                sequentialName + "-direct-1",
                                sequentialName + "-direct-2",
                                sequentialName + "-direct-3"));
    }

    @Test
    @DisplayName("root action has no parent")
    void rootActionHasNoParent() {
        Action root = Direct.of("root", context -> {});
        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(root.getParent()).isEmpty();
    }

    @Test
    @DisplayName("sequential children have correct parent reference")
    void sequentialChildrenHaveCorrectParentReference() {
        Action left = Direct.of("left", context -> {});
        Action right = Direct.of("right", context -> {});
        Action root = Sequential.of("root", List.of(left, right));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(root.getParent()).isEmpty();
        assertThat(root.getChildren()).hasSize(2);

        assertThat(left.getParent()).contains(root);
        assertThat(right.getParent()).contains(root);
    }

    @Test
    @DisplayName("parallel children have correct parent reference")
    void parallelChildrenHaveCorrectParentReference() {
        Action left = Direct.of("left", context -> {});
        Action right = Direct.of("right", context -> {});
        Action root = Parallel.of("root", List.of(left, right));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(root.getParent()).isEmpty();
        assertThat(root.getChildren()).hasSize(2);

        assertThat(left.getParent()).contains(root);
        assertThat(right.getParent()).contains(root);
    }

    @Test
    @DisplayName("multi-level tree has correct parent chain")
    void multiLevelTreeHasCorrectParentChain() {
        Action left = Direct.of("left", context -> {});
        Action right = Direct.of("right", context -> {});
        Action child1 = Sequential.of("child1", List.of(left));
        Action child2 = Sequential.of("child2", List.of(right));
        Action root = Parallel.of("root", List.of(child1, child2));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(root.getParent()).isEmpty();
        assertThat(root.getChildren()).hasSize(2);

        assertThat(child1.getParent()).contains(root);
        assertThat(child2.getParent()).contains(root);

        assertThat(left.getParent()).contains(child1);
        assertThat(right.getParent()).contains(child2);
    }

    @Test
    @DisplayName("four-layer action tree has correct parent references at each depth")
    void fourLayerActionTreeHasCorrectParentReferences() {
        var directExecutionCount = new AtomicInteger();
        var sequentialExecutions = new ConcurrentHashMap<String, List<String>>();
        Action root = Parallel.of(
                "root",
                16,
                List.of(
                        parallelAction("parallel-0", sequentialExecutions, directExecutionCount),
                        parallelAction("parallel-1", sequentialExecutions, directExecutionCount)));

        Runner.builder().build().run(root);

        assertThat(root.getParent()).isEmpty();
        assertThat(root.getChildren()).hasSize(2);

        for (int depth1Index = 0; depth1Index < root.getChildren().size(); depth1Index++) {
            Action depth1Action = root.getChildren().get(depth1Index);
            assertThat(depth1Action.getParent()).contains(root);

            for (int depth2Index = 0; depth2Index < depth1Action.getChildren().size(); depth2Index++) {
                Action depth2Action = depth1Action.getChildren().get(depth2Index);
                assertThat(depth2Action.getParent()).contains(depth1Action);

                for (int leafIndex = 0; leafIndex < depth2Action.getChildren().size(); leafIndex++) {
                    Action leafAction = depth2Action.getChildren().get(leafIndex);
                    assertThat(leafAction.getParent()).contains(depth2Action);
                }
            }
        }
    }

    @Test
    @DisplayName("reuses an already-safe listener without double wrapping")
    void reusesAnAlreadySafeListenerWithoutDoubleWrapping() {
        var delegate = new Listener() {};
        var safeListener = new org.paramixel.core.spi.listener.SafeListener(delegate);
        var observedListener = new java.util.concurrent.atomic.AtomicReference<Listener>();
        Action action = Direct.of("root", context -> observedListener.set(context.getListener()));

        Runner.builder().listener(safeListener).build().run(action);

        assertThat(observedListener).hasValue(safeListener);
    }

    @Test
    @DisplayName("notifies listener before and after each action")
    void notifiesGetListenerBeforeAndAfterEachAction() {
        var events = new CopyOnWriteArrayList<String>();
        Action root =
                Sequential.of("root", List.of(Direct.of("first", context -> {}), Direct.of("second", context -> {})));
        Listener listener = new Listener() {
            @Override
            public void runStarted(Runner runner) {
                events.add("runStarted");
            }

            @Override
            public void runCompleted(Runner runner, Result result) {
                events.add("runCompleted:"
                        + result.getAction().getClass().getSimpleName().charAt(0));
            }

            @Override
            public void beforeAction(Result result) {
                events.add("before:"
                        + result.getAction().getClass().getSimpleName().charAt(0));
            }

            @Override
            public void afterAction(Result result) {
                events.add(
                        "after:" + result.getAction().getClass().getSimpleName().charAt(0) + ":" + result.getStatus());
            }
        };

        Result result = Runner.builder().listener(listener).build().run(root);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(events)
                .containsExactly(
                        "runStarted",
                        "before:S",
                        "before:D",
                        "after:D:PASS",
                        "before:D",
                        "after:D:PASS",
                        "after:S:PASS",
                        "runCompleted:S");
    }

    @Test
    @DisplayName("rejects cyclic action graph before runStarted or execution")
    void rejectsCyclicActionGraphBeforeRunStartedOrExecution() {
        var events = new CopyOnWriteArrayList<String>();
        var executionCount = new AtomicInteger();
        Listener listener = new Listener() {
            @Override
            public void runStarted(Runner runner) {
                events.add("runStarted");
            }

            @Override
            public void runCompleted(Runner runner, Result result) {
                events.add("runCompleted");
            }
        };
        MutableAction root = new MutableAction("root", executionCount);
        MutableAction child = new MutableAction("child", executionCount);
        MutableAction grandchild = new MutableAction("grandchild", executionCount);
        root.addGraphChild(child);
        child.addGraphChild(grandchild);
        grandchild.addGraphChild(child);

        assertThatThrownBy(() -> Runner.builder().listener(listener).build().run(root))
                .isInstanceOf(CycleDetectedException.class)
                .hasMessageContaining("Cycle detected in action graph")
                .hasMessageContaining("child[")
                .hasMessageContaining("grandchild[");

        assertThat(events).isEmpty();
        assertThat(executionCount).hasValue(0);
    }

    @Test
    @DisplayName("allows child-bearing actions to expose children and parent links")
    void allowsCustomActionsToNavigateActionChildrenViaContext() {
        Action left = Direct.of("left", context -> {});
        Action right = Direct.of("right", context -> {});
        Action root = Sequential.of("root", List.of(left, right));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(root.getParent()).isEmpty();
        assertThat(root.getChildren()).containsExactly(left, right);
        assertThat(left.getParent()).contains(root);
        assertThat(right.getParent()).contains(root);
    }

    @Test
    @DisplayName("stores and returns provided configuration map")
    void storesAndReturnsProvidedGetConfigurationMap() {
        var expectedConfig = Map.of("key1", "value1", "key2", "value2");
        var runner = Runner.builder().configuration(expectedConfig).build();
        assertThat(runner.getConfiguration()).isEqualTo(expectedConfig);
    }

    @Test
    @DisplayName("defensively copies configuration map")
    void defensivelyCopiesGetConfigurationMap() {
        var originalConfig = new HashMap<String, String>();
        originalConfig.put("key1", "value1");
        Runner runner = Runner.builder().configuration(originalConfig).build();
        originalConfig.put("key2", "value2");
        assertThat(runner.getConfiguration()).doesNotContainKey("key2");
    }

    @Test
    @DisplayName("parallel with custom executor service runs children on user's pool")
    void parallelWithCustomExecutorService() {
        var executorService = Executors.newFixedThreadPool(4);
        var executionCount = new AtomicInteger();

        Action action = Parallel.of(
                "test",
                executorService,
                List.of(
                        Direct.of("child 1", context -> executionCount.incrementAndGet()),
                        Direct.of("child 2", context -> executionCount.incrementAndGet()),
                        Direct.of("child 3", context -> executionCount.incrementAndGet())));

        Result result = Runner.builder().build().run(action);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(executionCount).hasValue(3);

        executorService.shutdown();
    }

    @Test
    @DisplayName("parallel with custom executor service varargs runs children on user's pool")
    void parallelWithCustomExecutorServiceVarargs() {
        var executorService = Executors.newFixedThreadPool(4);
        var executionCount = new AtomicInteger();

        Action action = Parallel.of(
                "test",
                executorService,
                Direct.of("child 1", context -> executionCount.incrementAndGet()),
                Direct.of("child 2", context -> executionCount.incrementAndGet()),
                Direct.of("child 3", context -> executionCount.incrementAndGet()));

        Result result = Runner.builder().build().run(action);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(executionCount).hasValue(3);

        executorService.shutdown();
    }

    @Test
    @DisplayName("custom executor service is not shut down by runner")
    void customExecutorServiceNotShutDownByRunner() {
        var executorService = Executors.newFixedThreadPool(4);

        Action action = Parallel.of("test", executorService, List.of(Direct.of("child", context -> {})));

        Runner.builder().build().run(action);

        assertThat(executorService.isShutdown()).isFalse();
        assertThat(executorService.isTerminated()).isFalse();

        executorService.shutdown();
    }

    @Test
    @DisplayName("parallel workers preserve parent context class loader")
    void parallelWorkersPreserveParentContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader expectedClassLoader = new URLClassLoader(new URL[0], originalClassLoader);
        var observedClassLoaders = new CopyOnWriteArrayList<ClassLoader>();

        try {
            Thread.currentThread().setContextClassLoader(expectedClassLoader);

            Action action = Parallel.of(
                    "test",
                    4,
                    List.of(
                            Direct.of(
                                    "child 1",
                                    context -> observedClassLoaders.add(
                                            Thread.currentThread().getContextClassLoader())),
                            Direct.of(
                                    "child 2",
                                    context -> observedClassLoaders.add(
                                            Thread.currentThread().getContextClassLoader())),
                            Direct.of(
                                    "child 3",
                                    context -> observedClassLoaders.add(
                                            Thread.currentThread().getContextClassLoader())),
                            Direct.of(
                                    "child 4",
                                    context -> observedClassLoaders.add(
                                            Thread.currentThread().getContextClassLoader()))));

            Result result = Runner.builder().build().run(action);

            assertThat(result.getStatus().isPass()).isTrue();
            assertThat(observedClassLoaders).hasSize(4).containsOnly(expectedClassLoader);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    @DisplayName(
            "top-level default parallel uses runner executor and nested default parallel uses dedicated parallel executor")
    void topLevelDefaultParallelUsesRunnerExecutorAndNestedDefaultParallelUsesDedicatedParallelExecutor() {
        var outerThreadNames = new CopyOnWriteArrayList<String>();
        var innerThreadNames = new CopyOnWriteArrayList<String>();
        ExecutorService runnerExecutorService = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "custom-runner");
            thread.setDaemon(true);
            return thread;
        });

        try {
            Action action = Parallel.of(
                    "outer",
                    List.of(
                            Parallel.of(
                                    "inner-0",
                                    List.of(
                                            Direct.of(
                                                    "leaf-0-0",
                                                    context -> innerThreadNames.add(Thread.currentThread()
                                                            .getName())),
                                            Direct.of(
                                                    "leaf-0-1",
                                                    context -> innerThreadNames.add(Thread.currentThread()
                                                            .getName())))),
                            Direct.of(
                                    "outer-direct",
                                    context -> outerThreadNames.add(
                                            Thread.currentThread().getName()))));

            Result result = Runner.builder()
                    .executorService(runnerExecutorService)
                    .build()
                    .run(action);

            assertThat(result.getStatus().isPass()).isTrue();
            assertThat(outerThreadNames).allMatch(name -> name.contains("custom-runner"));
            assertThat(innerThreadNames).allMatch(name -> name.contains("paramixel-parallel"));
        } finally {
            runnerExecutorService.shutdown();
        }
    }

    @Test
    @DisplayName(
            "nested default parallel completes without deadlock when outer uses runner executor and inner uses default parallel executor")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void nestedDefaultParallelCompletesWithoutDeadlockWhenOuterUsesRunnerExecutorAndInnerUsesDefaultParallelExecutor() {
        var executionCount = new AtomicInteger();
        Action action = Parallel.of(
                "outer",
                List.of(
                        Parallel.of(
                                "inner-0",
                                List.of(
                                        Direct.of("leaf-0-0", context -> executionCount.incrementAndGet()),
                                        Direct.of("leaf-0-1", context -> executionCount.incrementAndGet()))),
                        Parallel.of(
                                "inner-1",
                                List.of(
                                        Direct.of("leaf-1-0", context -> executionCount.incrementAndGet()),
                                        Direct.of("leaf-1-1", context -> executionCount.incrementAndGet())))));

        Result result = Runner.builder()
                .configuration(Map.of(Configuration.RUNNER_PARALLELISM, "2"))
                .build()
                .run(action);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(executionCount).hasValue(4);
    }

    @Test
    @DisplayName("nested parallel can use dedicated inner executor services")
    void nestedParallelCanUseDedicatedInnerExecutorServices() {
        var executionCount = new AtomicInteger();
        var innerExecutorServices = new ArrayList<java.util.concurrent.ExecutorService>();

        try {
            List<Action> innerParallelActions = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                List<Action> leafActions = new ArrayList<>();
                for (int j = 0; j < 3; j++) {
                    leafActions.add(Direct.of("leaf " + i + "-" + j, context -> executionCount.incrementAndGet()));
                }

                var innerExecutorService = Executors.newFixedThreadPool(3);
                innerExecutorServices.add(innerExecutorService);
                innerParallelActions.add(Parallel.of("inner " + i, innerExecutorService, List.copyOf(leafActions)));
            }

            Action outerParallel = Parallel.of("outer", List.copyOf(innerParallelActions));

            Result result = Runner.builder().build().run(outerParallel);

            assertThat(result.getStatus().isPass()).isTrue();
            assertThat(executionCount).hasValue(12);
        } finally {
            for (var executorService : innerExecutorServices) {
                executorService.shutdown();
            }
        }
    }

    @Test
    @DisplayName("nested parallel with dedicated inner executor services completes without deadlock")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void nestedParallelWithDedicatedInnerExecutorServicesCompletesWithoutDeadlock() {
        var executionCount = new AtomicInteger();
        var innerExecutorServices = new ArrayList<ExecutorService>();

        try {
            List<Action> innerParallelActions = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                ExecutorService innerEs = Executors.newFixedThreadPool(2);
                innerExecutorServices.add(innerEs);
                innerParallelActions.add(Parallel.of(
                        "inner-" + i,
                        innerEs,
                        Direct.of("leaf-" + i + "-0", context -> executionCount.incrementAndGet()),
                        Direct.of("leaf-" + i + "-1", context -> executionCount.incrementAndGet())));
            }

            Action outerParallel = Parallel.of("outer", innerParallelActions);

            Result result = Runner.builder().build().run(outerParallel);

            assertThat(result.getStatus().isPass()).isTrue();
            assertThat(executionCount).hasValue(8);
        } finally {
            for (ExecutorService es : innerExecutorServices) {
                es.shutdown();
            }
        }
    }

    private static Action parallelAction(
            String parallelName, Map<String, List<String>> sequentialExecutions, AtomicInteger directExecutionCount) {
        return Parallel.of(
                parallelName,
                16,
                List.of(
                        sequentialAction(parallelName, 0, sequentialExecutions, directExecutionCount),
                        sequentialAction(parallelName, 1, sequentialExecutions, directExecutionCount)));
    }

    private static Action sequentialAction(
            String parallelName,
            int sequentialIndex,
            Map<String, List<String>> sequentialExecutions,
            AtomicInteger directExecutionCount) {
        String sequentialName = parallelName + "-sequential-" + sequentialIndex;
        return Sequential.of(
                sequentialName,
                List.of(
                        directAction(sequentialName, 0, sequentialExecutions, directExecutionCount),
                        directAction(sequentialName, 1, sequentialExecutions, directExecutionCount),
                        directAction(sequentialName, 2, sequentialExecutions, directExecutionCount),
                        directAction(sequentialName, 3, sequentialExecutions, directExecutionCount)));
    }

    private static Action directAction(
            String sequentialName,
            int directIndex,
            Map<String, List<String>> sequentialExecutions,
            AtomicInteger directExecutionCount) {
        String directName = sequentialName + "-direct-" + directIndex;
        return Direct.of(directName, context -> {
            directExecutionCount.incrementAndGet();
            sequentialExecutions
                    .computeIfAbsent(sequentialName, ignored -> new CopyOnWriteArrayList<>())
                    .add(directName);
        });
    }

    private static final class MutableAction implements Action {

        private final String id = java.util.UUID.randomUUID().toString();
        private final String name;
        private final AtomicInteger executionCount;
        private final List<Action> children = new ArrayList<>();
        private Action parent;

        private MutableAction(String name, AtomicInteger executionCount) {
            this.name = name;
            this.executionCount = executionCount;
        }

        private void addGraphChild(Action child) {
            children.add(child);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Optional<Action> getParent() {
            return Optional.ofNullable(parent);
        }

        @Override
        public void setParent(Action parent) {
            this.parent = parent;
        }

        @Override
        public void addChild(Action child) {
            addGraphChild(child);
            child.setParent(this);
        }

        @Override
        public List<Action> getChildren() {
            return List.copyOf(children);
        }

        @Override
        public Result execute(Context context) {
            executionCount.incrementAndGet();
            DefaultResult result = new DefaultResult(this);
            result.setStatus(DefaultStatus.PASS);
            result.setElapsedTime(Duration.ZERO);
            return result;
        }

        @Override
        public Result skip(Context context) {
            DefaultResult result = new DefaultResult(this);
            result.setStatus(DefaultStatus.SKIP);
            result.setElapsedTime(Duration.ZERO);
            return result;
        }
    }

    @Nested
    @DisplayName("deadlock detection in run()")
    class DeadlockDetection {

        @Test
        @DisplayName("rejects three-level nested default Parallel with parallelism=1")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void rejectsThreeLevelNestingWithParallelism1() {
            Action action = Parallel.of(
                    "A",
                    Parallel.of(
                            "B",
                            Parallel.of("C", Direct.of("C1", ctx -> {}), Direct.of("C2", ctx -> {})),
                            Direct.of("B2", ctx -> {})),
                    Direct.of("A2", ctx -> {}));

            assertThatThrownBy(() -> Runner.builder()
                            .configuration(Map.of(Configuration.RUNNER_PARALLELISM, "1"))
                            .build()
                            .run(action))
                    .isInstanceOf(DeadlockDetected.class)
                    .hasMessageContaining("thread-starvation deadlock")
                    .hasMessageContaining("3 levels")
                    .hasMessageContaining("1 thread");
        }

        @Test
        @DisplayName("accepts two-level nested default Parallel with parallelism=1")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void acceptsTwoLevelNestingWithParallelism1() {
            Action action = Parallel.of(
                    "outer", Parallel.of("inner", Direct.of("leaf-0", ctx -> {}), Direct.of("leaf-1", ctx -> {})));

            Result result = Runner.builder()
                    .configuration(Map.of(Configuration.RUNNER_PARALLELISM, "1"))
                    .build()
                    .run(action);

            assertThat(result.getStatus().isPass()).isTrue();
        }

        @Test
        @DisplayName("accepts three-level nested default Parallel with parallelism=2")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void acceptsThreeLevelNestingWithParallelism2() {
            Action action = Parallel.of(
                    "A",
                    Parallel.of(
                            "B",
                            Parallel.of("C", Direct.of("C1", ctx -> {}), Direct.of("C2", ctx -> {})),
                            Direct.of("B2", ctx -> {})),
                    Direct.of("A2", ctx -> {}));

            Result result = Runner.builder()
                    .configuration(Map.of(Configuration.RUNNER_PARALLELISM, "2"))
                    .build()
                    .run(action);

            assertThat(result.getStatus().isPass()).isTrue();
        }

        @Test
        @DisplayName("accepts deeply nested Parallel with custom inner executors")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void acceptsDeepNestingWithCustomExecutors() {
            ExecutorService innerEs1 = Executors.newFixedThreadPool(2);
            ExecutorService innerEs2 = Executors.newFixedThreadPool(2);
            try {
                Action action = Parallel.of(
                        "A",
                        Parallel.of(
                                "B",
                                innerEs1,
                                Parallel.of("C", innerEs2, Direct.of("C1", ctx -> {}), Direct.of("C2", ctx -> {})),
                                Direct.of("B2", ctx -> {})),
                        Direct.of("A2", ctx -> {}));

                Result result = Runner.builder()
                        .configuration(Map.of(Configuration.RUNNER_PARALLELISM, "1"))
                        .build()
                        .run(action);

                assertThat(result.getStatus().isPass()).isTrue();
            } finally {
                innerEs1.shutdown();
                innerEs2.shutdown();
            }
        }

        @Test
        @DisplayName("accepts single Parallel with parallelism=1 and many children")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void acceptsSingleParallelWithParallelism1() {
            Action action = Parallel.of(
                    "root", 1, Direct.of("a", ctx -> {}), Direct.of("b", ctx -> {}), Direct.of("c", ctx -> {}));

            Result result = Runner.builder()
                    .configuration(Map.of(Configuration.RUNNER_PARALLELISM, "1"))
                    .build()
                    .run(action);

            assertThat(result.getStatus().isPass()).isTrue();
        }

        @Test
        @DisplayName("detects deadlock with Sequential between Parallel nodes")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void detectsDeadlockWithSequentialBetweenParallels() {
            Action action = Parallel.of(
                    "A", Sequential.of("S", Parallel.of("B", Parallel.of("C", Direct.of("leaf", ctx -> {})))));

            assertThatThrownBy(() -> Runner.builder()
                            .configuration(Map.of(Configuration.RUNNER_PARALLELISM, "1"))
                            .build()
                            .run(action))
                    .isInstanceOf(DeadlockDetected.class)
                    .hasMessageContaining("thread-starvation deadlock");
        }

        @Test
        @DisplayName("detects deadlock in deepest branch of asymmetric tree")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void detectsDeadlockInAsymmetricTree() {
            Action action = Parallel.of(
                    "root",
                    Parallel.of("safe-inner", Direct.of("safe-leaf", ctx -> {})),
                    Parallel.of(
                            "danger-A",
                            Parallel.of("danger-B", Parallel.of("danger-C", Direct.of("danger-leaf", ctx -> {})))));

            assertThatThrownBy(() -> Runner.builder()
                            .configuration(Map.of(Configuration.RUNNER_PARALLELISM, "1"))
                            .build()
                            .run(action))
                    .isInstanceOf(DeadlockDetected.class)
                    .hasMessageContaining("4 levels");
        }

        @Test
        @DisplayName("accepts non-Parallel tree (Sequential with Direct children only)")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void acceptsNonParallelTree() {
            Action action = Sequential.of("root", Direct.of("a", ctx -> {}), Direct.of("b", ctx -> {}));

            Result result = Runner.builder()
                    .configuration(Map.of(Configuration.RUNNER_PARALLELISM, "1"))
                    .build()
                    .run(action);

            assertThat(result.getStatus().isPass()).isTrue();
        }

        @Test
        @DisplayName("accepts single leaf action (Direct) with parallelism=1")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void acceptsSingleLeafAction() {
            Action action = Direct.of("leaf", ctx -> {});

            Result result = Runner.builder()
                    .configuration(Map.of(Configuration.RUNNER_PARALLELISM, "1"))
                    .build()
                    .run(action);

            assertThat(result.getStatus().isPass()).isTrue();
        }

        @Test
        @DisplayName("accepts multiple custom-executor Parallels at same level")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void acceptsMultipleCustomExecutorsAtSameLevel() {
            ExecutorService es1 = Executors.newSingleThreadExecutor();
            ExecutorService es2 = Executors.newSingleThreadExecutor();
            try {
                Action action = Parallel.of(
                        "outer",
                        Parallel.of("inner1", es1, Direct.of("a", ctx -> {})),
                        Parallel.of("inner2", es2, Direct.of("b", ctx -> {})));

                Result result = Runner.builder()
                        .configuration(Map.of(Configuration.RUNNER_PARALLELISM, "1"))
                        .build()
                        .run(action);

                assertThat(result.getStatus().isPass()).isTrue();
            } finally {
                es1.shutdown();
                es2.shutdown();
            }
        }

        @Test
        @DisplayName("accepts deep default-executor Parallel with high parallelism")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void acceptsDeepNestingWithHighParallelism() {
            Action action = Parallel.of(
                    "A",
                    Parallel.of(
                            "B",
                            Parallel.of("C", Direct.of("C1", ctx -> {}), Direct.of("C2", ctx -> {})),
                            Direct.of("B2", ctx -> {})),
                    Direct.of("A2", ctx -> {}));

            Result result = Runner.builder().build().run(action);

            assertThat(result.getStatus().isPass()).isTrue();
        }
    }

    @Nested
    @DisplayName("Runner reusability")
    class Reusability {

        @Test
        @DisplayName("run() can be called multiple times on the same Runner instance")
        void runCanBeCalledMultipleTimes() {
            Runner runner = Runner.builder().build();

            Action first = Direct.of("first", context -> {});
            Result firstResult = runner.run(first);

            Action second = Direct.of("second", context -> {});
            Result secondResult = runner.run(second);

            assertThat(firstResult.getStatus().isPass()).isTrue();
            assertThat(secondResult.getStatus().isPass()).isTrue();
        }

        @Test
        @DisplayName("each run() uses fresh executor services")
        void eachRunUsesFreshExecutors() {
            Runner runner = Runner.builder().build();

            Action first = Direct.of("first", context -> {});
            Result firstResult = runner.run(first);

            Action second =
                    Parallel.of("second", Direct.of("child-1", context -> {}), Direct.of("child-2", context -> {}));
            Result secondResult = runner.run(second);

            assertThat(firstResult.getStatus().isPass()).isTrue();
            assertThat(secondResult.getStatus().isPass()).isTrue();
        }

        @Test
        @DisplayName("external executor service is not shut down by run() and survives multiple runs")
        void externalExecutorServiceNotShutDownByRun() {
            ExecutorService external = Executors.newFixedThreadPool(2);
            try {
                Runner runner = Runner.builder().executorService(external).build();

                Action first = Direct.of("first", context -> {});
                Result firstResult = runner.run(first);

                Action second = Direct.of("second", context -> {});
                Result secondResult = runner.run(second);

                assertThat(external.isShutdown()).isFalse();
                assertThat(firstResult.getStatus().isPass()).isTrue();
                assertThat(secondResult.getStatus().isPass()).isTrue();
            } finally {
                external.shutdown();
            }
        }

        @Test
        @DisplayName("concurrent run() calls on the same Runner are independent")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void concurrentRunsAreIndependent() throws Exception {
            Runner runner = Runner.builder().build();
            AtomicInteger counter = new AtomicInteger();

            ExecutorService testPool = Executors.newFixedThreadPool(2);
            try {
                Future<?> f1 = testPool.submit(() -> {
                    Action action = Direct.of("concurrent-1", context -> counter.incrementAndGet());
                    runner.run(action);
                });
                Future<?> f2 = testPool.submit(() -> {
                    Action action = Direct.of("concurrent-2", context -> counter.incrementAndGet());
                    runner.run(action);
                });
                f1.get();
                f2.get();
                assertThat(counter).hasValue(2);
            } finally {
                testPool.shutdown();
            }
        }
    }
}

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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.action.Sequential;

@DisplayName("DefaultRunner")
class RunnerTest {

    @Test
    @DisplayName("rejects sequential actions without children")
    void rejectsSequentialActionsWithoutChildren() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Sequential.of("empty", List.of()))
                .withMessage("action must have at least one child");
    }

    @Test
    @DisplayName("rejects parallel actions without children")
    void rejectsParallelActionsWithoutChildren() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Parallel.of("empty", List.of()))
                .withMessage("action must have at least one child");
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

        Runner.builder().build().run(root);

        assertThat(root.getResult().getStatus().isPass()).isTrue();
        assertThat(root.getResult().getStatus().getThrowable()).isEmpty();
        assertThat(root.getChildren()).hasSize(2);

        for (int depth1Index = 0; depth1Index < root.getChildren().size(); depth1Index++) {
            Action depth1Action = root.getChildren().get(depth1Index);
            assertThat(depth1Action.getResult().getStatus().isPass()).isTrue();
            assertThat(depth1Action.getName()).isEqualTo("parallel-" + depth1Index);
            assertThat(depth1Action.getChildren()).hasSize(2);

            for (int depth2Index = 0; depth2Index < depth1Action.getChildren().size(); depth2Index++) {
                Action depth2Action = depth1Action.getChildren().get(depth2Index);
                String sequentialName = "parallel-" + depth1Index + "-sequential-" + depth2Index;

                assertThat(depth2Action.getResult().getStatus().isPass()).isTrue();
                assertThat(depth2Action.getName()).isEqualTo(sequentialName);
                assertThat(depth2Action.getChildren()).hasSize(4);

                for (int leafIndex = 0; leafIndex < depth2Action.getChildren().size(); leafIndex++) {
                    Action leafAction = depth2Action.getChildren().get(leafIndex);

                    assertThat(leafAction.getResult().getStatus().isPass()).isTrue();
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
        Runner.builder().build().run(root);

        assertThat(root.getResult().getStatus().isPass()).isTrue();
        assertThat(root.getParent()).isEmpty();
    }

    @Test
    @DisplayName("sequential children have correct parent reference")
    void sequentialChildrenHaveCorrectParentReference() {
        Action left = Direct.of("left", context -> {});
        Action right = Direct.of("right", context -> {});
        Action root = Sequential.of("root", List.of(left, right));

        Runner.builder().build().run(root);

        assertThat(root.getResult().getStatus().isPass()).isTrue();
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

        Runner.builder().build().run(root);

        assertThat(root.getResult().getStatus().isPass()).isTrue();
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

        Runner.builder().build().run(root);

        assertThat(root.getResult().getStatus().isPass()).isTrue();
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
    @DisplayName("notifies listener before and after each action")
    void notifiesGetListenerBeforeAndAfterEachAction() {
        var events = new CopyOnWriteArrayList<String>();
        Action root =
                Sequential.of("root", List.of(Direct.of("first", context -> {}), Direct.of("second", context -> {})));
        Listener listener = new Listener() {
            @Override
            public void runStarted(Runner runner, Action action) {
                events.add("runStarted:" + action.getClass().getSimpleName().charAt(0));
            }

            @Override
            public void runCompleted(Runner runner, Action action) {
                events.add("runCompleted:" + action.getClass().getSimpleName().charAt(0));
            }

            @Override
            public void beforeAction(Context context, Action action) {
                events.add("before:" + action.getClass().getSimpleName().charAt(0));
            }

            @Override
            public void afterAction(Context context, Action action, Result result) {
                events.add("after:" + action.getClass().getSimpleName().charAt(0) + ":" + result.getStatus());
            }
        };

        Runner.builder().listener(listener).build().run(root);

        assertThat(root.getResult().getStatus().isPass()).isTrue();
        assertThat(events)
                .containsExactly(
                        "runStarted:S",
                        "before:S",
                        "before:D",
                        "after:D:PASS",
                        "before:D",
                        "after:D:PASS",
                        "after:S:PASS",
                        "runCompleted:S");
    }

    @Test
    @DisplayName("allows child-bearing actions to expose children and parent links")
    void allowsCustomActionsToNavigateActionChildrenViaContext() {
        Action left = Direct.of("left", context -> {});
        Action right = Direct.of("right", context -> {});
        Action root = Sequential.of("root", List.of(left, right));

        Runner.builder().build().run(root);

        assertThat(root.getResult().getStatus().isPass()).isTrue();
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

        Runner.builder().build().run(action);

        assertThat(action.getResult().getStatus().isPass()).isTrue();
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

        Runner.builder().build().run(action);

        assertThat(action.getResult().getStatus().isPass()).isTrue();
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

            Runner.builder().build().run(action);

            assertThat(action.getResult().getStatus().isPass()).isTrue();
            assertThat(observedClassLoaders).hasSize(4).containsOnly(expectedClassLoader);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
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

            Runner.builder().build().run(outerParallel);

            assertThat(outerParallel.getResult().getStatus().isPass()).isTrue();
            assertThat(executionCount).hasValue(12);
        } finally {
            for (var executorService : innerExecutorServices) {
                executorService.shutdown();
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
}

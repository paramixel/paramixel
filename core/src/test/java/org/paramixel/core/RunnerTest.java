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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.action.Sequential;
import org.paramixel.core.internal.DefaultRunner;

@DisplayName("DefaultRunner")
class RunnerTest {

    @Test
    @DisplayName("rejects sequential actions without children")
    void rejectsSequentialActionsWithoutChildren() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Sequential.of("empty", List.of()))
                .withMessage("sequential action must have at least one child");
    }

    @Test
    @DisplayName("rejects parallel actions without children")
    void rejectsParallelActionsWithoutChildren() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Parallel.of("empty", List.of()))
                .withMessage("parallel action must have at least one child");
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

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.failure()).isEmpty();
        assertThat(result.action()).isSameAs(root);
        assertThat(result.action()).isInstanceOf(Parallel.class);
        assertThat(result.children()).hasSize(2);

        for (int depth1Index = 0; depth1Index < result.children().size(); depth1Index++) {
            Result depth1Result = result.children().get(depth1Index);
            assertThat(depth1Result.status()).isEqualTo(Result.Status.PASS);
            assertThat(depth1Result.action().name()).isEqualTo("parallel-" + depth1Index);
            assertThat(depth1Result.action()).isInstanceOf(Parallel.class);
            assertThat(depth1Result.children()).hasSize(2);

            for (int depth2Index = 0; depth2Index < depth1Result.children().size(); depth2Index++) {
                Result depth2Result = depth1Result.children().get(depth2Index);
                String sequentialName = "parallel-" + depth1Index + "-sequential-" + depth2Index;

                assertThat(depth2Result.status()).isEqualTo(Result.Status.PASS);
                assertThat(depth2Result.action().name()).isEqualTo(sequentialName);
                assertThat(depth2Result.action()).isInstanceOf(Sequential.class);
                assertThat(depth2Result.children()).hasSize(4);

                for (int leafIndex = 0; leafIndex < depth2Result.children().size(); leafIndex++) {
                    Result leafResult = depth2Result.children().get(leafIndex);

                    assertThat(leafResult.status()).isEqualTo(Result.Status.PASS);
                    assertThat(leafResult.action().name()).isEqualTo(sequentialName + "-direct-" + leafIndex);
                    assertThat(leafResult.action()).isInstanceOf(Direct.class);
                    assertThat(leafResult.children()).isEmpty();
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
    @DisplayName("root result has no parent")
    void rootResultHasNoParent() {
        Action root = Direct.of("root", context -> {});
        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.parent()).isEmpty();
    }

    @Test
    @DisplayName("sequential children have correct parent reference")
    void sequentialChildrenHaveCorrectParentReference() {
        Action left = Direct.of("left", context -> {});
        Action right = Direct.of("right", context -> {});
        Action root = Sequential.of("root", List.of(left, right));

        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.parent()).isEmpty();
        assertThat(result.children()).hasSize(2);

        Result leftResult = result.children().get(0);
        Result rightResult = result.children().get(1);

        assertThat(leftResult.parent()).contains(result);
        assertThat(rightResult.parent()).contains(result);
    }

    @Test
    @DisplayName("parallel children have correct parent reference")
    void parallelChildrenHaveCorrectParentReference() {
        Action left = Direct.of("left", context -> {});
        Action right = Direct.of("right", context -> {});
        Action root = Parallel.of("root", List.of(left, right));

        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.parent()).isEmpty();
        assertThat(result.children()).hasSize(2);

        Result leftResult = result.children().get(0);
        Result rightResult = result.children().get(1);

        assertThat(leftResult.parent()).contains(result);
        assertThat(rightResult.parent()).contains(result);
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

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.parent()).isEmpty();
        assertThat(result.children()).hasSize(2);

        Result child1Result = result.children().get(0);
        Result child2Result = result.children().get(1);

        assertThat(child1Result.parent()).contains(result);
        assertThat(child2Result.parent()).contains(result);

        Result leftResult = child1Result.children().get(0);
        Result rightResult = child2Result.children().get(0);

        assertThat(leftResult.parent()).contains(child1Result);
        assertThat(rightResult.parent()).contains(child2Result);
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

        Result result = Runner.builder().build().run(root);

        assertThat(result.parent()).isEmpty();
        assertThat(result.children()).hasSize(2);

        for (int depth1Index = 0; depth1Index < result.children().size(); depth1Index++) {
            Result depth1Result = result.children().get(depth1Index);
            assertThat(depth1Result.parent()).contains(result);

            for (int depth2Index = 0; depth2Index < depth1Result.children().size(); depth2Index++) {
                Result depth2Result = depth1Result.children().get(depth2Index);
                assertThat(depth2Result.parent()).contains(depth1Result);

                for (int leafIndex = 0; leafIndex < depth2Result.children().size(); leafIndex++) {
                    Result leafResult = depth2Result.children().get(leafIndex);
                    assertThat(leafResult.parent()).contains(depth2Result);
                }
            }
        }
    }

    @Test
    @DisplayName("notifies listener before and after each action")
    void notifiesListenerBeforeAndAfterEachAction() {
        var events = new CopyOnWriteArrayList<String>();
        Action root =
                Sequential.of("root", List.of(Direct.of("first", context -> {}), Direct.of("second", context -> {})));
        Listener listener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                events.add("before:" + action.getClass().getSimpleName().charAt(0));
            }

            @Override
            public void afterAction(Context context, Action action, Result result) {
                events.add("after:" + action.getClass().getSimpleName().charAt(0) + ":" + result.status());
            }
        };

        Result result = Runner.builder().listener(listener).build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(events)
                .containsExactly("before:S", "before:D", "after:D:PASS", "before:D", "after:D:PASS", "after:S:PASS");
    }

    @Test
    @DisplayName("allows child-bearing actions to expose children and parent links")
    void allowsCustomActionsToNavigateActionChildrenViaContext() {
        Action left = Direct.of("left", context -> {});
        Action right = Direct.of("right", context -> {});
        Action root = Sequential.of("root", List.of(left, right));

        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(root.parent()).isEmpty();
        assertThat(root.children()).containsExactly(left, right);
        assertThat(left.parent()).contains(root);
        assertThat(right.parent()).contains(root);
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
                    .add(context.action().name());
        });
    }

    @Test
    @DisplayName("returns default configuration when builder method not used")
    void returnsDefaultConfigurationWhenBuilderMethodNotUsed() {
        Runner runner = Runner.builder().build();
        assertThat(runner.configuration()).isNotNull();
        assertThat(runner.configuration()).isEqualTo(Configuration.defaultProperties());
    }

    @Test
    @DisplayName("stores and returns provided configuration map")
    void storesAndReturnsProvidedConfigurationMap() {
        var expectedConfig = Map.of("key1", "value1", "key2", "value2");
        var runner = Runner.builder().configuration(expectedConfig).build();
        assertThat(runner.configuration()).isEqualTo(expectedConfig);
    }

    @Test
    @DisplayName("replaces configuration when called multiple times")
    void replacesConfigurationWhenCalledMultipleTimes() {
        var firstConfig = Map.of("key1", "value1");
        var secondConfig = Map.of("key2", "value2");
        var runner = Runner.builder()
                .configuration(firstConfig)
                .configuration(secondConfig)
                .build();
        assertThat(runner.configuration()).isEqualTo(secondConfig);
    }

    @Test
    @DisplayName("rejects null configuration")
    void rejectsNullConfiguration() {
        assertThatNullPointerException()
                .isThrownBy(() -> Runner.builder().configuration(null))
                .withMessage("configuration must not be null");
    }

    @Test
    @DisplayName("defensively copies configuration map")
    void defensivelyCopiesConfigurationMap() {
        var originalConfig = new HashMap<String, String>();
        originalConfig.put("key1", "value1");
        Runner runner = Runner.builder().configuration(originalConfig).build();
        originalConfig.put("key2", "value2");
        assertThat(runner.configuration()).doesNotContainKey("key2");
    }

    @Test
    @DisplayName("auto-closes executor service after parallel execution")
    void autoClosesExecutorServiceAfterParallelExecution() {
        Action action = Parallel.of("test", 4, List.of(Direct.of("child", context -> {})));
        var runner = (DefaultRunner) Runner.builder().build();

        var executorService = runner.executorService();
        assertThat(executorService).isNull();

        runner.run(action);
        var postRunExecutorService = runner.executorService();
        assertThat(postRunExecutorService).isNull();
    }
}

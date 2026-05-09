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

package org.paramixel.core.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Value;

@DisplayName("Container")
class ContainerTest {

    @Test
    @DisplayName("builder uses dependent declared policy by default")
    void builderUsesDependentDeclaredPolicyByDefault() {
        Container container =
                Container.builder("container").child(Noop.of("child")).build();

        assertThat(container.getContextMode()).isEqualTo(Action.ContextMode.ISOLATED);
        assertThat(container.getPolicy().childMode()).isEqualTo(Container.ChildMode.DEPENDENT);
        assertThat(container.getPolicy().orderMode()).isEqualTo(Container.OrderMode.DECLARED);
    }

    @Test
    @DisplayName("builder validates arguments")
    void builderValidatesArguments() {
        assertThatThrownBy(() -> Container.builder(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Container.builder(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Container.builder("container").contextMode(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("contextMode must not be null");
        assertThatThrownBy(() -> Container.builder("container").policy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("policy must not be null");
        assertThatThrownBy(() -> Container.builder("container").child(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("child must not be null");
        assertThatIllegalStateException()
                .isThrownBy(() -> Container.builder("empty").build())
                .withMessage("container must contain before, child, or after action");
    }

    @Test
    @DisplayName("policy seed is used with shuffled order")
    void policySeedIsUsedWithShuffledOrder() {
        Container.Policy policy = Container.Policy.builder()
                .seed(42L)
                .orderMode(Container.OrderMode.SHUFFLED)
                .build();

        assertThat(policy.seed()).isEqualTo(42L);
    }

    @Test
    @DisplayName("dependent declared stops after first failure and skips remaining")
    void dependentDeclaredStopsAfterFirstFailureAndSkipsRemaining() {
        List<String> order = new ArrayList<>();
        Action first = direct("first", context -> order.add("first"));
        Action second = direct("second", context -> {
            order.add("second");
            throw new RuntimeException("boom");
        });
        Action third = direct("third", context -> order.add("third"));

        Result result = Runner.builder()
                .build()
                .run(Container.builder("container")
                        .child(first)
                        .child(second)
                        .child(third)
                        .build());

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(order).containsExactly("first", "second");
        assertThat(result.getChildren()).hasSize(3);
        assertThat(result.getChildren().get(2).getStatus().isSkip()).isTrue();
    }

    @Test
    @DisplayName("independent declared runs all children")
    void independentDeclaredRunsAllChildren() {
        List<String> order = new ArrayList<>();
        Container.Policy policy = Container.Policy.builder()
                .childMode(Container.ChildMode.INDEPENDENT)
                .build();

        Result result = Runner.builder()
                .build()
                .run(Container.builder("container")
                        .policy(policy)
                        .child(direct("first", context -> order.add("first")))
                        .child(direct("second", context -> {
                            order.add("second");
                            throw new RuntimeException("boom");
                        }))
                        .child(direct("third", context -> order.add("third")))
                        .build());

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(order).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("shared children use same container store")
    void sharedChildrenUseSameContainerStore() {
        Action writer = Direct.builder("writer")
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> context.getStore().put("key", Value.of("value")))
                .build();
        Action reader = Direct.builder("reader")
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> assertThat(
                                context.getStore().get("key").orElseThrow().cast(String.class))
                        .isEqualTo("value"))
                .build();

        Result result = Runner.builder()
                .build()
                .run(Container.builder("container").child(writer).child(reader).build());

        assertThat(result.getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("isolated children do not use same store")
    void isolatedChildrenDoNotUseSameStore() {
        Action writer = Direct.builder("writer")
                .execute(context -> context.getStore().put("key", Value.of("value")))
                .build();
        Action reader = Direct.builder("reader")
                .execute(context -> assertThat(context.getStore().get("key")).isEmpty())
                .build();

        Result result = Runner.builder()
                .build()
                .run(Container.builder("container").child(writer).child(reader).build());

        assertThat(result.getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("shuffled order with fixed seed is deterministic")
    void shuffledOrderWithFixedSeedIsDeterministic() {
        List<String> order1 = new ArrayList<>();
        List<String> order2 = new ArrayList<>();
        Container.Policy policy = Container.Policy.builder()
                .orderMode(Container.OrderMode.SHUFFLED)
                .seed(12345L)
                .build();

        for (int run = 0; run < 2; run++) {
            List<String> target = (run == 0) ? order1 : order2;
            Action a = direct("alpha", context -> target.add("alpha"));
            Action b = direct("beta", context -> target.add("beta"));
            Action c = direct("gamma", context -> target.add("gamma"));
            Action d = direct("delta", context -> target.add("delta"));
            Action e = direct("epsilon", context -> target.add("epsilon"));

            Runner.builder()
                    .build()
                    .run(Container.builder("container")
                            .policy(Container.Policy.builder()
                                    .orderMode(Container.OrderMode.SHUFFLED)
                                    .seed(12345L)
                                    .build())
                            .child(a)
                            .child(b)
                            .child(c)
                            .child(d)
                            .child(e)
                            .build());
        }

        assertThat(order1).containsExactlyInAnyOrder("alpha", "beta", "gamma", "delta", "epsilon");
        assertThat(order1).isEqualTo(order2);
    }

    @Test
    @DisplayName("shuffled order differs from declared order")
    void shuffledOrderDiffersFromDeclaredOrder() {
        List<String> declaredOrder = new ArrayList<>();
        List<String> shuffledOrder = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            declaredOrder.add("child-" + i);
        }

        for (int i = 0; i < 10; i++) {
            String name = "child-" + i;
            shuffledOrder.add(name);
        }

        Container.Policy shuffledPolicy = Container.Policy.builder()
                .orderMode(Container.OrderMode.SHUFFLED)
                .seed(99L)
                .build();

        List<String> actualShuffled = new ArrayList<>();
        var children = new ArrayList<Action>();
        for (int i = 0; i < 10; i++) {
            String name = "child-" + i;
            children.add(direct(name, context -> actualShuffled.add(name)));
        }

        var builder = Container.builder("container").policy(shuffledPolicy);
        children.forEach(builder::child);
        Runner.builder().build().run(builder.build());

        assertThat(actualShuffled).containsExactlyInAnyOrderElementsOf(declaredOrder);
        assertThat(actualShuffled).isNotEqualTo(declaredOrder);
    }

    private static Direct direct(String name, Direct.Executable executable) {
        return Direct.builder(name).execute(executable).build();
    }
}

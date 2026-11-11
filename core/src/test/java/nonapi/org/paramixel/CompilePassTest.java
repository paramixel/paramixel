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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Conditional;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;

/**
 * Pins the compile pass: one descriptor per action occurrence, the descriptor shape mirroring the action shape, and
 * a subtree shared between two positions producing two independent descriptors.
 *
 * <p>The compile pass itself lives in {@code nonapi.org.paramixel.CompiledPlan}; these tests exercise it through the
 * package-private entry point exposed for tests.
 */
@DisplayName("Compile pass")
class CompilePassTest {

    private static ConcreteDescriptor compile(final Action action) {
        return CompiledPlan.compile(action).root().descriptor;
    }

    @Test
    @DisplayName("a leaf action produces one terminal descriptor")
    void leaf() {
        var descriptor = compile(Step.step("leaf", context -> {}));

        assertThat(descriptor.children()).isEmpty();
        assertThat(descriptor.before()).isEmpty();
        assertThat(descriptor.after()).isEmpty();
        assertThat(descriptor.status()).isEqualTo(Status.PENDING);
        assertThat(descriptor.action().displayName()).isEqualTo("leaf");
    }

    @Test
    @DisplayName("a container produces one descriptor per child in declaration order")
    void container() {
        var descriptor = compile(Sequential.sequential("seq")
                .child(Step.step("a", context -> {}))
                .child(Step.step("b", context -> {}))
                .build());

        assertThat(descriptor.children())
                .extracting(child -> child.action().displayName())
                .containsExactly("a", "b");
        assertThat(descriptor.children())
                .allSatisfy(child -> assertThat(child.parent()).containsSame(descriptor));
    }

    @Test
    @DisplayName("a scope maps before, body and after to separate slots")
    void scope() {
        var descriptor = compile(Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Step.step("body", context -> {}))
                .after(Step.step("after", context -> {}))
                .build());

        assertThat(descriptor.before().orElseThrow().action().displayName()).isEqualTo("before");
        assertThat(descriptor.children())
                .extracting(child -> child.action().displayName())
                .containsExactly("body");
        assertThat(descriptor.after().orElseThrow().action().displayName()).isEqualTo("after");
        assertThat(descriptor.before().orElseThrow().parent()).containsSame(descriptor);
        assertThat(descriptor.after().orElseThrow().parent()).containsSame(descriptor);
    }

    @Test
    @DisplayName("an Instance builds a scope whose before creates the fixture")
    void instance() {
        var descriptor = compile(Instance.of("fixture", Object.class, Object::new)
                .body(Step.step("body", context -> {}))
                .build());

        assertThat(descriptor.before().orElseThrow().action().displayName()).isEqualTo("[instantiate]");
        assertThat(descriptor.children()).hasSize(1);
        assertThat(descriptor.after().orElseThrow().action().displayName()).isEqualTo("[destroy]");
    }

    @Test
    @DisplayName("a Loop expands to one descriptor per iteration")
    void loop() {
        var descriptor = compile(Loop.loop("loop")
                .maxIterations(3)
                .body(Step.step("iteration", context -> {}))
                .build());

        assertThat(descriptor.children()).hasSize(3);
        assertThat(descriptor.children())
                .allSatisfy(child -> assertThat(child.action().displayName()).isEqualTo("iteration"));
        // Each iteration is its own descriptor occurrence.
        assertThat(descriptor.children().stream().map(Descriptor::id).distinct())
                .hasSize(3);
    }

    @Test
    @DisplayName("single-child decorators have exactly one body child and no lifecycle slots")
    void singleChildDecorators() {
        for (var action : List.of(
                Timeout.timeout("timeout")
                        .timeout(java.time.Duration.ofSeconds(1))
                        .body(Step.step("body", context -> {}))
                        .build(),
                Isolated.isolated("isolated", "lock")
                        .body(Step.step("body", context -> {}))
                        .build(),
                Conditional.conditional("conditional", context -> true)
                        .body(Step.step("body", context -> {}))
                        .build())) {
            var descriptor = compile(action);
            assertThat(descriptor.children()).hasSize(1);
            assertThat(descriptor.before()).isEmpty();
            assertThat(descriptor.after()).isEmpty();
        }
    }

    @Test
    @DisplayName("a Parallel keeps its children and pool")
    void parallel() {
        var descriptor = compile(Parallel.parallel("p", "docker")
                .child(Step.step("a", context -> {}))
                .build());

        assertThat(descriptor.children()).hasSize(1);
        assertThat(descriptor.action()).isInstanceOf(Parallel.class);
    }

    @Test
    @DisplayName("a subtree shared between two positions produces independent descriptors")
    void sharedSubtreeProducesIndependentDescriptors() {
        var shared = Step.step("shared", context -> {});
        var descriptor =
                compile(Sequential.sequential("seq").child(shared).child(shared).build());

        assertThat(descriptor.children()).hasSize(2);
        assertThat(descriptor.children().get(0).id())
                .isNotEqualTo(descriptor.children().get(1).id());
    }

    @Test
    @DisplayName("a compiled descriptor's structure cannot be mutated")
    void structureIsImmutable() {
        var descriptor = compile(Step.step("leaf", context -> {}));

        assertThatThrownBy(() -> descriptor.children().add(descriptor))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("rejects a null root action")
    void rejectsNullRoot() {
        assertThatThrownBy(() -> compile(null)).isInstanceOf(NullPointerException.class);
    }

    @Nested
    @DisplayName("every action type")
    class EveryActionType {

        @Test
        @DisplayName("all permitted action types compile")
        void allActionTypesCompile() {
            var actions = List.of(
                    (Action) Step.step("step", context -> {}),
                    Sequential.sequential("seq")
                            .child(Step.step("s", context -> {}))
                            .build(),
                    Parallel.parallel("p", "p1")
                            .child(Step.step("s", context -> {}))
                            .build(),
                    Scope.scope("scope").body(Step.step("s", context -> {})).build(),
                    Loop.loop("loop")
                            .maxIterations(1)
                            .body(Step.step("s", context -> {}))
                            .build(),
                    Timeout.timeout("t")
                            .timeout(java.time.Duration.ofSeconds(1))
                            .body(Step.step("s", context -> {}))
                            .build(),
                    Isolated.isolated("i", "lock")
                            .body(Step.step("s", context -> {}))
                            .build(),
                    Conditional.conditional("c", context -> true)
                            .body(Step.step("s", context -> {}))
                            .build());

            assertThat(actions).hasSize(Action.class.getPermittedSubclasses().length);
            for (var action : actions) {
                assertThat(compile(action)).isNotNull();
            }
        }
    }
}

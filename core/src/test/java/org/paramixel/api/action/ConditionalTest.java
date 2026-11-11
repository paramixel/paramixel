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

package org.paramixel.api.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Context;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.exception.SkipException;

@DisplayName("Conditional action")
class ConditionalTest {

    private static final Predicate<Context> ALWAYS_TRUE = ctx -> true;

    @Test
    @DisplayName("condition true — body passes, node passes")
    void conditionTrueBodyPasses() {
        var action = Conditional.conditional("true-pass", ALWAYS_TRUE)
                .body(Step.step("step", context -> {}))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("condition true — body fails, node fails")
    void conditionTrueBodyFails() {
        var action = Conditional.conditional("true-fail", ALWAYS_TRUE)
                .body(Step.step("step", context -> {
                    throw new RuntimeException("intentional failure");
                }))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).status().isFailed()).isTrue();
    }

    @Test
    @DisplayName("condition true — body skips, node skips")
    void conditionTrueBodySkips() {
        var action = Conditional.conditional("true-skip", ALWAYS_TRUE)
                .body(Step.step("step", context -> {
                    throw SkipException.skip("skip reason");
                }))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.status().isSkipped()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).status().isSkipped()).isTrue();
    }

    @Test
    @DisplayName("condition false — node passes, body skipped")
    void conditionFalseBodySkipped() {
        var action = Conditional.conditional("OS is not Linux", ctx -> false)
                .body(Step.step("step", context -> {}))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).status().isSkipped()).isTrue();
    }

    @Test
    @DisplayName("condition false — body is skipped")
    void conditionFalseBodyIsSkipped() {
        var action = Conditional.conditional("skip-gate", ctx -> false)
                .body(Step.step("step", context -> {}))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).status().isSkipped()).isTrue();
    }

    @Test
    @DisplayName("condition false — node passes, grandchildren all skipped")
    void conditionFalseCascadesToGrandchildren() {
        var action = Conditional.conditional("gate closed", ctx -> false)
                .body(Sequential.sequential("seq")
                        .child(Step.step("child-1", context -> {}))
                        .child(Step.step("child-2", context -> {}))
                        .child(Step.step("child-3", context -> {}))
                        .build())
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.status().isSkipped()).isTrue();
        assertThat(body.children()).hasSize(3);
        for (var grandchild : body.children()) {
            assertThat(grandchild.status().isSkipped()).isTrue();
        }
    }

    @Test
    @DisplayName("predicate throws — node failed with descriptive message, body skipped")
    void predicateThrowsFailsConditional() {
        var action = Conditional.conditional("throw-fail", ctx -> {
                    throw new RuntimeException("boom");
                })
                .body(Step.step("step", context -> {}))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.status().message()).isPresent();
        assertThat(root.status().message().get()).contains("condition evaluation failed");
        assertThat(root.status().message().get()).contains("boom");
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).status().isSkipped()).isTrue();
    }

    @Test
    @DisplayName("predicate wraps checked exception — failed with nested cause")
    void predicateWrapsCheckedException() throws Exception {
        var checked = new Exception("checked exception");
        var action = Conditional.conditional("throw-checked", ctx -> {
                    throw new RuntimeException(checked);
                })
                .body(Step.step("step", context -> {}))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.status().message()).isPresent();
        assertThat(root.status().message().get()).contains("condition evaluation failed");
        assertThat(root.status().message().get()).contains("checked exception");
    }

    @Test
    @DisplayName("conditional inside Sequential.dependent — false condition does not skip siblings")
    void conditionalInsideSequentialDependentDoesNotSkipSiblings() {
        var hitSibling = new boolean[] {false};
        var hitGatedStep = new boolean[] {false};
        var action = Sequential.sequential("seq")
                .dependent()
                .child(Conditional.conditional("gated", ctx -> false)
                        .body(Step.step("gated-step", context -> {
                            hitGatedStep[0] = true;
                        }))
                        .build())
                .child(Step.step("sibling", context -> {
                    hitSibling[0] = true;
                }))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(hitSibling[0])
                .as("A Conditional that did not meet its condition should not prevent "
                        + "remaining dependent siblings from executing")
                .isTrue();
        assertThat(hitGatedStep[0])
                .as("A Conditional that did not meet its condition should not execute its body")
                .isFalse();
        assertThat(root.status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("conditional inside Sequential.independent — false condition does not skip siblings")
    void conditionalInsideSequentialIndependentDoesNotSkipSiblings() {
        var hitGatedStep = new boolean[] {false};
        var hitSibling = new boolean[] {false};
        var action = Sequential.sequential("seq")
                .independent()
                .child(Conditional.conditional("gated-independent", ctx -> false)
                        .body(Step.step("gated-step", context -> {
                            hitGatedStep[0] = true;
                        }))
                        .build())
                .child(Step.step("sibling", context -> {
                    hitSibling[0] = true;
                }))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(hitGatedStep[0])
                .as("A Conditional that did not meet its condition should not execute its body")
                .isFalse();
        assertThat(hitSibling[0]).isTrue();
        assertThat(root.status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("two conditionals in parallel — one true runs, one false passes with skipped body")
    void twoConditionalsInParallelEvalIndependently() {
        var hitTrue = new boolean[] {false};
        var hitFalse = new boolean[] {false};
        var action = Parallel.parallel("parallel", "t0")
                .child(Conditional.conditional("true-branch", ctx -> true)
                        .body(Step.step("true-step", context -> {
                            hitTrue[0] = true;
                        }))
                        .build())
                .child(Conditional.conditional("false-branch", ctx -> false)
                        .body(Step.step("false-step", context -> {
                            hitFalse[0] = true;
                        }))
                        .build())
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(hitTrue[0]).isTrue();
        assertThat(hitFalse[0]).isFalse();
        assertThat(root.status().isPassed()).isTrue();
        var children = root.children();
        assertThat(children).hasSize(2);
        var trueBranch = children.get(0);
        var falseBranch = children.get(1);
        assertThat(trueBranch.status().isPassed()).isTrue();
        assertThat(falseBranch.status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("conditional wraps a Scope — condition true runs full lifecycle")
    void conditionalWrapsScopeRunsFullLifecycle() {
        var messages = new java.util.ArrayList<String>();
        var action = Conditional.conditional("wrap-scope", ctx -> true)
                .body(Scope.scope("scope")
                        .before(Step.step("before", context -> messages.add("before")))
                        .body(Step.step("body", context -> messages.add("body")))
                        .after(Step.step("after", context -> messages.add("after")))
                        .build())
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(messages).containsExactly("before", "body", "after");
    }

    @Test
    @DisplayName("descriptor tree contains body child")
    void descriptorTreeContainsBodyChild() {
        var child = Step.step("leaf", context -> {});
        var action =
                Conditional.conditional("tree-test", ALWAYS_TRUE).body(child).build();

        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.action().displayName()).isEqualTo("tree-test");
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).action().displayName()).isEqualTo("leaf");
    }

    @Nested
    @DisplayName("Builder")
    class Builder {

        @Test
        @DisplayName("build throws when body not configured")
        void buildThrowsWhenBodyNotConfigured() {
            assertThatThrownBy(() ->
                            Conditional.conditional("bad-cond", ctx -> true).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("body action must be configured");
        }

        @Test
        @DisplayName("body accepts another Builder")
        void bodyAcceptsAnotherBuilder() {
            var child = Step.step("inner-step", context -> {});
            var conditional = Conditional.conditional("from-builder", ALWAYS_TRUE)
                    .body(Instance.of("inner", Object.class, Object::new).body(child))
                    .build();

            assertThat(conditional.displayName()).isEqualTo("from-builder");
            assertThat(conditional.body()).isNotNull();
        }

        @Test
        @DisplayName("null displayName throws")
        void nullDisplayNameThrows() {
            assertThatThrownBy(() -> Conditional.conditional(null, ALWAYS_TRUE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("displayName is null");
        }

        @Test
        @DisplayName("blank displayName throws")
        void blankDisplayNameThrows() {
            assertThatThrownBy(() -> Conditional.conditional("", ALWAYS_TRUE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("displayName is blank");
        }

        @Test
        @DisplayName("null condition throws")
        void nullConditionThrows() {
            assertThatThrownBy(() -> Conditional.conditional("name", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("condition is null");
        }

        @Test
        @DisplayName("null body throws")
        void nullBodyThrows() {
            assertThatThrownBy(
                            () -> Conditional.conditional("name", ALWAYS_TRUE).body((Action) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("action is null");
        }

        @Test
        @DisplayName("accessors return configured values")
        void accessorsReturnConfiguredValues() {
            var child = Step.step("child", context -> {});
            var predicate = ALWAYS_TRUE;

            var action = Conditional.conditional("accessor-test", predicate)
                    .body(child)
                    .build();

            assertThat(action.displayName()).isEqualTo("accessor-test");
            assertThat(action.condition()).isSameAs(predicate);
            assertThat(action.body()).isSameAs(child);
        }
    }

    @Test
    @DisplayName("condition false — node passes and the skipped body states the reason")
    void conditionFalseReportsReason() {
        var action = Conditional.conditional("nightly only", ctx -> false)
                .body(Step.step("expensive", context -> {}))
                .build();
        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().kind()).isEqualTo(Status.Kind.PASSED);
        var body = root.children().get(0);
        assertThat(body.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(body.status().message()).hasValue("condition not met: nightly only");
    }
}

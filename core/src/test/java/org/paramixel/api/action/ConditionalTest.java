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
import org.paramixel.api.exception.SkipException;

@DisplayName("Conditional action")
@SuppressWarnings("removal")
class ConditionalTest {

    private static final Predicate<Context> ALWAYS_TRUE = ctx -> true;

    @Test
    @DisplayName("condition true — body passes, node passes")
    void conditionTrueBodyPasses() {
        var action = Conditional.builder("true-pass", ALWAYS_TRUE)
                .body(Step.of("step", context -> {}))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isPassed()).isTrue();
    }

    @Test
    @DisplayName("condition true — body fails, node fails")
    void conditionTrueBodyFails() {
        var action = Conditional.builder("true-fail", ALWAYS_TRUE)
                .body(Step.of("step", context -> {
                    throw new RuntimeException("intentional failure");
                }))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isFailed()).isTrue();
    }

    @Test
    @DisplayName("condition true — body skips, node skips")
    void conditionTrueBodySkips() {
        var action = Conditional.builder("true-skip", ALWAYS_TRUE)
                .body(Step.of("step", context -> {
                    SkipException.skip("skip reason");
                }))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isSkipped()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isSkipped()).isTrue();
    }

    @Test
    @DisplayName("condition false — node passes, body skipped")
    void conditionFalseBodySkipped() {
        var action = Conditional.builder("OS is not Linux", ctx -> false)
                .body(Step.of("step", context -> {}))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isSkipped()).isTrue();
    }

    @Test
    @DisplayName("condition false — body is skipped")
    void conditionFalseBodyIsSkipped() {
        var action = Conditional.builder("skip-gate", ctx -> false)
                .body(Step.of("step", context -> {}))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isSkipped()).isTrue();
    }

    @Test
    @DisplayName("condition false — node passes, grandchildren all skipped")
    void conditionFalseCascadesToGrandchildren() {
        var action = Conditional.builder("gate closed", ctx -> false)
                .body(Sequence.builder("seq")
                        .child(Step.of("child-1", context -> {}))
                        .child(Step.of("child-2", context -> {}))
                        .child(Step.of("child-3", context -> {}))
                        .build())
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.isSkipped()).isTrue();
        assertThat(body.children()).hasSize(3);
        for (var grandchild : body.children()) {
            assertThat(grandchild.isSkipped()).isTrue();
        }
    }

    @Test
    @DisplayName("predicate throws — node failed with descriptive message, body skipped")
    void predicateThrowsFailsConditional() {
        var action = Conditional.builder("throw-fail", ctx -> {
                    throw new RuntimeException("boom");
                })
                .body(Step.of("step", context -> {}))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.message()).isPresent();
        assertThat(root.message().get()).contains("condition evaluation failed");
        assertThat(root.message().get()).contains("boom");
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isSkipped()).isTrue();
    }

    @Test
    @DisplayName("predicate wraps checked exception — failed with nested cause")
    void predicateWrapsCheckedException() throws Exception {
        var checked = new Exception("checked exception");
        var action = Conditional.builder("throw-checked", ctx -> {
                    throw new RuntimeException(checked);
                })
                .body(Step.of("step", context -> {}))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.message()).isPresent();
        assertThat(root.message().get()).contains("condition evaluation failed");
        assertThat(root.message().get()).contains("checked exception");
    }

    @Test
    @DisplayName("conditional inside Sequence.dependent — false condition does not skip siblings")
    void conditionalInsideSequenceDependentDoesNotSkipSiblings() {
        var hitSibling = new boolean[] {false};
        var hitGatedStep = new boolean[] {false};
        var action = Sequence.builder("seq")
                .dependent()
                .child(Conditional.builder("gated", ctx -> false)
                        .body(Step.of("gated-step", context -> {
                            hitGatedStep[0] = true;
                        }))
                        .build())
                .child(Step.of("sibling", context -> {
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
        assertThat(root.isPassed()).isTrue();
    }

    @Test
    @DisplayName("conditional inside Sequential.dependent — false condition does not skip siblings")
    void conditionalInsideSequentialDependentDoesNotSkipSiblings() {
        var hitSibling = new boolean[] {false};
        var hitGatedStep = new boolean[] {false};
        var action = Sequential.builder("seq")
                .dependent()
                .child(Conditional.builder("gated", ctx -> false)
                        .body(Step.of("gated-step", context -> {
                            hitGatedStep[0] = true;
                        }))
                        .build())
                .child(Step.of("sibling", context -> {
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
        assertThat(root.isPassed()).isTrue();
    }

    @Test
    @DisplayName("conditional inside Sequential.independent — false condition does not skip siblings")
    void conditionalInsideSequentialIndependentDoesNotSkipSiblings() {
        var hitGatedStep = new boolean[] {false};
        var hitSibling = new boolean[] {false};
        var action = Sequential.builder("seq")
                .independent()
                .child(Conditional.builder("gated-independent", ctx -> false)
                        .body(Step.of("gated-step", context -> {
                            hitGatedStep[0] = true;
                        }))
                        .build())
                .child(Step.of("sibling", context -> {
                    hitSibling[0] = true;
                }))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(hitGatedStep[0])
                .as("A Conditional that did not meet its condition should not execute its body")
                .isFalse();
        assertThat(hitSibling[0]).isTrue();
        assertThat(root.isPassed()).isTrue();
    }

    @Test
    @DisplayName("conditional inside Sequence.independent — false condition does not skip siblings")
    void conditionalInsideSequenceIndependentDoesNotSkipSiblings() {
        var hitGatedStep = new boolean[] {false};
        var hitSibling = new boolean[] {false};
        var action = Sequence.builder("seq")
                .independent()
                .child(Conditional.builder("gated-independent", ctx -> false)
                        .body(Step.of("gated-step", context -> {
                            hitGatedStep[0] = true;
                        }))
                        .build())
                .child(Step.of("sibling", context -> {
                    hitSibling[0] = true;
                }))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(hitGatedStep[0])
                .as("A Conditional that did not meet its condition should not execute its body")
                .isFalse();
        assertThat(hitSibling[0]).isTrue();
        assertThat(root.isPassed()).isTrue();
    }

    @Test
    @DisplayName("two conditionals in parallel — one true runs, one false passes with skipped body")
    void twoConditionalsInParallelEvalIndependently() {
        var hitTrue = new boolean[] {false};
        var hitFalse = new boolean[] {false};
        var action = Parallel.builder("parallel")
                .child(Conditional.builder("true-branch", ctx -> true)
                        .body(Step.of("true-step", context -> {
                            hitTrue[0] = true;
                        }))
                        .build())
                .child(Conditional.builder("false-branch", ctx -> false)
                        .body(Step.of("false-step", context -> {
                            hitFalse[0] = true;
                        }))
                        .build())
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(hitTrue[0]).isTrue();
        assertThat(hitFalse[0]).isFalse();
        assertThat(root.isPassed()).isTrue();
        var children = root.children();
        assertThat(children).hasSize(2);
        var trueBranch = children.get(0);
        var falseBranch = children.get(1);
        assertThat(trueBranch.isPassed()).isTrue();
        assertThat(falseBranch.isPassed()).isTrue();
    }

    @Test
    @DisplayName("conditional wraps a Scope — condition true runs full lifecycle")
    void conditionalWrapsScopeRunsFullLifecycle() {
        var messages = new java.util.ArrayList<String>();
        var action = Conditional.builder("wrap-scope", ctx -> true)
                .body(Scope.builder("scope")
                        .before(Step.of("before", context -> messages.add("before")))
                        .body(Step.of("body", context -> messages.add("body")))
                        .after(Step.of("after", context -> messages.add("after")))
                        .build())
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(messages).containsExactly("before", "body", "after");
    }

    @Test
    @DisplayName("descriptor tree contains body child")
    void descriptorTreeContainsBodyChild() {
        var child = Step.of("leaf", context -> {});
        var action = Conditional.builder("tree-test", ALWAYS_TRUE).body(child).build();

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
            assertThatThrownBy(
                            () -> Conditional.builder("bad-cond", ctx -> true).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("body action must be configured");
        }

        @Test
        @DisplayName("body accepts another Builder")
        void bodyAcceptsAnotherBuilder() {
            var child = Step.of("inner-step", context -> {});
            var conditional = Conditional.builder("from-builder", ALWAYS_TRUE)
                    .body(Instance.builder("inner", Object::new).body(child))
                    .build();

            assertThat(conditional.displayName()).isEqualTo("from-builder");
            assertThat(conditional.body()).isNotNull();
        }

        @Test
        @DisplayName("null displayName throws")
        void nullDisplayNameThrows() {
            assertThatThrownBy(() -> Conditional.builder(null, ALWAYS_TRUE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("displayName is null");
        }

        @Test
        @DisplayName("blank displayName throws")
        void blankDisplayNameThrows() {
            assertThatThrownBy(() -> Conditional.builder("", ALWAYS_TRUE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("displayName is blank");
        }

        @Test
        @DisplayName("null condition throws")
        void nullConditionThrows() {
            assertThatThrownBy(() -> Conditional.builder("name", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("condition is null");
        }

        @Test
        @DisplayName("null body throws")
        void nullBodyThrows() {
            assertThatThrownBy(() -> Conditional.builder("name", ALWAYS_TRUE).body((Action) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("action is null");
        }

        @Test
        @DisplayName("null builder body throws")
        void nullBuilderBodyThrows() {
            assertThatThrownBy(() ->
                            Conditional.builder("name", ALWAYS_TRUE).body((org.paramixel.api.action.Builder) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("builder is null");
        }

        @Test
        @DisplayName("accessors return configured values")
        void accessorsReturnConfiguredValues() {
            var child = Step.of("child", context -> {});
            var predicate = ALWAYS_TRUE;

            var action =
                    Conditional.builder("accessor-test", predicate).body(child).build();

            assertThat(action.displayName()).isEqualTo("accessor-test");
            assertThat(action.condition()).isSameAs(predicate);
            assertThat(action.body()).isSameAs(child);
        }
    }
}

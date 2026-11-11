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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

/**
 * Pins the typed-fixture lifecycle: the factory runs only inside the node's own before step, the after step runs
 * exactly when creation was attempted, and a failed creation binds nothing.
 */
@DisplayName("Instance lifecycle")
@org.junit.jupiter.api.Timeout(60)
class InstanceLifecycleTest {

    private static Runner runner() {
        return Runner.builder().configuration(Configuration.of(Map.of())).build();
    }

    private static Descriptor run(final Action action) {
        return runner().run(action).descriptor().orElseThrow();
    }

    static final class Fixture implements AutoCloseable {

        private final AtomicInteger closed;
        private int beforeCalls;
        private int bodyCalls;
        private int afterCalls;

        Fixture(final AtomicInteger closed) {
            this.closed = closed;
        }

        void before() {
            beforeCalls++;
        }

        void body() {
            bodyCalls++;
        }

        void after() {
            afterCalls++;
        }

        @Override
        public void close() {
            closed.incrementAndGet();
        }
    }

    @Test
    @DisplayName("before, body and after see the same fixture and it is closed once")
    void beforeBodyAfterShareTheFixture() {
        var closed = new AtomicInteger();
        Fixture[] seen = new Fixture[3];

        var action = Instance.of("fixture", Fixture.class, () -> new Fixture(closed))
                .before("setUp()", fixture -> {
                    seen[0] = fixture;
                    fixture.before();
                })
                .body(Step.step("body", context -> {
                    var fixture = context.requireInstance(Fixture.class);
                    seen[1] = fixture;
                    fixture.body();
                }))
                .after("tearDown()", fixture -> {
                    seen[2] = fixture;
                    fixture.after();
                })
                .build();

        var root = run(action);

        assertThat(root.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(seen[0]).isSameAs(seen[1]).isSameAs(seen[2]);
        assertThat(seen[0].beforeCalls).isEqualTo(1);
        assertThat(seen[0].bodyCalls).isEqualTo(1);
        assertThat(seen[0].afterCalls).isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a skipped node never calls the factory and does not run after")
    void skippedNodeNeverCreatesTheFixture() {
        var factoryCalls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Sequential.sequential("root")
                .child(Step.step("fail", context -> {
                    throw FailException.fail("stop");
                }))
                .child(Instance.of("fixture", Fixture.class, () -> {
                            factoryCalls.incrementAndGet();
                            return new Fixture(closed);
                        })
                        .body(Step.step("body", context -> {})))
                .build();

        var root = run(action);

        assertThat(root.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(factoryCalls.get()).isZero();
        assertThat(closed.get()).isZero();
        var instance = root.children().get(1);
        assertThat(instance.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(instance.before().orElseThrow().status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(instance.after().orElseThrow().status().kind()).isEqualTo(Status.Kind.SKIPPED);
    }

    @Test
    @DisplayName("an aborted sibling does not short-circuit a dependent sequence, so the fixture is created")
    void abortedSiblingDoesNotStopTheSequence() {
        var factoryCalls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Sequential.sequential("root")
                .child(Step.step("abort", context -> {
                    throw org.paramixel.api.exception.AbortException.abort("stop");
                }))
                .child(Instance.of("fixture", Fixture.class, () -> {
                            factoryCalls.incrementAndGet();
                            return new Fixture(closed);
                        })
                        .body(Step.step("body", context -> {})))
                .build();

        var root = run(action);

        assertThat(root.status().kind()).isEqualTo(Status.Kind.ABORTED);
        assertThat(factoryCalls.get()).isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a conditional whose condition is false never calls the factory")
    void conditionalFalseNeverCreatesTheFixture() {
        var factoryCalls = new AtomicInteger();
        var closed = new AtomicInteger();

        var action = Conditional.conditional("off", context -> false)
                .body(Instance.of("fixture", Fixture.class, () -> {
                            factoryCalls.incrementAndGet();
                            return new Fixture(closed);
                        })
                        .body(Step.step("body", context -> {})))
                .build();

        var root = run(action);

        assertThat(root.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(factoryCalls.get()).isZero();
        assertThat(closed.get()).isZero();
    }

    @Test
    @DisplayName("a failing factory skips the body, runs after as a no-op and leaves siblings unaffected")
    void failingFactorySkipsBodyAndKeepsSiblings() {
        var siblingCalls = new AtomicInteger();
        var bodyCalls = new AtomicInteger();

        var action = Parallel.parallel("root", "test")
                .child(Instance.of("broken", Fixture.class, () -> {
                            throw new IllegalStateException("cannot create");
                        })
                        .body(Step.step("body", context -> bodyCalls.incrementAndGet())))
                .child(Step.step("sibling", context -> siblingCalls.incrementAndGet()))
                .build();

        var result = Runner.builder()
                .configuration(Configuration.of(Map.of("paramixel.parallelism.test", "2")))
                .build()
                .run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(bodyCalls.get()).isZero();
        assertThat(siblingCalls.get()).isEqualTo(1);
        var instance = root.children().get(0);
        assertThat(instance.children().get(0).status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(instance.after().orElseThrow().status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("a failing before step skips the body and still runs after")
    void failingBeforeSkipsBodyAndRunsAfter() {
        var closed = new AtomicInteger();
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Instance.of("fixture", Fixture.class, () -> new Fixture(closed))
                .before("setUp()", fixture -> {
                    throw FailException.fail("setUp failed");
                })
                .body(Step.step("body", context -> bodyCalls.incrementAndGet()))
                .after("tearDown()", fixture -> afterCalls.incrementAndGet())
                .build();

        var root = run(action);

        assertThat(root.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a failing after step fails the node and still closes the fixture")
    void failingAfterStillClosesTheFixture() {
        var closed = new AtomicInteger();

        var action = Instance.of("fixture", Fixture.class, () -> new Fixture(closed))
                .body(Step.step("body", context -> {}))
                .after("tearDown()", fixture -> {
                    throw FailException.fail("tearDown failed");
                })
                .build();

        var root = run(action);

        assertThat(root.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a skipped body still runs after and closes the fixture")
    void skippedBodyStillRunsAfter() {
        var closed = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Instance.of("fixture", Fixture.class, () -> new Fixture(closed))
                .body(Step.step("body", context -> {
                    throw SkipException.skip("not today");
                }))
                .after("tearDown()", fixture -> afterCalls.incrementAndGet())
                .build();

        var root = run(action);

        assertThat(root.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(afterCalls.get()).isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("sibling instances are independent")
    void siblingInstancesAreIndependent() {
        var closed = new AtomicInteger();
        var action = Sequential.sequential("root")
                .child(Instance.of("a", Fixture.class, () -> new Fixture(closed))
                        .body(Step.step("a-body", context -> {})))
                .child(Instance.of("b", Fixture.class, () -> new Fixture(closed))
                        .body(Step.step("b-body", context -> {})))
                .build();

        var root = run(action);

        assertThat(root.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(closed.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("the fixture is available through Context.instance and requireInstance")
    void contextLookup() {
        var action = Instance.of("fixture", Fixture.class, () -> new Fixture(new AtomicInteger()))
                .body(Sequential.sequential("body")
                        .child(Step.step("optional", context -> {
                            if (context.instance(Fixture.class).isEmpty()) {
                                throw FailException.fail("fixture is not available");
                            }
                        }))
                        .child(Step.step("required", context -> context.requireInstance(Fixture.class))))
                .build();

        assertThat(run(action).status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("the tree reports the generated lifecycle slots")
    void reportsGeneratedSlots() {
        var root = run(Instance.of("fixture", Fixture.class, () -> new Fixture(new AtomicInteger()))
                .body(Step.step("body", context -> {}))
                .build());

        assertThat(root.before().orElseThrow().displayName()).isEqualTo("[instantiate]");
        assertThat(root.children()).extracting(Descriptor::displayName).containsExactly("body");
        assertThat(root.after().orElseThrow().displayName()).isEqualTo("[destroy]");
    }

    @Test
    @DisplayName("the fixture type is bound by its declared type")
    void bindsDeclaredType() {
        var action = Instance.of("fixture", CharSequence.class, () -> "fixture")
                .body(Step.step("body", context -> {
                    var value = context.requireInstance(CharSequence.class);
                    var exact = context.requireInstance(String.class);
                    if (!value.equals(exact)) {
                        throw FailException.fail("declared and runtime lookups differ");
                    }
                }))
                .build();

        assertThat(run(action).status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("a fixture declared as a supertype is found by its runtime class")
    void declaredSupertypeFoundByRuntimeClass() {
        var action = Instance.of("fixture", CharSequence.class, () -> new StringBuilder("value"))
                .body(Step.step("body", context -> context.requireInstance(StringBuilder.class)))
                .build();

        assertThat(run(action).status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("nested fixtures bind the nearest one")
    void nestedFixturesBindNearest() {
        var inner = Instance.of("inner", String.class, () -> "inner")
                .body(Step.step("inner-body", context -> {
                    if (!"inner".equals(context.requireInstance(String.class))) {
                        throw FailException.fail("expected the inner fixture");
                    }
                }))
                .build();
        var outer =
                Instance.of("outer", String.class, () -> "outer").body(inner).build();

        assertThat(run(outer).status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("a null factory result fails the instantiate step")
    void nullFactoryResultFails() {
        var action = Instance.of("fixture", Fixture.class, () -> null)
                .body(Step.step("body", context -> {}))
                .build();

        var root = run(action);

        assertThat(root.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(root.before().orElseThrow().status().message()).contains("factory returned null");
    }

    @Test
    @DisplayName("the builder requires a body")
    void builderRequiresBody() {
        var builder = Instance.of("fixture", Fixture.class, () -> new Fixture(new AtomicInteger()));

        org.assertj.core.api.Assertions.assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("body action must be configured");
    }

    @Test
    @DisplayName("the builder validates names and consumers")
    void builderValidatesArguments() {
        var builder = Instance.of("fixture", Fixture.class, () -> new Fixture(new AtomicInteger()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> builder.before(" ", fixture -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> builder.after(null, fixture -> {}))
                .isInstanceOf(NullPointerException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> builder.before("name", null))
                .isInstanceOf(NullPointerException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> builder.after("name", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a shared fixture is visible to children of a nested Parallel")
    void sharedFixtureVisibleToNestedParallel() {
        var action = Instance.of("fixture", Fixture.class, () -> new Fixture(new AtomicInteger()))
                .body(Parallel.parallel("branches", "test")
                        .child(Step.step("a", context -> context.requireInstance(Fixture.class)))
                        .child(Step.step("b", context -> context.requireInstance(Fixture.class))))
                .build();

        var result = Runner.builder()
                .configuration(Configuration.of(Map.of("paramixel.parallelism.test", "2")))
                .build()
                .run(action);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.descriptor().orElseThrow().children()).hasSize(1);
        assertThat(result.descriptor().orElseThrow().children().get(0).children())
                .hasSize(2);
    }

    @Test
    @DisplayName("an instance with a before and after step reports phase sequentials")
    void reportsPhaseSequentials() {
        var root = run(Instance.of("fixture", Fixture.class, () -> new Fixture(new AtomicInteger()))
                .before("setUp()", Fixture::before)
                .body(Sequential.sequential("tests").child(Step.step("test", context -> {})))
                .after("tearDown()", Fixture::after)
                .build());

        var before = root.before().orElseThrow();
        assertThat(before.children()).extracting(Descriptor::displayName).containsExactly("[instantiate]", "setUp()");
        var after = root.after().orElseThrow();
        assertThat(after.children()).extracting(Descriptor::displayName).containsExactly("tearDown()", "[destroy]");
        assertThat(root.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("an untyped step needs no fixture")
    void untypedStepNeedsNoFixture() {
        assertThat(run(Step.step("plain", context -> {})).status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("the tree's fixture steps are internal and marked with brackets")
    void generatedStepsAreMarkedInternal() {
        var root = run(Instance.of("fixture", Fixture.class, () -> new Fixture(new AtomicInteger()))
                .body(Step.step("body", context -> {}))
                .build());

        var internal = List.of(root.before().orElseThrow(), root.after().orElseThrow());
        assertThat(internal)
                .allSatisfy(descriptor ->
                        assertThat(descriptor.displayName()).startsWith("[").endsWith("]"));
    }
}

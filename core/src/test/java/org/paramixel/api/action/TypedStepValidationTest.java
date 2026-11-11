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

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;

/**
 * Pins the compile-time fixture checks: a typed step must find an enclosing fixture, and a step whose enclosing
 * fixtures are ambiguous is rejected, both before any user code runs and both naming the step.
 */
@DisplayName("Typed step validation")
@org.junit.jupiter.api.Timeout(60)
class TypedStepValidationTest {

    interface Fixture {}

    static class BaseFixture implements Fixture {}

    static final class ConcreteFixture extends BaseFixture {}

    private static Result run(final Action action) {
        return Runner.builder()
                .configuration(Configuration.of(Map.of()))
                .build()
                .run(action);
    }

    @Test
    @DisplayName("a typed step with no enclosing fixture is rejected before any user code runs")
    void missingFixtureRejected() {
        var result = run(Sequential.sequential("root")
                .child(Step.step("needs-fixture", Fixture.class, fixture -> {}))
                .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        var message = result.status().message().orElseThrow();
        assertThat(message).contains("needs-fixture");
        assertThat(message).contains(Fixture.class.getName());
        assertThat(message).contains("no enclosing Instance");
    }

    @Test
    @DisplayName("the rejection names the enclosing path of the step")
    void rejectionNamesThePath() {
        var result = run(Sequential.sequential("outer")
                .child(Sequential.sequential("inner")
                        .child(Step.step("deep-step", Fixture.class, fixture -> {}))
                        .build())
                .build());

        assertThat(result.status().message().orElseThrow()).contains("outer / inner / deep-step");
    }

    @Test
    @DisplayName("an untyped step needs no fixture")
    void untypedStepIsFine() {
        assertThat(run(Step.step("plain", context -> {})).status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("a typed step inside a matching fixture runs")
    void matchingFixtureRuns() {
        var result = run(Instance.of("fixture", Fixture.class, BaseFixture::new)
                .body(Step.step("step", Fixture.class, fixture -> {}))
                .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("a step declared as a supertype accepts a fixture declared as a subtype")
    void supertypeStepAcceptsSubtypeFixture() {
        var result = run(Instance.of("fixture", ConcreteFixture.class, ConcreteFixture::new)
                .body(Step.step("step", Fixture.class, fixture -> {}))
                .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("a step declared as a subtype is rejected for a fixture declared as an unrelated supertype")
    void subtypeStepRejectedForUnrelatedFixture() {
        var result = run(Instance.of("fixture", Object.class, Object::new)
                .body(Step.step("step", Fixture.class, fixture -> {}))
                .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.status().message().orElseThrow()).contains("step");
        assertThat(result.status().message().orElseThrow()).contains("no enclosing Instance provides");
    }

    @Test
    @DisplayName("a nested fixture is visible to the steps of its subtree")
    void nestedFixtureVisible() {
        var result = run(Sequential.sequential("root")
                .child(Instance.of("outer", Fixture.class, BaseFixture::new)
                        .body(Instance.of("inner", String.class, () -> "inner")
                                .body(Step.step("step", Fixture.class, fixture -> {})))
                        .build())
                .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("a step whose enclosing fixtures are ambiguous is rejected")
    void ambiguousFixturesRejected() {
        var result = run(Instance.of("outer", BaseFixture.class, BaseFixture::new)
                .body(Instance.of("inner", ConcreteFixture.class, ConcreteFixture::new)
                        .body(Step.step("step", Fixture.class, fixture -> {}))
                        .build())
                .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        var message = result.status().message().orElseThrow();
        assertThat(message).contains("more than one compatible fixture");
        assertThat(message).contains(BaseFixture.class.getName());
        assertThat(message).contains(ConcreteFixture.class.getName());
    }

    @Test
    @DisplayName("sibling fixtures of the same type in unrelated subtrees are accepted")
    void siblingFixturesAccepted() {
        var result = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.poolKey("branches"), "2")))
                .build()
                .run(Parallel.parallel("root", "branches")
                        .child(Instance.of("a", Fixture.class, BaseFixture::new)
                                .body(Step.step("a-step", Fixture.class, fixture -> {})))
                        .child(Instance.of("b", Fixture.class, BaseFixture::new)
                                .body(Step.step("b-step", Fixture.class, fixture -> {})))
                        .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }
}

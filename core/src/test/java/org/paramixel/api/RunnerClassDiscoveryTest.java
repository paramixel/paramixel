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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

@DisplayName("Runner class discovery")
class RunnerClassDiscoveryTest {

    @Test
    @DisplayName("finds single factory via run with classOf selector")
    void findsSingleFactory() {
        var selector =
                Selector.builder().classOf(ActionResolverSmokeFixture.class).build();
        var result = Runner.builder().build().run(selector);
        var children = result.descriptor().orElseThrow().children();
        assertThat(children).hasSize(1);
        assertThat(children.get(0).action().displayName()).isEqualTo("smoke-action");
    }

    @Test
    @DisplayName("applies selector tag filter via matchesTag")
    void appliesTagFiltersWithAndSemantics() {
        var selector = Selector.builder()
                .classOf(ActionResolverMultiTagFixture.class)
                .build()
                .and(Selector.builder()
                        .tagPattern(Pattern.compile("smoke-fast"))
                        .build());

        var result = Runner.builder().build().run(selector);

        var children = result.descriptor().orElseThrow().children();
        assertThat(children).hasSize(1);
        assertThat(children.get(0).action().displayName()).isEqualTo("multi-tag-action");
    }

    @Test
    @DisplayName("returns empty when tag filter does not match")
    void returnsEmptyWhenTagFiltersDoNotBothMatch() {
        var selector = Selector.builder()
                .classOf(ActionResolverMultiTagFixture.class)
                .build()
                .and(Selector.builder().tagPattern(Pattern.compile("missing")).build());

        assertThat(Runner.builder().build().run(selector).descriptor()).isEmpty();
    }

    @Test
    @DisplayName("blank tag values produce discovery validation failure action")
    void rejectsBlankTagValues() {
        var selector = Selector.builder().classOf(BlankTagFactory.class).build();

        var result = Runner.builder().build().run(selector);

        var children = result.descriptor().orElseThrow().children();
        assertThat(children).hasSize(1);
        assertThat(children.get(0).action().displayName()).startsWith("Discovery validation failure:");
    }

    @Test
    @DisplayName("child override without factory shadows parent factory")
    void childOverrideWithoutFactoryShadowsParentFactory() {
        var selector =
                Selector.builder().classOf(ChildOverridesWithoutFactory.class).build();

        assertThat(Runner.builder().build().run(selector).descriptor()).isEmpty();
    }

    @Test
    @DisplayName("throws when more than one factory exists in a hierarchy")
    void throwsWhenMoreThanOneFactoryExistsInHierarchy() {
        var selector = Selector.builder().classOf(ChildDeclaresOwnFactory.class).build();

        assertThatThrownBy(() -> Runner.builder().build().run(selector))
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("more than one @Paramixel.Factory method");
    }

    @Test
    @DisplayName("error from factory method is re-thrown directly, not wrapped")
    void errorFromFactoryMethodIsReThrownDirectlyNotWrapped() {
        var selector = Selector.builder().classOf(ErrorThrowingFactory.class).build();

        assertThatThrownBy(() -> Runner.builder().build().run(selector))
                .isInstanceOf(TestError.class)
                .hasMessage("factory error");
    }

    static class ParentFactory {
        @Paramixel.Factory
        public static Action factory() {
            return Step.step("parent-action", context -> {});
        }
    }

    static class ChildOverridesWithoutFactory extends ParentFactory {
        public static Action factory() {
            return Step.step("child-action", context -> {});
        }
    }

    static class ParentFactoryTwo {
        @Paramixel.Factory
        public static Action parentAction() {
            return Step.step("parent-action", context -> {});
        }
    }

    static class ChildDeclaresOwnFactory extends ParentFactoryTwo {
        @Paramixel.Factory
        public static Action childAction() {
            return Step.step("child-action", context -> {});
        }
    }

    static class BlankTagFactory {
        @Paramixel.Factory
        @Paramixel.Tag(" ")
        public static Action factory() {
            return Step.step("blank-tag-action", context -> {});
        }
    }

    static class ErrorThrowingFactory {
        @Paramixel.Factory
        public static Action factory() {
            throw new TestError("factory error");
        }
    }

    static class TestError extends Error {
        TestError(final String message) {
            super(message);
        }
    }
}

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

import java.util.Optional;
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
        Selector selector = Selector.classOf(ClasspathResolverSmokeFixture.class);
        Optional<Result> result = Runner.builder().build().run(selector);
        assertThat(result).isPresent();
        var children = result.orElseThrow().descriptor().orElseThrow().children();
        assertThat(children).hasSize(1);
        assertThat(children.get(0).metadata().name()).isEqualTo("smoke-action");
    }

    @Test
    @DisplayName("applies selector tag filter via matchesTag")
    void appliesTagFiltersWithAndSemantics() {
        Selector selector =
                Selector.and(Selector.classOf(ClasspathResolverMultiTagFixture.class), Selector.tagRegex("smoke"));

        Optional<Result> result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        var children = result.orElseThrow().descriptor().orElseThrow().children();
        assertThat(children).hasSize(1);
        assertThat(children.get(0).metadata().name()).isEqualTo("multi-tag-action");
    }

    @Test
    @DisplayName("returns empty when tag filter does not match")
    void returnsEmptyWhenTagFiltersDoNotBothMatch() {
        Selector selector =
                Selector.and(Selector.classOf(ClasspathResolverMultiTagFixture.class), Selector.tagRegex("missing"));

        assertThat(Runner.builder().build().run(selector)).isEmpty();
    }

    @Test
    @DisplayName("blank tag values produce discovery validation failure action")
    void rejectsBlankTagValues() {
        Selector selector = Selector.classOf(BlankTagFactory.class);

        Optional<Result> result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        var children = result.orElseThrow().descriptor().orElseThrow().children();
        assertThat(children).hasSize(1);
        assertThat(children.get(0).metadata().name()).startsWith("Discovery validation failure:");
    }

    @Test
    @DisplayName("child override without factory shadows parent factory")
    void childOverrideWithoutFactoryShadowsParentFactory() {
        Selector selector = Selector.classOf(ChildOverridesWithoutFactory.class);

        assertThat(Runner.builder().build().run(selector)).isEmpty();
    }

    @Test
    @DisplayName("throws when more than one factory exists in a hierarchy")
    void throwsWhenMoreThanOneFactoryExistsInHierarchy() {
        Selector selector = Selector.classOf(ChildDeclaresOwnFactory.class);

        assertThatThrownBy(() -> Runner.builder().build().run(selector))
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("more than one @Paramixel.Factory method");
    }

    @Test
    @DisplayName("error from factory method is re-thrown directly, not wrapped")
    void errorFromFactoryMethodIsReThrownDirectlyNotWrapped() {
        Selector selector = Selector.classOf(ErrorThrowingFactory.class);

        assertThatThrownBy(() -> Runner.builder().build().run(selector))
                .isInstanceOf(TestError.class)
                .hasMessage("factory error");
    }

    static class ParentFactory {
        @Paramixel.Factory
        public static Action<?> factory() {
            return Step.of("parent-action", context -> {});
        }
    }

    static class ChildOverridesWithoutFactory extends ParentFactory {
        public static Action<?> factory() {
            return Step.of("child-action", context -> {});
        }
    }

    static class ParentFactoryTwo {
        @Paramixel.Factory
        public static Action<?> parentAction() {
            return Step.of("parent-action", context -> {});
        }
    }

    static class ChildDeclaresOwnFactory extends ParentFactoryTwo {
        @Paramixel.Factory
        public static Action<?> childAction() {
            return Step.of("child-action", context -> {});
        }
    }

    static class BlankTagFactory {
        @Paramixel.Factory
        @Paramixel.Tag(" ")
        public static Action<?> factory() {
            return Step.of("blank-tag-action", context -> {});
        }
    }

    static class ErrorThrowingFactory {
        @Paramixel.Factory
        public static Action<?> factory() {
            throw new TestError("factory error");
        }
    }

    static class TestError extends Error {
        TestError(final String message) {
            super(message);
        }
    }
}

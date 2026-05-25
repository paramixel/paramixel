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

package org.paramixel.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ClasspathResolverMultiTagFixture;
import org.paramixel.api.ClasspathResolverSmokeFixture;
import org.paramixel.api.Configuration;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Spec;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

@DisplayName("ClasspathResolver")
class ClasspathResolverTest {

    @Test
    @DisplayName("finds single factory")
    void findsSingleFactory() {
        Selector selector = Selector.classOf(ClasspathResolverSmokeFixture.class);
        var configuration = Configuration.defaultConfiguration();
        Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();

        assertThat(result).isPresent();
        Parallel<?> root = (Parallel<?>) result.orElseThrow();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).name()).isEqualTo("smoke-action");
    }

    @Test
    @DisplayName("applies selector tag filter with AND semantics")
    void appliesTagFiltersWithAndSemantics() {
        Selector selector =
                Selector.and(Selector.classOf(ClasspathResolverMultiTagFixture.class), Selector.tagRegex("smoke"));

        var configuration = Configuration.defaultConfiguration();
        Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();

        assertThat(result).isPresent();
        Parallel<?> root = (Parallel<?>) result.orElseThrow();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).name()).isEqualTo("multi-tag-action");
    }

    @Test
    @DisplayName("returns empty when tag filter does not match")
    void returnsEmptyWhenTagFiltersDoNotBothMatch() {
        Selector selector =
                Selector.and(Selector.classOf(ClasspathResolverMultiTagFixture.class), Selector.tagRegex("missing"));

        var configuration = Configuration.defaultConfiguration();
        Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("child override without factory shadows parent factory")
    void childOverrideWithoutFactoryShadowsParentFactory() {
        Selector selector = Selector.classOf(ChildOverridesWithoutFactory.class);

        var configuration = Configuration.defaultConfiguration();
        assertThat(new ClasspathResolver(configuration, selector).resolveActions())
                .isEmpty();
    }

    @Test
    @DisplayName("throws when more than one factory exists in a hierarchy")
    void throwsWhenMoreThanOneFactoryExistsInHierarchy() {
        Selector selector = Selector.classOf(ChildDeclaresOwnFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThatThrownBy(() -> new ClasspathResolver(configuration, selector).resolveActions())
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("more than one @Paramixel.Factory method");
    }

    @Test
    @DisplayName("error from factory method is re-thrown directly, not wrapped")
    void errorFromFactoryMethodIsReThrownDirectlyNotWrapped() {
        Selector selector = Selector.classOf(ErrorThrowingFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThatThrownBy(() -> new ClasspathResolver(configuration, selector).resolveActions())
                .isInstanceOf(TestError.class)
                .hasMessage("factory error");
    }

    @Test
    @DisplayName("skips @Paramixel.Disabled factory")
    void skipsDisabledFactory() {
        Selector selector = Selector.classOf(DisabledFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThat(new ClasspathResolver(configuration, selector).resolveActions())
                .isEmpty();
    }

    @Test
    @DisplayName("skips @Paramixel.Disabled with reason factory")
    void skipsDisabledWithReasonFactory() {
        Selector selector = Selector.classOf(DisabledWithReasonFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThat(new ClasspathResolver(configuration, selector).resolveActions())
                .isEmpty();
    }

    @Test
    @DisplayName("null-returning factory produces skipped action")
    void nullReturningFactoryProducesSkippedAction() {
        Selector selector = Selector.classOf(NullReturningFactory.class);
        var configuration = Configuration.defaultConfiguration();
        Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();

        assertThat(result).isPresent();
        Runner runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "1")))
                .build();
        var runResult = runner.run(result.orElseThrow());
        assertThat(runResult.status()).isEqualTo(Status.SKIPPED);
        var root = runResult.descriptor().orElseThrow();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).metadata().status()).isEqualTo(Status.SKIPPED);
    }

    @Test
    @DisplayName("resolves Spec-returning factory")
    void resolvesSpecReturningFactory() {
        Selector selector = Selector.classOf(SpecReturningFactory.class);
        var configuration = Configuration.defaultConfiguration();
        Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();

        assertThat(result).isPresent();
        Parallel<?> root = (Parallel<?>) result.orElseThrow();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).name()).isEqualTo("spec-action");
    }

    @Test
    @DisplayName("resolves actions using selector")
    void resolvesActionsUsingSelector() {
        Selector selector =
                Selector.and(Selector.classRegex("ClasspathResolverSmokeFixture"), Selector.tagRegex("^smoke$"));

        var configuration = Configuration.defaultConfiguration();
        Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();

        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).isInstanceOf(Parallel.class);
    }

    @Test
    @DisplayName("returns empty when selector tag filter excludes all matches")
    void returnsEmptyWhenSelectorTagFilterExcludesAllMatches() {
        Selector selector =
                Selector.and(Selector.classRegex("ClasspathResolverSmokeFixture"), Selector.tagRegex("nomatch"));

        var configuration = Configuration.defaultConfiguration();
        assertThat(new ClasspathResolver(configuration, selector).resolveActions())
                .isEmpty();
    }

    @Test
    @DisplayName("continues after blank tag value during classpath discovery")
    void continuesAfterBlankTagValueDuringClasspathDiscovery() {
        Selector selector = Selector.classRegex(
                "ClasspathResolverValidTaggedDiscoveryFixture|ClasspathResolverInvalidBlankTagDiscoveryFixture");

        var configuration = Configuration.defaultConfiguration();
        Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();

        assertThat(result).isPresent();
        Parallel<?> root = (Parallel<?>) result.orElseThrow();
        assertThat(root.children()).hasSize(2);
        assertThat(root.children())
                .anySatisfy(action -> assertThat(action.name()).isEqualTo("valid-tagged-discovery"));
        assertThat(root.children())
                .anySatisfy(action -> assertThat(action.name()).startsWith("Discovery validation failure:"));
    }

    @Test
    @DisplayName("discovery validation failure action fails when run")
    void discoveryValidationFailureActionFailsWhenRun() {
        Selector selector = Selector.classRegex(
                "ClasspathResolverValidTaggedDiscoveryFixture|ClasspathResolverInvalidBlankTagDiscoveryFixture");

        var configuration = Configuration.defaultConfiguration();
        Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();

        assertThat(result).isPresent();
        Runner runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "1")))
                .build();
        var rootResult = runner.run(result.orElseThrow()).descriptor().orElseThrow();
        assertThat(rootResult.metadata().status().isFailed()).isTrue();
        assertThat(rootResult.children()).anySatisfy(child -> {
            assertThat(child.metadata().status().isPassed()).isTrue();
        });
        assertThat(rootResult.children()).anySatisfy(child -> {
            assertThat(child.metadata().status().isFailed()).isTrue();
            assertThat(child.metadata().name()).startsWith("Discovery validation failure:");
            Throwable throwable = child.metadata().throwable().orElseThrow();
            assertThat(throwable).isInstanceOf(ResolverException.class);
            assertThat(throwable.getMessage()).contains("Invalid @Paramixel.Tag on");
            assertThat(throwable.getMessage()).contains("tag value must not be blank");
            assertThat(throwable.getMessage()).contains("ClasspathResolverInvalidBlankTagDiscoveryFixture#factory");
        });
    }

    @Test
    @DisplayName("aggregates multiple blank tag values")
    void aggregatesMultipleBlankTagValues() {
        Selector selector = Selector.classRegex(
                "ClasspathResolverValidTaggedDiscoveryFixture|ClasspathResolverInvalidBlankTagDiscoveryFixture"
                        + "|ClasspathResolverAnotherInvalidBlankTagDiscoveryFixture");

        var configuration = Configuration.defaultConfiguration();
        Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();

        assertThat(result).isPresent();
        Parallel<?> root = (Parallel<?>) result.orElseThrow();
        assertThat(root.children())
                .anySatisfy(action ->
                        assertThat(action.name()).contains("ClasspathResolverInvalidBlankTagDiscoveryFixture#factory"));
        assertThat(root.children())
                .anySatisfy(action -> assertThat(action.name())
                        .contains("ClasspathResolverAnotherInvalidBlankTagDiscoveryFixture#factory"));
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

    static class DisabledFactory {
        @Paramixel.Factory
        @Paramixel.Disabled
        public static Action<?> factory() {
            return Step.of("disabled-action", context -> {});
        }
    }

    static class DisabledWithReasonFactory {
        @Paramixel.Factory
        @Paramixel.Disabled("pending fix for BUG-123")
        public static Action<?> factory() {
            return Step.of("disabled-with-reason", context -> {});
        }
    }

    static class NonPublicFactory {
        @Paramixel.Factory
        static Action<?> factory() {
            return Step.of("non-public", obj -> {});
        }
    }

    static class NonStaticFactory {
        @Paramixel.Factory
        public Action<?> factory() {
            return Step.of("non-static", obj -> {});
        }
    }

    static class ParameterizedFactory {
        @Paramixel.Factory
        public static Action<?> factory(final String arg) {
            return Step.of("parameterized", obj -> {});
        }
    }

    static class WrongReturnTypeFactory {
        @Paramixel.Factory
        public static String factory() {
            return "not an action";
        }
    }

    static class NullReturningFactory {
        @Paramixel.Factory
        public static Action<?> factory() {
            return null;
        }
    }

    static class RuntimeExceptionThrowingFactory {
        @Paramixel.Factory
        public static Action<?> factory() {
            throw new RuntimeException("factory runtime error");
        }
    }

    static class SpecReturningFactory {
        @Paramixel.Factory
        public static Spec<?> factory() {
            return Lifecycle.of("spec-action").before("step", context -> {});
        }
    }

    static class TestError extends Error {
        TestError(final String message) {
            super(message);
        }
    }
}

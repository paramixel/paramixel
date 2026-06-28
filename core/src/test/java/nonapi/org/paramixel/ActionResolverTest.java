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

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ActionResolverMultiTagFixture;
import org.paramixel.api.ActionResolverSmokeFixture;
import org.paramixel.api.Configuration;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

@DisplayName("ActionResolver")
class ActionResolverTest {

    @Test
    @DisplayName("finds single factory")
    void findsSingleFactory() {
        var selector = Selector.classOf(ActionResolverSmokeFixture.class);
        var configuration = Configuration.defaultConfiguration();
        var result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isPresent();
        var root = (Parallel) result.orElseThrow();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).displayName()).isEqualTo("smoke-action");
    }

    @Test
    @DisplayName("applies selector tag filter with AND semantics")
    void appliesTagFiltersWithAndSemantics() {
        var selector =
                Selector.and(Selector.classOf(ActionResolverMultiTagFixture.class), Selector.tagRegex("smoke-fast"));

        var configuration = Configuration.defaultConfiguration();
        var result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isPresent();
        var root = (Parallel) result.orElseThrow();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).displayName()).isEqualTo("multi-tag-action");
    }

    @Test
    @DisplayName("returns empty when tag filter does not match")
    void returnsEmptyWhenTagFiltersDoNotBothMatch() {
        var selector =
                Selector.and(Selector.classOf(ActionResolverMultiTagFixture.class), Selector.tagRegex("missing"));

        var configuration = Configuration.defaultConfiguration();
        var result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("child override without factory shadows parent factory")
    void childOverrideWithoutFactoryShadowsParentFactory() {
        var selector = Selector.classOf(ChildOverridesWithoutFactory.class);

        var configuration = Configuration.defaultConfiguration();
        assertThat(new ActionResolver(configuration, selector).resolveRootAction())
                .isEmpty();
    }

    @Test
    @DisplayName("throws when more than one factory exists in a hierarchy")
    void throwsWhenMoreThanOneFactoryExistsInHierarchy() {
        var selector = Selector.classOf(ChildDeclaresOwnFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThatThrownBy(() -> new ActionResolver(configuration, selector).resolveRootAction())
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("more than one @Paramixel.Factory method");
    }

    @Test
    @DisplayName("error from factory method is re-thrown directly, not wrapped")
    void errorFromFactoryMethodIsReThrownDirectlyNotWrapped() {
        var selector = Selector.classOf(ErrorThrowingFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThatThrownBy(() -> new ActionResolver(configuration, selector).resolveRootAction())
                .isInstanceOf(TestError.class)
                .hasMessage("factory error");
    }

    @Test
    @DisplayName("skips @Paramixel.Disabled factory")
    void skipsDisabledFactory() {
        var selector = Selector.classOf(DisabledFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThat(new ActionResolver(configuration, selector).resolveRootAction())
                .isEmpty();
    }

    @Test
    @DisplayName("skips @Paramixel.Disabled with reason factory")
    void skipsDisabledWithReasonFactory() {
        var selector = Selector.classOf(DisabledWithReasonFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThat(new ActionResolver(configuration, selector).resolveRootAction())
                .isEmpty();
    }

    @Test
    @DisplayName("null-returning factory is skipped")
    void nullReturningFactoryIsSkipped() {
        var selector = Selector.classOf(NullReturningFactory.class);
        var configuration = Configuration.defaultConfiguration();
        var result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("resolves Action-returning factory")
    void resolvesBuilderReturningFactory() {
        var selector = Selector.classOf(BuilderReturningFactory.class);
        var configuration = Configuration.defaultConfiguration();
        var result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isPresent();
        var root = (Parallel) result.orElseThrow();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).displayName()).isEqualTo("scope");
    }

    @Test
    @DisplayName("resolves actions using selector")
    void resolvesActionsUsingSelector() {
        var selector =
                Selector.and(Selector.classRegex(".*ActionResolverSmokeFixture.*"), Selector.tagRegex("^smoke$"));

        var configuration = Configuration.defaultConfiguration();
        var result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).isInstanceOf(Parallel.class);
    }

    @Test
    @DisplayName("returns empty when selector tag filter excludes all matches")
    void returnsEmptyWhenSelectorTagFilterExcludesAllMatches() {
        var selector =
                Selector.and(Selector.classRegex(".*ActionResolverSmokeFixture.*"), Selector.tagRegex("nomatch"));

        var configuration = Configuration.defaultConfiguration();
        assertThat(new ActionResolver(configuration, selector).resolveRootAction())
                .isEmpty();
    }

    @Test
    @DisplayName("continues after blank tag value during classpath discovery")
    void continuesAfterBlankTagValueDuringClasspathDiscovery() {
        var selector = Selector.classRegex(
                ".*ActionResolverValidTaggedDiscoveryFixture.*|.*ActionResolverInvalidBlankTagDiscoveryFixture.*");

        var configuration = Configuration.defaultConfiguration();
        var result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isPresent();
        var root = (Parallel) result.orElseThrow();
        assertThat(root.children()).hasSize(2);
        assertThat(root.children())
                .anySatisfy(action -> assertThat(action.displayName()).isEqualTo("valid-tagged-discovery"));
        assertThat(root.children())
                .anySatisfy(action -> assertThat(action.displayName()).startsWith("Discovery validation failure:"));
    }

    @Test
    @DisplayName("discovery validation failure action fails when run")
    void discoveryValidationFailureActionFailsWhenRun() {
        var selector = Selector.classRegex(
                ".*ActionResolverValidTaggedDiscoveryFixture.*|.*ActionResolverInvalidBlankTagDiscoveryFixture.*");

        var configuration = Configuration.defaultConfiguration();
        var result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isPresent();
        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "1")))
                .build();
        var rootResult = runner.run(result.orElseThrow()).descriptor().orElseThrow();
        assertThat(rootResult.isFailed()).isTrue();
        assertThat(rootResult.children()).anySatisfy(child -> {
            assertThat(child.isPassed()).isTrue();
        });
        assertThat(rootResult.children()).anySatisfy(child -> {
            assertThat(child.isFailed()).isTrue();
            assertThat(child.action().displayName()).startsWith("Discovery validation failure:");
            Throwable throwable = child.throwable().orElseThrow();
            assertThat(throwable).isInstanceOf(ResolverException.class);
            assertThat(throwable.getMessage()).contains("Invalid @Paramixel.Tag on");
            assertThat(throwable.getMessage()).contains("tag value is blank");
            assertThat(throwable.getMessage()).contains("ActionResolverInvalidBlankTagDiscoveryFixture#factory");
        });
    }

    @Test
    @DisplayName("aggregates multiple blank tag values")
    void aggregatesMultipleBlankTagValues() {
        var selector = Selector.classRegex(
                ".*ActionResolverValidTaggedDiscoveryFixture.*|.*ActionResolverInvalidBlankTagDiscoveryFixture.*"
                        + "|.*ActionResolverAnotherInvalidBlankTagDiscoveryFixture.*");

        var configuration = Configuration.defaultConfiguration();
        var result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isPresent();
        var root = (Parallel) result.orElseThrow();
        assertThat(root.children())
                .anySatisfy(action -> assertThat(action.displayName())
                        .contains("ActionResolverInvalidBlankTagDiscoveryFixture#factory"));
        assertThat(root.children())
                .anySatisfy(action -> assertThat(action.displayName())
                        .contains("ActionResolverAnotherInvalidBlankTagDiscoveryFixture#factory"));
    }

    static class ParentFactory {
        @Paramixel.Factory
        public static Action factory() {
            return Step.of("parent-action", context -> {});
        }
    }

    static class ChildOverridesWithoutFactory extends ParentFactory {
        public static Action factory() {
            return Step.of("child-action", context -> {});
        }
    }

    static class ParentFactoryTwo {
        @Paramixel.Factory
        public static Action parentAction() {
            return Step.of("parent-action", context -> {});
        }
    }

    static class ChildDeclaresOwnFactory extends ParentFactoryTwo {
        @Paramixel.Factory
        public static Action childAction() {
            return Step.of("child-action", context -> {});
        }
    }

    static class BlankTagFactory {
        @Paramixel.Factory
        @Paramixel.Tag(" ")
        public static Action factory() {
            return Step.of("blank-tag-action", context -> {});
        }
    }

    static class ErrorThrowingFactory {
        @Paramixel.Factory
        public static Action factory() {
            throw new TestError("factory error");
        }
    }

    static class DisabledFactory {
        @Paramixel.Factory
        @Paramixel.Disabled
        public static Action factory() {
            return Step.of("disabled-action", context -> {});
        }
    }

    static class DisabledWithReasonFactory {
        @Paramixel.Factory
        @Paramixel.Disabled("pending fix for BUG-123")
        public static Action factory() {
            return Step.of("disabled-with-reason", context -> {});
        }
    }

    static class NonPublicFactory {
        @Paramixel.Factory
        static Action factory() {
            return Step.of("non-public", context -> {});
        }
    }

    static class NonStaticFactory {
        @Paramixel.Factory
        public Action factory() {
            return Step.of("non-static", context -> {});
        }
    }

    static class ParameterizedFactory {
        @Paramixel.Factory
        public static Action factory(final String arg) {
            return Step.of("parameterized", context -> {});
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
        public static Action factory() {
            return null;
        }
    }

    static class RuntimeExceptionThrowingFactory {
        @Paramixel.Factory
        public static Action factory() {
            throw new RuntimeException("factory runtime error");
        }
    }

    static class BuilderReturningFactory {
        @Paramixel.Factory
        public static Action factory() {
            return Scope.builder("scope").body(Step.of("step", context -> {})).build();
        }
    }

    static class TestError extends Error {
        TestError(final String message) {
            super(message);
        }
    }
}

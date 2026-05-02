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

package org.paramixel.core.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Configuration;
import org.paramixel.core.ConfigurationException;
import org.paramixel.core.Paramixel;
import org.paramixel.core.ResolverException;
import org.paramixel.core.action.Direct;

@DisplayName("Resolver")
class ResolverTest {

    @Nested
    @DisplayName("resolveParallelism")
    class ResolveParallelism {

        @Test
        @DisplayName("returns value from provided configuration map")
        void returnsValueFromProvidedConfigurationMap() {
            Map<String, String> configuration = Map.of(Configuration.RUNNER_PARALLELISM, "4");
            int parallelism = Resolver.resolveParallelism(configuration);
            assertThat(parallelism).isEqualTo(4);
        }

        @Test
        @DisplayName("falls back to default properties when key is absent from provided map")
        void fallsBackToDefaultPropertiesWhenKeyIsAbsent() {
            Map<String, String> configuration = Map.of();
            int parallelism = Resolver.resolveParallelism(configuration);
            int expected = Integer.parseInt(Configuration.defaultProperties().get(Configuration.RUNNER_PARALLELISM));
            assertThat(parallelism).isEqualTo(expected);
        }

        @Test
        @DisplayName("falls back to default properties when configuration is null")
        void fallsBackToDefaultPropertiesWhenConfigurationIsNull() {
            int parallelism = Resolver.resolveParallelism(null);
            int expected = Integer.parseInt(Configuration.defaultProperties().get(Configuration.RUNNER_PARALLELISM));
            assertThat(parallelism).isEqualTo(expected);
        }

        @Test
        @DisplayName("provided configuration map takes precedence over defaults")
        void providedConfigurationTakesPrecedenceOverDefaults() {
            Map<String, String> configuration = Map.of(Configuration.RUNNER_PARALLELISM, "2");
            int parallelism = Resolver.resolveParallelism(configuration);
            assertThat(parallelism).isEqualTo(2);
        }

        @Test
        @DisplayName("throws ConfigurationException for non-integer value")
        void throwsConfigurationExceptionForNonIntegerValue() {
            Map<String, String> configuration = Map.of(Configuration.RUNNER_PARALLELISM, "not-a-number");
            assertThatThrownBy(() -> Resolver.resolveParallelism(configuration))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("expected integer")
                    .hasMessageContaining("not-a-number");
        }

        @Test
        @DisplayName("throws ConfigurationException for zero")
        void throwsConfigurationExceptionForZero() {
            Map<String, String> configuration = Map.of(Configuration.RUNNER_PARALLELISM, "0");
            assertThatThrownBy(() -> Resolver.resolveParallelism(configuration))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("expected positive integer")
                    .hasMessageContaining("0");
        }

        @Test
        @DisplayName("throws ConfigurationException for negative value")
        void throwsConfigurationExceptionForNegativeValue() {
            Map<String, String> configuration = Map.of(Configuration.RUNNER_PARALLELISM, "-1");
            assertThatThrownBy(() -> Resolver.resolveParallelism(configuration))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("expected positive integer")
                    .hasMessageContaining("-1");
        }

        @Test
        @DisplayName("accepts positive integer values")
        void acceptsPositiveIntegerValues() {
            Map<String, String> configuration = Map.of(Configuration.RUNNER_PARALLELISM, "1");
            assertThat(Resolver.resolveParallelism(configuration)).isEqualTo(1);

            Map<String, String> largeConfiguration = Map.of(Configuration.RUNNER_PARALLELISM, "256");
            assertThat(Resolver.resolveParallelism(largeConfiguration)).isEqualTo(256);
        }

        @Test
        @DisplayName("does not mutate provided configuration map")
        void doesNotMutateProvidedConfigurationMap() {
            Map<String, String> configuration = Map.of(Configuration.RUNNER_PARALLELISM, "4");
            int parallelism = Resolver.resolveParallelism(configuration);
            assertThat(parallelism).isEqualTo(4);
            assertThat(configuration).containsExactly(Map.entry(Configuration.RUNNER_PARALLELISM, "4"));
        }
    }

    @Nested
    @DisplayName("resolveActionsFromClass")
    class ResolveActionsFromClass {

        @Test
        @DisplayName("single action factory on a class is returned directly")
        void singleActionFactoryReturnedDirectly() {
            var result = Resolver.resolveActionsFromClass(SingleActionClass.class);
            assertThat(result).isPresent();
            var action = result.get();
            assertThat(action).isInstanceOf(Direct.class);
            assertThat(action.getName()).isEqualTo("single-action");
        }

        @Test
        @DisplayName("returns empty optional for class with no action factories")
        void returnsEmptyForClassWithNoActionFactories() {
            var result = Resolver.resolveActionsFromClass(NoActionsClass.class);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips disabled action factory methods")
        void skipsDisabledActionFactoryMethods() {
            var result = Resolver.resolveActionsFromClass(DisabledActionClass.class);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("throws for non-public factory method")
        void throwsForNonPublicFactoryMethod() {
            assertThatThrownBy(() -> Resolver.resolveActionsFromClass(NonPublicActionClass.class))
                    .isInstanceOf(ResolverException.class)
                    .hasMessageContaining("method must be public static");
        }

        @Test
        @DisplayName("throws for non-static factory method")
        void throwsForNonStaticFactoryMethod() {
            assertThatThrownBy(() -> Resolver.resolveActionsFromClass(NonStaticActionClass.class))
                    .isInstanceOf(ResolverException.class)
                    .hasMessageContaining("method must be public static");
        }

        @Test
        @DisplayName("throws for factory method with parameters")
        void throwsForFactoryMethodWithParameters() {
            assertThatThrownBy(() -> Resolver.resolveActionsFromClass(ParamsActionClass.class))
                    .isInstanceOf(ResolverException.class)
                    .hasMessageContaining("method must have no parameters");
        }

        @Test
        @DisplayName("throws for factory method returning null")
        void throwsForFactoryMethodReturningNull() {
            assertThatThrownBy(() -> Resolver.resolveActionsFromClass(NullReturnActionClass.class))
                    .isInstanceOf(ResolverException.class)
                    .hasMessageContaining("method returned null");
        }

        @Test
        @DisplayName("throws for factory method with non-Action return type")
        void throwsForFactoryMethodWithNonActionReturnType() {
            assertThatThrownBy(() -> Resolver.resolveActionsFromClass(WrongReturnActionClass.class))
                    .isInstanceOf(ResolverException.class)
                    .hasMessageContaining("return type must be Action");
        }

        @Test
        @DisplayName("throws ResolverException for class with two declared @ActionFactory methods")
        void throwsForTwoDeclaredActionFactories() {
            assertThatThrownBy(() -> Resolver.resolveActionsFromClass(TwoFactoriesClass.class))
                    .isInstanceOf(ResolverException.class)
                    .hasMessageContaining("more than one")
                    .hasMessageContaining("TwoFactoriesClass");
        }

        @Test
        @DisplayName("finds single @ActionFactory inherited from parent class")
        void findsInheritedFactoryMethod() {
            var result = Resolver.resolveActionsFromClass(ChildInheritsFactory.class);
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("parent-action");
        }

        @Test
        @DisplayName("child override with @ActionFactory shadows parent")
        void childOverrideWithAnnotationShadowsParent() {
            var result = Resolver.resolveActionsFromClass(ChildOverridesWithFactory.class);
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("child-action");
        }

        @Test
        @DisplayName("child override without @ActionFactory shadows parent factory")
        void childOverrideWithoutAnnotationShadowsParent() {
            var result = Resolver.resolveActionsFromClass(ChildOverridesWithoutAnnotation.class);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("child declares own @ActionFactory plus inherits one: throws ResolverException")
        void childDeclaresOwnPlusInherits() {
            assertThatThrownBy(() -> Resolver.resolveActionsFromClass(ChildDeclaresPlusInherits.class))
                    .isInstanceOf(ResolverException.class)
                    .hasMessageContaining("more than one")
                    .hasMessageContaining("ChildDeclaresPlusInherits");
        }

        @Test
        @DisplayName("child disables overridden factory: no eligible factory methods")
        void childDisablesOverride() {
            var result = Resolver.resolveActionsFromClass(ChildDisablesOverride.class);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("finds @ActionFactory deep in hierarchy (grandparent)")
        void deepInheritance() {
            var result = Resolver.resolveActionsFromClass(ChildDeepInherits.class);
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("grandparent-action");
        }
    }

    @Nested
    @DisplayName("resolveActions with configuration")
    class ResolveActionsWithConfiguration {

        @Test
        @DisplayName("resolveActions(ClassLoader, Map) uses parallelism from configuration")
        void resolveActionsWithClassLoaderAndConfiguration() {
            Map<String, String> configuration = Map.of(Configuration.RUNNER_PARALLELISM, "4");
            var result = Resolver.resolveActions(Thread.currentThread().getContextClassLoader(), configuration);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("resolveActions(Map) uses parallelism from configuration")
        void resolveActionsWithConfiguration() {
            Map<String, String> configuration = Map.of(Configuration.RUNNER_PARALLELISM, "4");
            var result = Resolver.resolveActions(configuration);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("resolveActions throws ConfigurationException for invalid parallelism")
        void resolveActionsThrowsConfigurationExceptionForInvalidParallelism() {
            Map<String, String> configuration = Map.of(Configuration.RUNNER_PARALLELISM, "not-a-number");
            assertThatThrownBy(() -> Resolver.resolveActions(configuration)).isInstanceOf(ConfigurationException.class);
        }
    }

    static class TwoFactoriesClass {
        @Paramixel.ActionFactory
        public static org.paramixel.core.Action firstFactory() {
            return Direct.of("first", context -> {});
        }

        @Paramixel.ActionFactory
        public static org.paramixel.core.Action secondFactory() {
            return Direct.of("second", context -> {});
        }
    }

    static class SingleActionClass {
        @Paramixel.ActionFactory
        public static org.paramixel.core.Action singleAction() {
            return Direct.of("single-action", context -> {});
        }
    }

    static class NoActionsClass {}

    static class DisabledActionClass {
        @Paramixel.ActionFactory
        @Paramixel.Disabled("Not ready")
        public static org.paramixel.core.Action disabledAction() {
            return Direct.of("disabled-action", context -> {});
        }
    }

    static class NonPublicActionClass {
        @Paramixel.ActionFactory
        static org.paramixel.core.Action privateAction() {
            return Direct.of("private-action", context -> {});
        }
    }

    static class NonStaticActionClass {
        @Paramixel.ActionFactory
        public org.paramixel.core.Action instanceAction() {
            return Direct.of("instance-action", context -> {});
        }
    }

    static class ParamsActionClass {
        @Paramixel.ActionFactory
        public static org.paramixel.core.Action actionWithParams(String param) {
            return Direct.of("params-action", context -> {});
        }
    }

    static class NullReturnActionClass {
        @Paramixel.ActionFactory
        public static org.paramixel.core.Action nullAction() {
            return null;
        }
    }

    static class WrongReturnActionClass {
        @Paramixel.ActionFactory
        public static String wrongReturn() {
            return "not an action";
        }
    }

    static class ParentWithFactory {
        @Paramixel.ActionFactory
        public static org.paramixel.core.Action parentFactory() {
            return Direct.of("parent-action", context -> {});
        }
    }

    static class ChildInheritsFactory extends ParentWithFactory {}

    static class ChildOverridesWithFactory extends ParentWithFactory {
        @Paramixel.ActionFactory
        public static org.paramixel.core.Action parentFactory() {
            return Direct.of("child-action", context -> {});
        }
    }

    static class ChildOverridesWithoutAnnotation extends ParentWithFactory {
        public static org.paramixel.core.Action parentFactory() {
            return Direct.of("child-no-annotation", context -> {});
        }
    }

    static class ChildDeclaresPlusInherits extends ParentWithFactory {
        @Paramixel.ActionFactory
        public static org.paramixel.core.Action childFactory() {
            return Direct.of("child-additional-action", context -> {});
        }
    }

    static class ChildDisablesOverride extends ParentWithFactory {
        @Paramixel.ActionFactory
        @Paramixel.Disabled("disabled")
        public static org.paramixel.core.Action parentFactory() {
            return Direct.of("child-disabled", context -> {});
        }
    }

    static class GrandparentFactory {
        @Paramixel.ActionFactory
        public static org.paramixel.core.Action grandparentAction() {
            return Direct.of("grandparent-action", context -> {});
        }
    }

    static class ParentMiddle extends GrandparentFactory {}

    static class ChildDeepInherits extends ParentMiddle {}

    static class FactoryThrowsExceptionClass {
        @Paramixel.ActionFactory
        public static org.paramixel.core.Action throwingFactory() {
            throw new RuntimeException("factory blew up");
        }
    }

    @Nested
    @DisplayName("resolveActions with Selector")
    class ResolveActionsWithSelector {

        @Test
        @DisplayName("resolves with Selector from package name")
        void resolvesWithSelectorFromPackageName() {
            Selector selector = Selector.byPackageName("nonexistent.package");
            var result = Resolver.resolveActions(selector);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("resolves with Selector and Composition")
        void resolvesWithSelectorAndComposition() {
            Selector selector = Selector.byPackageName("nonexistent.package");
            var resultSequential = Resolver.resolveActions(selector, Resolver.Composition.SEQUENTIAL);
            assertThat(resultSequential).isEmpty();
            var resultParallel = Resolver.resolveActions(selector, Resolver.Composition.PARALLEL);
            assertThat(resultParallel).isEmpty();
        }

        @Test
        @DisplayName("resolves with Selector and configuration")
        void resolvesWithSelectorAndConfiguration() {
            Selector selector = Selector.byPackageName("nonexistent.package");
            Map<String, String> config = Map.of(Configuration.RUNNER_PARALLELISM, "4");
            var result = Resolver.resolveActions(selector, config);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("resolves with Selector, Composition, and configuration")
        void resolvesWithSelectorCompositionAndConfiguration() {
            Selector selector = Selector.byPackageName("nonexistent.package");
            Map<String, String> config = Map.of(Configuration.RUNNER_PARALLELISM, "4");
            var result = Resolver.resolveActions(selector, Resolver.Composition.SEQUENTIAL, config);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolveActions with package regex")
    class ResolveActionsWithPackageRegex {

        @Test
        @DisplayName("empty result for nonexistent package regex")
        void emptyResultForNonexistentPackage() {
            var result = Resolver.resolveActions("nonexistent.package.that.does.not.exist");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("valid regex with no matches returns empty")
        void validRegexWithNoMatchesReturnsEmpty() {
            var result = Resolver.resolveActions("nonexistent\\.package\\.that\\.does\\.not\\.exist");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("composition sequential with package regex")
        void compositionSequentialWithPackageRegex() {
            var result = Resolver.resolveActions("nonexistent.package", Resolver.Composition.SEQUENTIAL);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolveActions with predicate")
    class ResolveActionsWithPredicate {

        @Test
        @DisplayName("returns empty when predicate matches nothing")
        void returnsEmptyWhenPredicateMatchesNothing() {
            var result = Resolver.resolveActions(pkg -> false);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty with predicate and composition")
        void returnsEmptyWithPredicateAndComposition() {
            var result = Resolver.resolveActions(pkg -> false, Resolver.Composition.SEQUENTIAL);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty with predicate, composition, and configuration")
        void returnsEmptyWithPredicateCompositionAndConfiguration() {
            Map<String, String> config = Map.of(Configuration.RUNNER_PARALLELISM, "2");
            var result = Resolver.resolveActions(pkg -> false, Resolver.Composition.SEQUENTIAL, config);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolveActions with ClassLoader")
    class ResolveActionsWithClassLoader {

        @Test
        @DisplayName("empty result for custom classloader with no matching actions")
        void emptyResultForCustomClassLoader() {
            var result = Resolver.resolveActions(Thread.currentThread().getContextClassLoader());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("empty result with classloader, predicate, and composition")
        void emptyResultWithClassLoaderPredicateAndComposition() {
            var result = Resolver.resolveActions(
                    Thread.currentThread().getContextClassLoader(), pkg -> false, Resolver.Composition.SEQUENTIAL);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolveActionsFromClass edge cases")
    class ResolveActionsFromClassEdgeCases {

        @Test
        @DisplayName("factory method that throws an exception during invocation")
        void factoryMethodThrowsException() {
            assertThatThrownBy(() -> Resolver.resolveActionsFromClass(FactoryThrowsExceptionClass.class))
                    .isInstanceOf(ResolverException.class)
                    .hasMessageContaining("Failed to invoke")
                    .hasMessageContaining("throwingFactory");
        }

        @Test
        @DisplayName("returns empty for abstract class with no action factories")
        void returnsEmptyForAbstractClassWithNoActionFactories() {
            var result = Resolver.resolveActionsFromClass(AbstractClassNoFactory.class);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolveParallelism edge cases")
    class ResolveParallelismEdgeCases {

        @Test
        @DisplayName("accepts Integer.MAX_VALUE as valid parallelism")
        void acceptsMaxInteger() {
            Map<String, String> configuration =
                    Map.of(Configuration.RUNNER_PARALLELISM, String.valueOf(Integer.MAX_VALUE));
            int parallelism = Resolver.resolveParallelism(configuration);
            assertThat(parallelism).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("empty configuration map falls back to defaults")
        void emptyConfigurationMapFallsBackToDefaults() {
            Map<String, String> configuration = Map.of();
            int parallelism = Resolver.resolveParallelism(configuration);
            int expected = Integer.parseInt(Configuration.defaultProperties().get(Configuration.RUNNER_PARALLELISM));
            assertThat(parallelism).isEqualTo(expected);
        }
    }

    abstract static class AbstractClassNoFactory {}
}

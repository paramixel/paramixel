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

package org.paramixel.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Direct;
import org.paramixel.core.exception.ConfigurationException;
import org.paramixel.core.exception.ResolverException;

@DisplayName("Resolver")
class ResolverTest {

    @Nested
    @DisplayName("resolveParallelism")
    class ResolveParallelism {

        @Test
        @DisplayName("returns value from provided configuration map")
        void returnsValueFromProvidedConfigurationMap() {
            assertThat(Resolver.resolveParallelism(Map.of(Configuration.RUNNER_PARALLELISM, "4")))
                    .isEqualTo(4);
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid value")
        void throwsConfigurationExceptionForInvalidValue() {
            assertThatThrownBy(() -> Resolver.resolveParallelism(Map.of(Configuration.RUNNER_PARALLELISM, "abc")))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("expected integer");
        }
    }

    @Nested
    @DisplayName("selector builder")
    class SelectorBuilder {

        @Test
        @DisplayName("rejects blank regex")
        void rejectsBlankRegex() {
            assertThatThrownBy(() -> Selector.builder().tagMatch(" "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tagMatch regex must not be blank");
        }

        @Test
        @DisplayName("rejects invalid regex")
        void rejectsInvalidRegex() {
            assertThatThrownBy(() -> Selector.builder().packageMatch("["))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid regex");
        }

        @Test
        @DisplayName("rejects both package and class match")
        void rejectsBothLocationModes() {
            assertThatThrownBy(() -> Selector.builder()
                            .packageMatch("example")
                            .classMatch("Example")
                            .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("only one location match");
        }

        @Test
        @DisplayName("packageOf matches package and subpackages")
        void packageOfMatchesPackageAndSubpackages() {
            Selector selector = Selector.builder().packageOf(ResolverTest.class).build();

            assertThat(selector.matchesLocation(ResolverTest.class)).isTrue();
            assertThat(selector.matchesLocation(org.paramixel.core.action.Noop.class))
                    .isTrue();
        }

        @Test
        @DisplayName("classOf matches exact class")
        void classOfMatchesExactClass() {
            Selector selector =
                    Selector.builder().classOf(ResolverSmokeFixture.class).build();

            assertThat(selector.matchesLocation(ResolverSmokeFixture.class)).isTrue();
            assertThat(selector.matchesLocation(ResolverMultiTagFixture.class)).isFalse();
        }

        @Test
        @DisplayName("packageOf rejects null")
        void packageOfRejectsNull() {
            assertThatThrownBy(() -> Selector.builder().packageOf(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("clazz must not be null");
        }

        @Test
        @DisplayName("classOf rejects null")
        void classOfRejectsNull() {
            assertThatThrownBy(() -> Selector.builder().classOf(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("clazz must not be null");
        }
    }

    @Nested
    @DisplayName("resolveActionFromClass")
    class ResolveActionFromClass {

        @Test
        @DisplayName("finds single action factory")
        void findsSingleActionFactory() {
            Optional<Action> result = Resolver.resolveActionFromClass(ResolverSmokeFixture.class);
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getName()).isEqualTo("smoke-action");
        }

        @Test
        @DisplayName("applies selector and configuration tag filters with AND semantics")
        void appliesTagFiltersWithAndSemantics() {
            Optional<Action> result = Resolver.resolveActionFromClass(
                    ResolverMultiTagFixture.class, Pattern.compile("smoke"), Pattern.compile("critical"));

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getName()).isEqualTo("multi-tag-action");
        }

        @Test
        @DisplayName("returns empty when tag filters do not both match")
        void returnsEmptyWhenTagFiltersDoNotBothMatch() {
            Optional<Action> result = Resolver.resolveActionFromClass(
                    ResolverMultiTagFixture.class, Pattern.compile("smoke"), Pattern.compile("missing"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("rejects blank tag values")
        void rejectsBlankTagValues() {
            assertThatThrownBy(() -> Resolver.resolveActionFromClass(BlankTagActionFactory.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tag value must not be blank");
        }

        @Test
        @DisplayName("child override without action factory shadows parent factory")
        void childOverrideWithoutActionFactoryShadowsParentFactory() {
            assertThat(Resolver.resolveActionFromClass(ChildOverridesWithoutFactory.class))
                    .isEmpty();
        }

        @Test
        @DisplayName("throws when more than one factory exists in a hierarchy")
        void throwsWhenMoreThanOneFactoryExistsInHierarchy() {
            assertThatThrownBy(() -> Resolver.resolveActionFromClass(ChildDeclaresOwnFactory.class))
                    .isInstanceOf(ResolverException.class)
                    .hasMessageContaining("more than one @Paramixel.ActionFactory method");
        }
    }

    @Nested
    @DisplayName("public resolveActions API")
    class PublicResolveActionsApi {

        @Test
        @DisplayName("resolveActions(configuration) honors name and tag configuration")
        void resolveActionsConfigurationHonorsNameAndTagConfiguration() {
            Map<String, String> configuration = Map.of(
                    Configuration.CLASS_MATCH, "ResolverSmokeFixture",
                    Configuration.TAG_MATCH, "^smoke$");

            Optional<Action> result = Resolver.resolveActions(configuration);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getName()).isEqualTo("smoke-action");
        }

        @Test
        @DisplayName("resolveActions(configuration) honors package and class configuration together")
        void resolveActionsConfigurationHonorsPackageAndClassConfigurationTogether() {
            Map<String, String> configuration = Map.of(
                    Configuration.PACKAGE_MATCH, "^org\\.paramixel\\.core$",
                    Configuration.CLASS_MATCH, "ResolverSmokeFixture");

            Optional<Action> result = Resolver.resolveActions(configuration);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getName()).isEqualTo("smoke-action");
        }

        @Test
        @DisplayName("resolveActions(configuration, selector) applies selector and configuration filters")
        void resolveActionsConfigurationAndSelectorApplyTogether() {
            Map<String, String> configuration = Map.of(
                    Configuration.CLASS_MATCH, "ResolverMultiTagFixture",
                    Configuration.TAG_MATCH, "critical");
            Selector selector = Selector.builder()
                    .classMatch("ResolverMultiTagFixture")
                    .tagMatch("smoke")
                    .build();

            Optional<Action> result = Resolver.resolveActions(configuration, selector);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getName()).isEqualTo("multi-tag-action");
        }

        @Test
        @DisplayName("returns empty when configuration tag filter excludes all matches")
        void returnsEmptyWhenConfigurationTagFilterExcludesAllMatches() {
            Map<String, String> configuration = Map.of(
                    Configuration.CLASS_MATCH, "ResolverSmokeFixture",
                    Configuration.TAG_MATCH, "nomatch");

            assertThat(Resolver.resolveActions(configuration)).isEmpty();
        }

        @Test
        @DisplayName("supports selector-only class match")
        void supportsSelectorOnlyClassMatch() {
            Selector selector = Selector.builder()
                    .classOf(ResolverSmokeFixture.class)
                    .tagMatch("^smoke$")
                    .build();

            Optional<Action> result = Resolver.resolveActions(selector);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getName()).isEqualTo("smoke-action");
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid configured tag regex")
        void throwsConfigurationExceptionForInvalidConfiguredTagRegex() {
            assertThatThrownBy(() -> Resolver.resolveActions(Map.of(Configuration.TAG_MATCH, "[")))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(Configuration.TAG_MATCH);
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid configured class regex")
        void throwsConfigurationExceptionForInvalidConfiguredClassRegex() {
            assertThatThrownBy(() -> Resolver.resolveActions(Map.of(Configuration.CLASS_MATCH, "[")))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(Configuration.CLASS_MATCH);
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid configured package regex")
        void throwsConfigurationExceptionForInvalidConfiguredPackageRegex() {
            assertThatThrownBy(() -> Resolver.resolveActions(Map.of(Configuration.PACKAGE_MATCH, "[")))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(Configuration.PACKAGE_MATCH);
        }
    }

    static class ParentFactory {
        @Paramixel.ActionFactory
        public static Action actionFactory() {
            return Direct.of("parent-action", context -> {});
        }
    }

    static class ChildOverridesWithoutFactory extends ParentFactory {
        public static Action actionFactory() {
            return Direct.of("child-action", context -> {});
        }
    }

    static class ParentFactoryTwo {
        @Paramixel.ActionFactory
        public static Action parentAction() {
            return Direct.of("parent-action", context -> {});
        }
    }

    static class ChildDeclaresOwnFactory extends ParentFactoryTwo {
        @Paramixel.ActionFactory
        public static Action childAction() {
            return Direct.of("child-action", context -> {});
        }
    }

    static class BlankTagActionFactory {
        @Paramixel.ActionFactory
        @Paramixel.Tag(" ")
        public static Action actionFactory() {
            return Direct.of("blank-tag-action", context -> {});
        }
    }
}

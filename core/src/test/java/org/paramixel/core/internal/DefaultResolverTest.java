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

package org.paramixel.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Configuration;
import org.paramixel.core.Paramixel;
import org.paramixel.core.ResolverMultiTagFixture;
import org.paramixel.core.ResolverSmokeFixture;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.exception.ConfigurationException;
import org.paramixel.core.exception.ResolverException;

@DisplayName("DefaultResolver")
class DefaultResolverTest {

    @Nested
    @DisplayName("resolveActionFromClass")
    class ResolveActionFromClass {

        @Test
        @DisplayName("finds single action factory")
        void findsSingleActionFactory() {
            DefaultResolver resolver = new DefaultResolver(new DefaultConfiguration(null));

            Optional<Action> result = resolver.resolveActionFromClass(ResolverSmokeFixture.class);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getName()).isEqualTo("smoke-action");
        }

        @Test
        @DisplayName("applies selector and configuration tag filters with AND semantics")
        void appliesTagFiltersWithAndSemantics() {
            DefaultResolver resolver = new DefaultResolver(new DefaultConfiguration(null));

            Optional<Action> result = resolver.resolveActionFromClass(
                    ResolverMultiTagFixture.class, Pattern.compile("smoke"), Pattern.compile("critical"));

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getName()).isEqualTo("multi-tag-action");
        }

        @Test
        @DisplayName("returns empty when tag filters do not both match")
        void returnsEmptyWhenTagFiltersDoNotBothMatch() {
            DefaultResolver resolver = new DefaultResolver(new DefaultConfiguration(null));

            Optional<Action> result = resolver.resolveActionFromClass(
                    ResolverMultiTagFixture.class, Pattern.compile("smoke"), Pattern.compile("missing"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("rejects blank tag values")
        void rejectsBlankTagValues() {
            DefaultResolver resolver = new DefaultResolver(new DefaultConfiguration(null));

            assertThatThrownBy(() -> resolver.resolveActionFromClass(BlankTagActionFactory.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tag value must not be blank");
        }

        @Test
        @DisplayName("child override without action factory shadows parent factory")
        void childOverrideWithoutActionFactoryShadowsParentFactory() {
            DefaultResolver resolver = new DefaultResolver(new DefaultConfiguration(null));

            assertThat(resolver.resolveActionFromClass(ChildOverridesWithoutFactory.class))
                    .isEmpty();
        }

        @Test
        @DisplayName("throws when more than one factory exists in a hierarchy")
        void throwsWhenMoreThanOneFactoryExistsInHierarchy() {
            DefaultResolver resolver = new DefaultResolver(new DefaultConfiguration(null));

            assertThatThrownBy(() -> resolver.resolveActionFromClass(ChildDeclaresOwnFactory.class))
                    .isInstanceOf(ResolverException.class)
                    .hasMessageContaining("more than one @Paramixel.ActionFactory method");
        }

        @Test
        @DisplayName("error from factory method is re-thrown directly, not wrapped")
        void errorFromFactoryMethodIsReThrownDirectlyNotWrapped() {
            DefaultResolver resolver = new DefaultResolver(new DefaultConfiguration(null));

            assertThatThrownBy(() -> resolver.resolveActionFromClass(ErrorThrowingFactory.class))
                    .isInstanceOf(TestError.class)
                    .hasMessage("factory error");
        }

        @Test
        @DisplayName("rejects null configuration")
        void rejectsNullConfiguration() {
            assertThatThrownBy(() -> new DefaultResolver(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("configuration must not be null");
        }
    }

    @Nested
    @DisplayName("resolveActions")
    class ResolveActions {

        @Test
        @DisplayName("resolves actions using configuration")
        void resolvesActionsUsingConfiguration() {
            DefaultConfiguration configuration = new DefaultConfiguration(Map.of(
                    Configuration.CLASS_MATCH, "ResolverSmokeFixture",
                    Configuration.TAG_MATCH, "^smoke$"));
            DefaultResolver resolver = new DefaultResolver(configuration);

            Optional<Action> result = resolver.resolveActions(null);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow()).isInstanceOf(Parallel.class);
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid tag regex")
        void throwsConfigurationExceptionForInvalidTagRegex() {
            DefaultConfiguration configuration = new DefaultConfiguration(Map.of(Configuration.TAG_MATCH, "["));
            DefaultResolver resolver = new DefaultResolver(configuration);

            assertThatThrownBy(() -> resolver.resolveActions(null))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(Configuration.TAG_MATCH);
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid class regex")
        void throwsConfigurationExceptionForInvalidClassRegex() {
            DefaultConfiguration configuration = new DefaultConfiguration(Map.of(Configuration.CLASS_MATCH, "["));
            DefaultResolver resolver = new DefaultResolver(configuration);

            assertThatThrownBy(() -> resolver.resolveActions(null))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(Configuration.CLASS_MATCH);
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid package regex")
        void throwsConfigurationExceptionForInvalidPackageRegex() {
            DefaultConfiguration configuration = new DefaultConfiguration(Map.of(Configuration.PACKAGE_MATCH, "["));
            DefaultResolver resolver = new DefaultResolver(configuration);

            assertThatThrownBy(() -> resolver.resolveActions(null))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(Configuration.PACKAGE_MATCH);
        }

        @Test
        @DisplayName("returns empty when configuration tag filter excludes all matches")
        void returnsEmptyWhenConfigurationTagFilterExcludesAllMatches() {
            DefaultConfiguration configuration = new DefaultConfiguration(Map.of(
                    Configuration.CLASS_MATCH, "ResolverSmokeFixture",
                    Configuration.TAG_MATCH, "nomatch"));
            DefaultResolver resolver = new DefaultResolver(configuration);

            assertThat(resolver.resolveActions(null)).isEmpty();
        }
    }

    static class ParentFactory {
        @Paramixel.ActionFactory
        public static Action actionFactory() {
            return Direct.builder("parent-action").execute(context -> {}).build();
        }
    }

    static class ChildOverridesWithoutFactory extends ParentFactory {
        public static Action actionFactory() {
            return Direct.builder("child-action").execute(context -> {}).build();
        }
    }

    static class ParentFactoryTwo {
        @Paramixel.ActionFactory
        public static Action parentAction() {
            return Direct.builder("parent-action").execute(context -> {}).build();
        }
    }

    static class ChildDeclaresOwnFactory extends ParentFactoryTwo {
        @Paramixel.ActionFactory
        public static Action childAction() {
            return Direct.builder("child-action").execute(context -> {}).build();
        }
    }

    static class BlankTagActionFactory {
        @Paramixel.ActionFactory
        @Paramixel.Tag(" ")
        public static Action actionFactory() {
            return Direct.builder("blank-tag-action").execute(context -> {}).build();
        }
    }

    static class ErrorThrowingFactory {
        @Paramixel.ActionFactory
        public static Action actionFactory() {
            throw new TestError("factory error");
        }
    }

    static class TestError extends Error {
        TestError(String message) {
            super(message);
        }
    }
}

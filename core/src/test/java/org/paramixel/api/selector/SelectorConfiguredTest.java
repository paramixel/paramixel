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

package org.paramixel.api.selector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import nonapi.org.paramixel.selector.ConcreteSelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.exception.ConfigurationException;

@DisplayName("Selector configured")
class SelectorConfiguredTest {

    private static boolean matches(
            final Selector selector, final String packageName, final String className, final List<String> tags) {
        return ConcreteSelector.require(selector).test(packageName, className, tags);
    }

    @Nested
    @DisplayName("fromConfiguration")
    class FromConfiguration {

        @Test
        @DisplayName("an empty configuration selects everything")
        void emptySelectsEverything() {
            var selector = Selector.fromConfiguration(Configuration.of(Map.of()));

            assertThat(matches(selector, "a.b", "a.b.C", List.of())).isTrue();
            assertThat(matches(selector, "a.b", "a.b.C", List.of("smoke"))).isTrue();
        }

        @Test
        @DisplayName("reads the package, class and tag regex keys")
        void readsAllThreeKeys() {
            var selector = Selector.fromConfiguration(Configuration.of(Map.of(
                    Configuration.MATCH_PACKAGE_REGEX, "a\\.b",
                    Configuration.MATCH_CLASS_REGEX, ".*Test",
                    Configuration.MATCH_TAG_REGEX, "smoke")));

            assertThat(matches(selector, "a.b", "a.b.FastTest", List.of("smoke")))
                    .isTrue();
            assertThat(matches(selector, "a.b", "a.b.FastTest", List.of("slow")))
                    .isFalse();
            assertThat(matches(selector, "a.b", "a.b.Fixture", List.of("smoke")))
                    .isFalse();
            assertThat(matches(selector, "x", "a.b.FastTest", List.of("smoke"))).isFalse();
        }

        @Test
        @DisplayName("an invalid pattern throws ConfigurationException naming the key")
        void invalidPatternNamesTheKey() {
            var configuration = Configuration.of(Map.of(Configuration.MATCH_PACKAGE_REGEX, "[invalid"));

            assertThatThrownBy(() -> Selector.fromConfiguration(configuration))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(Configuration.MATCH_PACKAGE_REGEX)
                    .hasMessageContaining("[invalid");
        }

        @Test
        @DisplayName("a blank pattern throws ConfigurationException naming the key")
        void blankPatternNamesTheKey() {
            var configuration = Configuration.of(Map.of(Configuration.MATCH_TAG_REGEX, "   "));

            assertThatThrownBy(() -> Selector.fromConfiguration(configuration))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(Configuration.MATCH_TAG_REGEX)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("rejects a null configuration")
        void rejectsNullConfiguration() {
            assertThatThrownBy(() -> Selector.fromConfiguration(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("configured()")
    class Configured {

        @Test
        @DisplayName("is an unresolved placeholder that cannot be evaluated outside a runner")
        void unresolvedPlaceholderFailsClearly() {
            var selector = Selector.configured();

            assertThat(ConcreteSelector.require(selector).isConfiguredPlaceholder())
                    .isTrue();
            assertThat(ConcreteSelector.require(selector).usesConfiguration()).isTrue();
            assertThatThrownBy(() -> matches(selector, "a", "a.C", List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Selector.configured()");
        }

        @Test
        @DisplayName("resolves to the configuration selector")
        void resolvesWithConfiguration() {
            var selector = ConcreteSelector.require(Selector.configured())
                    .resolve(Configuration.of(Map.of(Configuration.MATCH_CLASS_REGEX, ".*Test")));

            assertThat(selector.isConfiguredPlaceholder()).isFalse();
            assertThat(selector.test("a", "a.FastTest", List.of())).isTrue();
            assertThat(selector.test("a", "a.Fixture", List.of())).isFalse();
        }

        @Test
        @DisplayName("resolves inside an and() composition")
        void resolvesInsideComposition() {
            var selector = Selector.configured()
                    .and(Selector.builder()
                            .tagPattern(java.util.regex.Pattern.compile("smoke"))
                            .build());
            var resolved = ConcreteSelector.require(selector)
                    .resolve(Configuration.of(Map.of(Configuration.MATCH_PACKAGE_REGEX, "a")));

            assertThat(resolved.test("a", "a.C", List.of("smoke"))).isTrue();
            assertThat(resolved.test("a", "a.C", List.of("slow"))).isFalse();
            assertThat(resolved.test("b", "b.C", List.of("smoke"))).isFalse();
        }
    }
}

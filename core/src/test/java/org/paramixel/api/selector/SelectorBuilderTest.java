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
import java.util.regex.Pattern;
import nonapi.org.paramixel.selector.ConcreteSelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Selector builder")
class SelectorBuilderTest {

    private static final List<String> NO_TAGS = List.of();

    private static boolean matches(
            final Selector selector, final String packageName, final String className, final List<String> tags) {
        return ConcreteSelector.require(selector).test(packageName, className, tags);
    }

    private static boolean matches(final Selector selector, final String packageName, final String className) {
        return matches(selector, packageName, className, NO_TAGS);
    }

    @Test
    @DisplayName("an empty builder selects everything")
    void emptyBuilderSelectsEverything() {
        var selector = Selector.builder().build();

        assertThat(matches(selector, "a.b", "a.b.C")).isTrue();
        assertThat(matches(selector, "", "C")).isTrue();
        assertThat(matches(selector, "a.b", "a.b.C", List.of("smoke"))).isTrue();
    }

    @Nested
    @DisplayName("package dimension")
    class PackageDimension {

        @Test
        @DisplayName("packageTreeOf selects the package and its subpackages")
        void packageTreeOf() {
            var selector =
                    Selector.builder().packageTreeOf(SelectorBuilderTest.class).build();
            var pkg = SelectorBuilderTest.class.getPackageName();

            assertThat(matches(selector, pkg, "a.b.C")).isTrue();
            assertThat(matches(selector, pkg + ".nested", "a.b.nested.C")).isTrue();
            assertThat(matches(selector, pkg + "x", "a.bx.C")).isFalse();
            assertThat(matches(selector, "other", "other.C")).isFalse();
        }

        @Test
        @DisplayName("packagePattern matches with full-match semantics")
        void packagePattern() {
            var selector =
                    Selector.builder().packagePattern(Pattern.compile("a\\.b")).build();

            assertThat(matches(selector, "a.b", "a.b.C")).isTrue();
            assertThat(matches(selector, "a.b.c", "a.b.c.C")).isFalse();
            assertThat(matches(selector, "xa.by", "xa.by.C")).isFalse();
        }

        @Test
        @DisplayName("excludePackageTreeOf keeps everything outside the package")
        void excludePackageTreeOf() {
            var selector = Selector.builder()
                    .excludePackageTreeOf(SelectorBuilderTest.class)
                    .build();
            var pkg = SelectorBuilderTest.class.getPackageName();

            assertThat(matches(selector, pkg, "a.b.C")).isFalse();
            assertThat(matches(selector, pkg + ".nested", "a.b.nested.C")).isFalse();
            assertThat(matches(selector, "other", "other.C")).isTrue();
        }

        @Test
        @DisplayName("excludePackagePattern rejects only matching package names")
        void excludePackagePattern() {
            var selector = Selector.builder()
                    .excludePackagePattern(Pattern.compile("a\\.b"))
                    .build();

            assertThat(matches(selector, "a.b", "a.b.C")).isFalse();
            assertThat(matches(selector, "a.b", "a.b", List.of())).isFalse();
            assertThat(matches(selector, "x.y", "x.y.C")).isTrue();
        }

        @Test
        @DisplayName("includes are ORed inside the dimension")
        void multipleIncludesAnyMayMatch() {
            var selector = Selector.builder()
                    .packagePattern(Pattern.compile("a"))
                    .packagePattern(Pattern.compile("b"))
                    .build();

            assertThat(matches(selector, "a", "a.C")).isTrue();
            assertThat(matches(selector, "b", "b.C")).isTrue();
            assertThat(matches(selector, "c", "c.C")).isFalse();
        }
    }

    @Nested
    @DisplayName("class dimension")
    class ClassDimension {

        @Test
        @DisplayName("classOf selects exactly one class")
        void classOf() {
            var selector = Selector.builder().classOf(SelectorBuilderTest.class).build();

            assertThat(matches(selector, "a.b", SelectorBuilderTest.class.getName()))
                    .isTrue();
            assertThat(matches(selector, "a.b", SelectorBuilderTest.class.getName() + "X"))
                    .isFalse();
        }

        @Test
        @DisplayName("excludeClassOf rejects exactly one class")
        void excludeClassOf() {
            var selector =
                    Selector.builder().excludeClassOf(SelectorBuilderTest.class).build();

            assertThat(matches(selector, "a.b", SelectorBuilderTest.class.getName()))
                    .isFalse();
            assertThat(matches(selector, "a.b", "a.b.Other")).isTrue();
        }

        @Test
        @DisplayName("classPattern and excludeClassPattern use full-match semantics")
        void classPattern() {
            var selector = Selector.builder()
                    .classPattern(Pattern.compile(".*Test"))
                    .excludeClassPattern(Pattern.compile(".*Slow.*"))
                    .build();

            assertThat(matches(selector, "a.b", "a.b.FastTest")).isTrue();
            assertThat(matches(selector, "a.b", "a.b.SlowTest")).isFalse();
            assertThat(matches(selector, "a.b", "a.b.Fixture")).isFalse();
        }
    }

    @Nested
    @DisplayName("dimensions are ANDed")
    class DimensionsAreAnded {

        @Test
        @DisplayName("package and class criteria must both match")
        void packageAndClass() {
            var selector = Selector.builder()
                    .packagePattern(Pattern.compile("a"))
                    .classPattern(Pattern.compile(".*Test"))
                    .build();

            assertThat(matches(selector, "a", "a.FastTest")).isTrue();
            assertThat(matches(selector, "a", "a.Fixture")).isFalse();
            assertThat(matches(selector, "b", "b.FastTest")).isFalse();
        }

        @Test
        @DisplayName("include and exclude in one dimension combine")
        void includeAndExclude() {
            var selector = Selector.builder()
                    .packagePattern(Pattern.compile("a|b"))
                    .excludePackagePattern(Pattern.compile("b"))
                    .build();

            assertThat(matches(selector, "a", "a.C")).isTrue();
            assertThat(matches(selector, "b", "b.C")).isFalse();
            assertThat(matches(selector, "c", "c.C")).isFalse();
        }

        @Test
        @DisplayName("and() requires both selectors to accept")
        void andRequiresBoth() {
            var left =
                    Selector.builder().packageTreeOf(SelectorBuilderTest.class).build();
            var right = Selector.builder().classOf(SelectorBuilderTest.class).build();
            var both = left.and(right);
            var pkg = SelectorBuilderTest.class.getPackageName();

            assertThat(matches(both, pkg, SelectorBuilderTest.class.getName())).isTrue();
            assertThat(matches(both, pkg, pkg + ".Other")).isFalse();
            assertThat(matches(both, "other", SelectorBuilderTest.class.getName()))
                    .isFalse();
        }

        @Test
        @DisplayName("and() rejects a null selector")
        void andRejectsNull() {
            assertThatThrownBy(() -> Selector.builder().build().and(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("tags")
    class Tags {

        @Test
        @DisplayName("a tag include requires at least one matching tag")
        void tagIncludeRequiresAMatch() {
            var selector =
                    Selector.builder().tagPattern(Pattern.compile("smoke")).build();

            assertThat(matches(selector, "a", "a.C", List.of("smoke"))).isTrue();
            assertThat(matches(selector, "a", "a.C", List.of("slow", "smoke"))).isTrue();
            assertThat(matches(selector, "a", "a.C", List.of("slow"))).isFalse();
        }

        @Test
        @DisplayName("an untagged class never matches a tag include")
        void untaggedNeverMatchesTagInclude() {
            var selector = Selector.builder().tagPattern(Pattern.compile(".*")).build();

            assertThat(matches(selector, "a", "a.C", NO_TAGS)).isFalse();
            assertThat(matches(selector, "a", "a.C", List.of("any"))).isTrue();
        }

        @Test
        @DisplayName("an untagged class is kept by a tag exclude")
        void untaggedKeptByTagExclude() {
            var selector = Selector.builder()
                    .excludeTagPattern(Pattern.compile("smoke|slow"))
                    .build();

            assertThat(matches(selector, "a", "a.C", NO_TAGS)).isTrue();
            assertThat(matches(selector, "a", "a.C", List.of("fast"))).isTrue();
            assertThat(matches(selector, "a", "a.C", List.of("smoke"))).isFalse();
            assertThat(matches(selector, "a", "a.C", List.of("fast", "slow"))).isFalse();
        }
    }

    @Nested
    @DisplayName("arguments")
    class Arguments {

        @Test
        @DisplayName("reject null criteria")
        void rejectNullCriteria() {
            var builder = Selector.builder();
            assertThatThrownBy(() -> builder.packageTreeOf(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> builder.excludePackageTreeOf(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> builder.packagePattern(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> builder.excludePackagePattern(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> builder.classOf(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> builder.excludeClassOf(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> builder.classPattern(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> builder.excludeClassPattern(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> builder.tagPattern(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> builder.excludeTagPattern(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects a selector the framework did not create")
        void rejectsForeignSelector() {
            var foreign = new Selector() {};

            assertThatThrownBy(() -> ConcreteSelector.require(foreign))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Selector.builder()");
        }
    }
}

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Selector")
class SelectorTest {

    @Nested
    @DisplayName("builder")
    class BuilderTests {

        @Test
        @DisplayName("creates selector with no criteria")
        void createsSelectorWithNoCriteria() {
            Selector selector = Selector.builder().build();

            assertThat(selector).isNotNull();
            assertThat(selector.matchesLocation(SelectorTest.class)).isTrue();
        }

        @Test
        @DisplayName("creates selector with package match")
        void createsSelectorWithPackageMatch() {
            Selector selector =
                    Selector.builder().packageMatch("org\\.paramixel").build();

            assertThat(selector).isNotNull();
        }

        @Test
        @DisplayName("creates selector with class match")
        void createsSelectorWithClassMatch() {
            Selector selector = Selector.builder().classMatch("SelectorTest").build();

            assertThat(selector).isNotNull();
        }

        @Test
        @DisplayName("creates selector with tag match")
        void createsSelectorWithTagMatch() {
            Selector selector = Selector.builder().tagMatch("smoke").build();

            assertThat(selector).isNotNull();
            assertThat(selector.getTagPattern()).isNotNull();
        }

        @Test
        @DisplayName("creates selector with location and tag match")
        void createsSelectorWithLocationAndTagMatch() {
            Selector selector = Selector.builder()
                    .packageMatch("org\\.paramixel")
                    .tagMatch("smoke")
                    .build();

            assertThat(selector).isNotNull();
        }

        @Test
        @DisplayName("packageMatch rejects null")
        void packageMatchRejectsNull() {
            assertThatThrownBy(() -> Selector.builder().packageMatch(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("packageMatch rejects blank")
        void packageMatchRejectsBlank() {
            assertThatThrownBy(() -> Selector.builder().packageMatch("")).isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> Selector.builder().packageMatch("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("packageMatch rejects invalid regex")
        void packageMatchRejectsInvalidRegex() {
            assertThatThrownBy(() -> Selector.builder().packageMatch("("))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("packageMatch");
        }

        @Test
        @DisplayName("classMatch rejects null")
        void classMatchRejectsNull() {
            assertThatThrownBy(() -> Selector.builder().classMatch(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("classMatch rejects blank")
        void classMatchRejectsBlank() {
            assertThatThrownBy(() -> Selector.builder().classMatch("")).isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> Selector.builder().classMatch("   ")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("classMatch rejects invalid regex")
        void classMatchRejectsInvalidRegex() {
            assertThatThrownBy(() -> Selector.builder().classMatch("("))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("classMatch");
        }

        @Test
        @DisplayName("tagMatch rejects null")
        void tagMatchRejectsNull() {
            assertThatThrownBy(() -> Selector.builder().tagMatch(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("tagMatch rejects blank")
        void tagMatchRejectsBlank() {
            assertThatThrownBy(() -> Selector.builder().tagMatch("")).isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> Selector.builder().tagMatch("   ")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("tagMatch rejects invalid regex")
        void tagMatchRejectsInvalidRegex() {
            assertThatThrownBy(() -> Selector.builder().tagMatch("("))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tagMatch");
        }

        @Test
        @DisplayName("packageOf rejects null")
        void packageOfRejectsNull() {
            assertThatThrownBy(() -> Selector.builder().packageOf(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("packageOf creates anchored package pattern matching same package")
        void packageOfCreatesAnchoredPackagePattern() {
            Selector selector = Selector.builder().packageOf(SelectorTest.class).build();

            assertThat(selector.matchesLocation(SelectorTest.class)).isTrue();
            assertThat(selector.matchesLocation(String.class)).isFalse();
        }

        @Test
        @DisplayName("classOf rejects null")
        void classOfRejectsNull() {
            assertThatThrownBy(() -> Selector.builder().classOf(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("classOf creates anchored class pattern matching exact class")
        void classOfCreatesAnchoredClassPattern() {
            Selector selector = Selector.builder().classOf(SelectorTest.class).build();

            assertThat(selector.matchesLocation(SelectorTest.class)).isTrue();
            assertThat(selector.matchesLocation(String.class)).isFalse();
        }

        @Test
        @DisplayName("build rejects both package and class match")
        void buildRejectsBothPackageAndClassMatch() {
            assertThatThrownBy(() -> Selector.builder()
                            .packageMatch("org")
                            .classMatch("Test")
                            .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("only one location match");
        }
    }

    @Nested
    @DisplayName("matchesLocation")
    class MatchesLocationTests {

        @Test
        @DisplayName("rejects null class")
        void rejectsNullClass() {
            Selector selector = Selector.builder().build();

            assertThatThrownBy(() -> selector.matchesLocation(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("returns true when no pattern is set")
        void returnsTrueWhenNoPatternIsSet() {
            Selector selector = Selector.builder().build();

            assertThat(selector.matchesLocation(SelectorTest.class)).isTrue();
        }

        @Test
        @DisplayName("matches package in package mode")
        void matchesPackageInPackageMode() {
            Selector selector =
                    Selector.builder().packageMatch("org\\.paramixel\\.core").build();

            assertThat(selector.matchesLocation(SelectorTest.class)).isTrue();
        }

        @Test
        @DisplayName("does not match unrelated package in package mode")
        void doesNotMatchUnrelatedPackageInPackageMode() {
            Selector selector = Selector.builder().packageMatch("com\\.example").build();

            assertThat(selector.matchesLocation(SelectorTest.class)).isFalse();
        }

        @Test
        @DisplayName("matches class name in class mode")
        void matchesClassNameInClassMode() {
            Selector selector = Selector.builder().classMatch("SelectorTest").build();

            assertThat(selector.matchesLocation(SelectorTest.class)).isTrue();
        }

        @Test
        @DisplayName("does not match unrelated class in class mode")
        void doesNotMatchUnrelatedClassInClassMode() {
            Selector selector = Selector.builder().classMatch("NonExistent").build();

            assertThat(selector.matchesLocation(SelectorTest.class)).isFalse();
        }
    }

    @Nested
    @DisplayName("getTagPattern")
    class GetTagPatternTests {

        @Test
        @DisplayName("returns null when no tag pattern is set")
        void returnsNullWhenNoTagPatternIsSet() {
            Selector selector = Selector.builder().build();

            assertThat(selector.getTagPattern()).isNull();
        }

        @Test
        @DisplayName("returns pattern when tag match is set")
        void returnsPatternWhenTagMatchIsSet() {
            Selector selector = Selector.builder().tagMatch("smoke").build();

            assertThat(selector.getTagPattern()).isNotNull();
            assertThat(selector.getTagPattern().pattern()).isEqualTo("smoke");
        }
    }
}

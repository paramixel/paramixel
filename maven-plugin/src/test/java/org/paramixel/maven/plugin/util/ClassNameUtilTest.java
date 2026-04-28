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

package org.paramixel.maven.plugin.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.maven.plugin.internal.util.ClassNameUtil;

@DisplayName("ClassNameUtil tests")
class ClassNameUtilTest {

    @Nested
    @DisplayName("abbreviateClassName() argument validation")
    class ArgumentValidationTests {

        @Test
        @DisplayName("should throw NPE when fullClassName is null")
        void shouldThrowNpeWhenFullClassNameIsNull() {
            assertThatThrownBy(() -> ClassNameUtil.abbreviateClassName(null, 10))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("fullClassName");
        }

        @Test
        @DisplayName("should throw IAE when maxLength is zero")
        void shouldThrowIaeWhenMaxLengthIsZero() {
            assertThatThrownBy(() -> ClassNameUtil.abbreviateClassName("com.example.Test", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxLength");
        }

        @Test
        @DisplayName("should throw IAE when maxLength is negative")
        void shouldThrowIaeWhenMaxLengthIsNegative() {
            assertThatThrownBy(() -> ClassNameUtil.abbreviateClassName("com.example.Test", -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxLength");
        }
    }

    @Nested
    @DisplayName("abbreviateClassName() valid inputs")
    class ValidInputTests {

        @Test
        @DisplayName("should abbreviate fully-qualified class name")
        void shouldAbbreviateFullyQualifiedName() {
            var result = ClassNameUtil.abbreviateClassName("com.example.TestClass", 20);
            assertThat(result).isEqualTo("c.example.TestClass");
        }

        @Test
        @DisplayName("should return simple class name as-is")
        void shouldReturnSimpleClassNameAsIs() {
            var result = ClassNameUtil.abbreviateClassName("TestClass", Integer.MAX_VALUE);
            assertThat(result).isEqualTo("TestClass");
        }

        @Test
        @DisplayName("should return minimal abbreviation when maxLength is too small")
        void shouldReturnMinimalAbbreviationWhenMaxLengthTooSmall() {
            var result = ClassNameUtil.abbreviateClassName("com.example.TestClass", 3);
            assertThat(result).isEqualTo("c.e.TestClass");
        }

        @Test
        @DisplayName("should progressively expand from right to left")
        void shouldProgressivelyExpandFromRightToLeft() {
            // com.example.TestClass -> minimal: c.e.TestClass (14 chars)
            // maxLength 20 allows expanding the rightmost abbreviated segment first
            var result = ClassNameUtil.abbreviateClassName("com.example.TestClass", 20);
            assertThat(result).isEqualTo("c.example.TestClass");
        }

        @Test
        @DisplayName("should return full name when maxLength is MAX_VALUE")
        void shouldReturnFullNameWhenMaxLengthIsMaxValue() {
            var result = ClassNameUtil.abbreviateClassName("com.example.TestClass", Integer.MAX_VALUE);
            assertThat(result).isEqualTo("com.example.TestClass");
        }

        @Test
        @DisplayName("should handle deeply nested packages")
        void shouldHandleDeeplyNestedPackages() {
            var result = ClassNameUtil.abbreviateClassName("org.paramixel.maven.plugin.util.ClassNameUtil", 40);
            assertThat(result).isEqualTo("o.p.maven.plugin.util.ClassNameUtil");
        }

        @Test
        @DisplayName("should handle single-segment name with MAX_VALUE")
        void shouldHandleSingleSegmentNameWithMaxValue() {
            var result = ClassNameUtil.abbreviateClassName("SingleClass", Integer.MAX_VALUE);
            assertThat(result).isEqualTo("SingleClass");
        }

        @Test
        @DisplayName("should return full name when it fits within maxLength")
        void shouldReturnFullNameWhenItFitsWithinMaxLength() {
            var result = ClassNameUtil.abbreviateClassName("com.example.TestClass", 100);
            assertThat(result).isEqualTo("com.example.TestClass");
        }

        @Test
        @DisplayName("should handle empty package segment gracefully")
        void shouldHandleEmptyPackageSegmentGracefully() {
            // Splitting "com..TestClass" on "." produces ["com", "", "TestClass"]
            // The empty segment is abbreviated to empty string
            var result = ClassNameUtil.abbreviateClassName("com..TestClass", 20);
            assertThat(result).contains("TestClass");
        }

        @Test
        @DisplayName("should abbreviate multi-level package progressively")
        void shouldAbbreviateMultiLevelPackageProgressively() {
            // org.apache.commons.lang3.StringUtils -> minimal: o.a.c.l.StringUtils (22 chars)
            var result = ClassNameUtil.abbreviateClassName("org.apache.commons.lang3.StringUtils", 30);
            // Should expand some segments from right to left
            assertThat(result).contains("StringUtils");
            assertThat(result.length()).isLessThanOrEqualTo(30);
        }
    }
}

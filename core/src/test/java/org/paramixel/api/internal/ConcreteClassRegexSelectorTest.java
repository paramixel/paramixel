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

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConcreteClassRegexSelector")
class ConcreteClassRegexSelectorTest {

    @Test
    @DisplayName("constructor rejects null pattern")
    void constructorRejectsNullPattern() {
        assertThatThrownBy(() -> new ConcreteClassRegexSelector(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("pattern must not be null");
    }

    @Test
    @DisplayName("getPattern returns the compiled pattern")
    void getPatternReturnsCompiledPattern() {
        var selector = new ConcreteClassRegexSelector(Pattern.compile("Test"));

        assertThat(selector.pattern()).isNotNull();
        assertThat(selector.pattern().pattern()).isEqualTo("Test");
    }

    @Test
    @DisplayName("matchesClass returns true for matching class name")
    void matchesClassReturnsTrueForMatch() {
        var selector = new ConcreteClassRegexSelector(Pattern.compile("Test"));

        assertThat(selector.matchesClass("com.example.MyTest")).isTrue();
    }

    @Test
    @DisplayName("matchesClass returns false for non-matching class name")
    void matchesClassReturnsFalseForNonMatch() {
        var selector = new ConcreteClassRegexSelector(Pattern.compile("Test"));

        assertThat(selector.matchesClass("com.example.Runner")).isFalse();
    }

    @Test
    @DisplayName("matchesClass rejects null")
    void matchesClassRejectsNull() {
        var selector = new ConcreteClassRegexSelector(Pattern.compile("Test"));

        assertThatThrownBy(() -> selector.matchesClass(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("className must not be null");
    }

    @Test
    @DisplayName("matchesPackage always returns true")
    void matchesPackageAlwaysReturnsTrue() {
        var selector = new ConcreteClassRegexSelector(Pattern.compile("Test"));

        assertThat(selector.matchesPackage("com.example")).isTrue();
    }

    @Test
    @DisplayName("matchesTag always returns true")
    void matchesTagAlwaysReturnsTrue() {
        var selector = new ConcreteClassRegexSelector(Pattern.compile("Test"));

        assertThat(selector.matchesTag("smoke")).isTrue();
    }

    @Test
    @DisplayName("equals returns true for same reference")
    void equalsReturnsTrueForSameReference() {
        var selector = new ConcreteClassRegexSelector(Pattern.compile("Test"));

        assertThat(selector.equals(selector)).isTrue();
    }

    @Test
    @DisplayName("equals returns true for same pattern and flags")
    void equalsReturnsTrueForSamePatternAndFlags() {
        var selector1 = new ConcreteClassRegexSelector(Pattern.compile("Test"));
        var selector2 = new ConcreteClassRegexSelector(Pattern.compile("Test"));

        assertThat(selector1.equals(selector2)).isTrue();
        assertThat(selector1.hashCode()).isEqualTo(selector2.hashCode());
    }

    @Test
    @DisplayName("equals returns false for different pattern")
    void equalsReturnsFalseForDifferentPattern() {
        var selector1 = new ConcreteClassRegexSelector(Pattern.compile("Test"));
        var selector2 = new ConcreteClassRegexSelector(Pattern.compile("Other"));

        assertThat(selector1.equals(selector2)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for different flags")
    void equalsReturnsFalseForDifferentFlags() {
        var selector1 = new ConcreteClassRegexSelector(Pattern.compile("Test"));
        var selector2 = new ConcreteClassRegexSelector(Pattern.compile("Test", Pattern.CASE_INSENSITIVE));

        assertThat(selector1.equals(selector2)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for null")
    void equalsReturnsFalseForNull() {
        var selector = new ConcreteClassRegexSelector(Pattern.compile("Test"));

        assertThat(selector.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for different type")
    void equalsReturnsFalseForDifferentType() {
        var selector = new ConcreteClassRegexSelector(Pattern.compile("Test"));

        assertThat(selector.equals("Test")).isFalse();
    }

    @Test
    @DisplayName("hashCode is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
        var selector1 = new ConcreteClassRegexSelector(Pattern.compile("Test"));
        var selector2 = new ConcreteClassRegexSelector(Pattern.compile("Test"));

        assertThat(selector1.equals(selector2)).isTrue();
        assertThat(selector1.hashCode()).isEqualTo(selector2.hashCode());
    }

    @Test
    @DisplayName("toString contains pattern")
    void toStringContainsPattern() {
        var selector = new ConcreteClassRegexSelector(Pattern.compile("Test"));

        assertThat(selector.toString()).isEqualTo("ClassRegexSelector{pattern='Test'}");
    }
}

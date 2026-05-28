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

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConcreteTagRegexSelector")
class ConcreteTagRegexSelectorTest {

    @Test
    @DisplayName("constructor rejects null pattern")
    void constructorRejectsNullPattern() {
        assertThatThrownBy(() -> new ConcreteTagRegexSelector(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("pattern is null");
    }

    @Test
    @DisplayName("getPattern returns the compiled pattern")
    void getPatternReturnsCompiledPattern() {
        var selector = new ConcreteTagRegexSelector(Pattern.compile("smoke"));

        assertThat(selector.pattern()).isNotNull();
        assertThat(selector.pattern().pattern()).isEqualTo("smoke");
    }

    @Test
    @DisplayName("matchesTag returns true for matching tag")
    void matchesTagReturnsTrueForMatch() {
        var selector = new ConcreteTagRegexSelector(Pattern.compile("smoke"));

        assertThat(selector.matchesTag("smoke-test")).isTrue();
    }

    @Test
    @DisplayName("matchesTag returns false for non-matching tag")
    void matchesTagReturnsFalseForNonMatch() {
        var selector = new ConcreteTagRegexSelector(Pattern.compile("smoke"));

        assertThat(selector.matchesTag("integration")).isFalse();
    }

    @Test
    @DisplayName("matchesTag rejects null")
    void matchesTagRejectsNull() {
        var selector = new ConcreteTagRegexSelector(Pattern.compile("smoke"));

        assertThatThrownBy(() -> selector.matchesTag(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tag is null");
    }

    @Test
    @DisplayName("matchesClass always returns true")
    void matchesClassAlwaysReturnsTrue() {
        var selector = new ConcreteTagRegexSelector(Pattern.compile("smoke"));

        assertThat(selector.matchesClass("com.example.MyTest")).isTrue();
    }

    @Test
    @DisplayName("matchesPackage always returns true")
    void matchesPackageAlwaysReturnsTrue() {
        var selector = new ConcreteTagRegexSelector(Pattern.compile("smoke"));

        assertThat(selector.matchesPackage("com.example")).isTrue();
    }

    @Test
    @DisplayName("equals returns true for same reference")
    void equalsReturnsTrueForSameReference() {
        var selector = new ConcreteTagRegexSelector(Pattern.compile("smoke"));

        assertThat(selector.equals(selector)).isTrue();
    }

    @Test
    @DisplayName("equals returns true for same pattern and flags")
    void equalsReturnsTrueForSamePatternAndFlags() {
        var selector1 = new ConcreteTagRegexSelector(Pattern.compile("smoke"));
        var selector2 = new ConcreteTagRegexSelector(Pattern.compile("smoke"));

        assertThat(selector1.equals(selector2)).isTrue();
        assertThat(selector1.hashCode()).isEqualTo(selector2.hashCode());
    }

    @Test
    @DisplayName("equals returns false for different pattern")
    void equalsReturnsFalseForDifferentPattern() {
        var selector1 = new ConcreteTagRegexSelector(Pattern.compile("smoke"));
        var selector2 = new ConcreteTagRegexSelector(Pattern.compile("integration"));

        assertThat(selector1.equals(selector2)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for different flags")
    void equalsReturnsFalseForDifferentFlags() {
        var selector1 = new ConcreteTagRegexSelector(Pattern.compile("smoke"));
        var selector2 = new ConcreteTagRegexSelector(Pattern.compile("smoke", Pattern.CASE_INSENSITIVE));

        assertThat(selector1.equals(selector2)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for null")
    void equalsReturnsFalseForNull() {
        var selector = new ConcreteTagRegexSelector(Pattern.compile("smoke"));

        assertThat(selector.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for different type")
    void equalsReturnsFalseForDifferentType() {
        var selector = new ConcreteTagRegexSelector(Pattern.compile("smoke"));

        assertThat(selector.equals("smoke")).isFalse();
    }

    @Test
    @DisplayName("hashCode is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
        var selector1 = new ConcreteTagRegexSelector(Pattern.compile("smoke"));
        var selector2 = new ConcreteTagRegexSelector(Pattern.compile("smoke"));

        assertThat(selector1.equals(selector2)).isTrue();
        assertThat(selector1.hashCode()).isEqualTo(selector2.hashCode());
    }

    @Test
    @DisplayName("toString contains pattern")
    void toStringContainsPattern() {
        var selector = new ConcreteTagRegexSelector(Pattern.compile("smoke"));

        assertThat(selector.toString()).isEqualTo("TagRegexSelector{pattern='smoke'}");
    }
}

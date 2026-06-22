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

package nonapi.org.paramixel.selector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConcretePackageRegexSelector")
class ConcretePackageRegexSelectorTest {

    @Test
    @DisplayName("constructor rejects null pattern")
    void constructorRejectsNullPattern() {
        assertThatThrownBy(() -> new ConcretePackageRegexSelector(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("pattern is null");
    }

    @Test
    @DisplayName("getPattern returns the compiled pattern")
    void getPatternReturnsCompiledPattern() {
        var selector = new ConcretePackageRegexSelector(Pattern.compile("org\\.paramixel"));

        assertThat(selector.pattern()).isNotNull();
        assertThat(selector.pattern().pattern()).isEqualTo("org\\.paramixel");
    }

    @Test
    @DisplayName("matchesPackage returns true for matching package")
    void matchesPackageReturnsTrueForMatch() {
        var selector = new ConcretePackageRegexSelector(Pattern.compile("org\\.paramixel\\.api"));

        assertThat(selector.matchesPackage("org.paramixel.api")).isTrue();
    }

    @Test
    @DisplayName("matchesPackage returns false for non-matching package")
    void matchesPackageReturnsFalseForNonMatch() {
        var selector = new ConcretePackageRegexSelector(Pattern.compile("paramixel"));

        assertThat(selector.matchesPackage("com.example")).isFalse();
    }

    @Test
    @DisplayName("matchesPackage rejects null")
    void matchesPackageRejectsNull() {
        var selector = new ConcretePackageRegexSelector(Pattern.compile("org"));

        assertThatThrownBy(() -> selector.matchesPackage(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("packageName is null");
    }

    @Test
    @DisplayName("matchesClass always returns true")
    void matchesClassAlwaysReturnsTrue() {
        var selector = new ConcretePackageRegexSelector(Pattern.compile("org\\.paramixel"));

        assertThat(selector.matchesClass("com.example.MyTest")).isTrue();
    }

    @Test
    @DisplayName("matchesTag always returns true")
    void matchesTagAlwaysReturnsTrue() {
        var selector = new ConcretePackageRegexSelector(Pattern.compile("org\\.paramixel"));

        assertThat(selector.matchesTag("smoke")).isTrue();
    }

    @Test
    @DisplayName("equals returns true for same reference")
    void equalsReturnsTrueForSameReference() {
        var selector = new ConcretePackageRegexSelector(Pattern.compile("org"));

        assertThat(selector.equals(selector)).isTrue();
    }

    @Test
    @DisplayName("equals returns true for same pattern and flags")
    void equalsReturnsTrueForSamePatternAndFlags() {
        var selector1 = new ConcretePackageRegexSelector(Pattern.compile("org"));
        var selector2 = new ConcretePackageRegexSelector(Pattern.compile("org"));

        assertThat(selector1.equals(selector2)).isTrue();
        assertThat(selector1.hashCode()).isEqualTo(selector2.hashCode());
    }

    @Test
    @DisplayName("equals returns false for different pattern")
    void equalsReturnsFalseForDifferentPattern() {
        var selector1 = new ConcretePackageRegexSelector(Pattern.compile("org"));
        var selector2 = new ConcretePackageRegexSelector(Pattern.compile("com"));

        assertThat(selector1.equals(selector2)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for different flags")
    void equalsReturnsFalseForDifferentFlags() {
        var selector1 = new ConcretePackageRegexSelector(Pattern.compile("org"));
        var selector2 = new ConcretePackageRegexSelector(Pattern.compile("org", Pattern.CASE_INSENSITIVE));

        assertThat(selector1.equals(selector2)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for null")
    void equalsReturnsFalseForNull() {
        var selector = new ConcretePackageRegexSelector(Pattern.compile("org"));

        assertThat(selector.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals returns false for different type")
    void equalsReturnsFalseForDifferentType() {
        var selector = new ConcretePackageRegexSelector(Pattern.compile("org"));

        assertThat(selector.equals("org")).isFalse();
    }

    @Test
    @DisplayName("hashCode is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
        var selector1 = new ConcretePackageRegexSelector(Pattern.compile("org"));
        var selector2 = new ConcretePackageRegexSelector(Pattern.compile("org"));

        assertThat(selector1.equals(selector2)).isTrue();
        assertThat(selector1.hashCode()).isEqualTo(selector2.hashCode());
    }

    @Test
    @DisplayName("toString contains pattern")
    void toStringContainsPattern() {
        var selector = new ConcretePackageRegexSelector(Pattern.compile("org\\.paramixel"));

        assertThat(selector.toString()).isEqualTo("PackageRegexSelector{pattern='org\\.paramixel'}");
    }
}

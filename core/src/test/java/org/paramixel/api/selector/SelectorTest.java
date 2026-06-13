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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Selector")
class SelectorTest {

    @Test
    @DisplayName("all() matches all packages")
    void allMatchesAllPackages() {
        var selector = Selector.all();

        assertThat(selector.matchesPackage(SelectorTest.class.getPackageName())).isTrue();
    }

    @Test
    @DisplayName("all() matches all classes")
    void allMatchesAllClasses() {
        var selector = Selector.all();

        assertThat(selector.matchesClass("org.paramixel.api.SelectorTest")).isTrue();
    }

    @Test
    @DisplayName("all() matches all tags")
    void allMatchesAllTags() {
        var selector = Selector.all();

        assertThat(selector.matchesTag("anything")).isTrue();
    }

    @Test
    @DisplayName("all() returns singleton")
    void allReturnsSingleton() {
        assertThat(Selector.all()).isSameAs(Selector.all());
    }

    @Test
    @DisplayName("packageRegex matches package")
    void packageRegexMatchesPackage() {
        var selector = Selector.packageRegex("org\\.paramixel\\.api");

        assertThat(selector.matchesPackage(SelectorTest.class.getPackageName())).isTrue();
    }

    @Test
    @DisplayName("packageRegex does not match unrelated package")
    void packageRegexDoesNotMatchUnrelatedPackage() {
        var selector = Selector.packageRegex("com\\.example");

        assertThat(selector.matchesPackage(SelectorTest.class.getPackageName())).isFalse();
    }

    @Test
    @DisplayName("packageRegex returns true for matchesClass")
    void packageRegexReturnsTrueForMatchesClass() {
        var selector = Selector.packageRegex("org\\.paramixel");

        assertThat(selector.matchesClass("org.paramixel.api.SelectorTest")).isTrue();
    }

    @Test
    @DisplayName("packageRegex returns true for matchesTag")
    void packageRegexReturnsTrueForMatchesTag() {
        var selector = Selector.packageRegex("org\\.paramixel");

        assertThat(selector.matchesTag("smoke")).isTrue();
    }

    @Test
    @DisplayName("classRegex matches class name")
    void classRegexMatchesClassName() {
        var selector = Selector.classRegex("SelectorTest");

        assertThat(selector.matchesClass(SelectorTest.class.getName())).isTrue();
    }

    @Test
    @DisplayName("classRegex does not match unrelated class")
    void classRegexDoesNotMatchUnrelatedClass() {
        var selector = Selector.classRegex("NonExistent");

        assertThat(selector.matchesClass(SelectorTest.class.getName())).isFalse();
    }

    @Test
    @DisplayName("classRegex returns true for matchesPackage")
    void classRegexReturnsTrueForMatchesPackage() {
        var selector = Selector.classRegex("SelectorTest");

        assertThat(selector.matchesPackage("org.paramixel.api")).isTrue();
    }

    @Test
    @DisplayName("classRegex returns true for matchesTag")
    void classRegexReturnsTrueForMatchesTag() {
        var selector = Selector.classRegex("SelectorTest");

        assertThat(selector.matchesTag("smoke")).isTrue();
    }

    @Test
    @DisplayName("tagRegex matches tag value")
    void tagRegexMatchesTagValue() {
        var selector = Selector.tagRegex("smoke");

        assertThat(selector.matchesTag("smoke")).isTrue();
        assertThat(selector.matchesTag("smoke-test")).isTrue();
        assertThat(selector.matchesTag("integration")).isFalse();
    }

    @Test
    @DisplayName("tagRegex returns true for matchesPackage")
    void tagRegexReturnsTrueForMatchesPackage() {
        var selector = Selector.tagRegex("smoke");

        assertThat(selector.matchesPackage("org.paramixel.api")).isTrue();
    }

    @Test
    @DisplayName("tagRegex returns true for matchesClass")
    void tagRegexReturnsTrueForMatchesClass() {
        var selector = Selector.tagRegex("smoke");

        assertThat(selector.matchesClass("org.paramixel.api.SelectorTest")).isTrue();
    }

    @Test
    @DisplayName("packageRegex is instance of PackageRegexSelector")
    void packageRegexIsInstanceOfPackageRegexSelector() {
        var selector = Selector.packageRegex("org\\.paramixel");

        assertThat(selector).isInstanceOf(PackageRegexSelector.class);
        assertThat(((PackageRegexSelector) selector).pattern().pattern()).isEqualTo("org\\.paramixel");
    }

    @Test
    @DisplayName("classRegex is instance of ClassRegexSelector")
    void classRegexIsInstanceOfClassRegexSelector() {
        var selector = Selector.classRegex("SelectorTest");

        assertThat(selector).isInstanceOf(ClassRegexSelector.class);
        assertThat(((ClassRegexSelector) selector).pattern().pattern()).isEqualTo("SelectorTest");
    }

    @Test
    @DisplayName("tagRegex is instance of TagRegexSelector")
    void tagRegexIsInstanceOfTagRegexSelector() {
        var selector = Selector.tagRegex("smoke");

        assertThat(selector).isInstanceOf(TagRegexSelector.class);
        assertThat(((TagRegexSelector) selector).pattern().pattern()).isEqualTo("smoke");
    }

    @Test
    @DisplayName("rejects null packageName in matchesPackage")
    void rejectsNullPackageName() {
        var selector = Selector.all();

        assertThatThrownBy(() -> selector.matchesPackage(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects null className in matchesClass")
    void rejectsNullClassName() {
        var selector = Selector.all();

        assertThatThrownBy(() -> selector.matchesClass(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects null tag in matchesTag")
    void rejectsNullTag() {
        var selector = Selector.tagRegex("smoke");

        assertThatThrownBy(() -> selector.matchesTag(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("packageTreeOf matches same package")
    void packageTreeOfMatchesSamePackage() {
        var selector = Selector.packageTreeOf(SelectorTest.class);

        assertThat(selector.matchesPackage(SelectorTest.class.getPackageName())).isTrue();
    }

    @Test
    @DisplayName("packageTreeOf rejects different package")
    void packageTreeOfRejectsDifferentPackage() {
        var selector = Selector.packageTreeOf(SelectorTest.class);

        assertThat(selector.matchesPackage(String.class.getPackageName())).isFalse();
    }

    @Test
    @DisplayName("packageTreeOf matches subpackage")
    void packageTreeOfMatchesSubpackage() {
        var selector = Selector.packageTreeOf(SelectorTest.class);

        assertThat(selector.matchesPackage(SelectorTest.class.getPackageName() + ".sub"))
                .isTrue();
    }

    @Test
    @DisplayName("packageOf matches same package (exact)")
    void packageOfMatchesSamePackage() {
        var selector = Selector.packageOf(SelectorTest.class);

        assertThat(selector.matchesPackage(SelectorTest.class.getPackageName())).isTrue();
    }

    @Test
    @DisplayName("packageOf rejects subpackage")
    void packageOfRejectsSubpackage() {
        var selector = Selector.packageOf(SelectorTest.class);

        assertThat(selector.matchesPackage(SelectorTest.class.getPackageName() + ".sub"))
                .isFalse();
    }

    @Test
    @DisplayName("packageOf rejects different package")
    void packageOfRejectsDifferentPackage() {
        var selector = Selector.packageOf(SelectorTest.class);

        assertThat(selector.matchesPackage(String.class.getPackageName())).isFalse();
    }

    @Test
    @DisplayName("classOf matches exact class")
    void classOfMatchesExactClass() {
        var selector = Selector.classOf(SelectorTest.class);

        assertThat(selector.matchesClass(SelectorTest.class.getName())).isTrue();
    }

    @Test
    @DisplayName("classOf rejects different class")
    void classOfRejectsDifferentClass() {
        var selector = Selector.classOf(SelectorTest.class);

        assertThat(selector.matchesClass(String.class.getName())).isFalse();
    }

    @Test
    @DisplayName("matchesTag returns true for all() when no tag pattern set")
    void matchesTagReturnsTrueForAll() {
        var selector = Selector.all();

        assertThat(selector.matchesTag("anything")).isTrue();
    }

    @Test
    @DisplayName("matchesPackage rejects null")
    void matchesPackageRejectsNull() {
        var selector = Selector.packageRegex("test");

        assertThatThrownBy(() -> selector.matchesPackage(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("matchesClass rejects null")
    void matchesClassRejectsNull() {
        var selector = Selector.classRegex("test");

        assertThatThrownBy(() -> selector.matchesClass(null)).isInstanceOf(NullPointerException.class);
    }
}

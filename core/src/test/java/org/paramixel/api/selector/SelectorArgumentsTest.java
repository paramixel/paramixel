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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Selector arguments")
class SelectorArgumentsTest {

    @Test
    @DisplayName("all() matches everything")
    void allMatchesEverything() {
        Selector selector = Selector.all();

        assertThat(selector).isNotNull();
        assertThat(selector.matchesPackage(SelectorArgumentsTest.class.getPackageName()))
                .isTrue();
        assertThat(selector.matchesClass("org.paramixel.api.SelectorArgumentsTest"))
                .isTrue();
        assertThat(selector.matchesTag("smoke")).isTrue();
    }

    @Test
    @DisplayName("packageRegex creates package regex selector")
    void packageRegexCreatesSelector() {
        Selector selector = Selector.packageRegex("org\\.paramixel");

        assertThat(selector).isNotNull();
        assertThat(selector).isInstanceOf(PackageRegexSelector.class);
    }

    @Test
    @DisplayName("classRegex creates class regex selector")
    void classRegexCreatesSelector() {
        Selector selector = Selector.classRegex("SelectorArgumentsTest");

        assertThat(selector).isNotNull();
        assertThat(selector).isInstanceOf(ClassRegexSelector.class);
    }

    @Test
    @DisplayName("tagRegex creates tag regex selector")
    void tagRegexCreatesSelector() {
        Selector selector = Selector.tagRegex("smoke");

        assertThat(selector).isNotNull();
        assertThat(selector).isInstanceOf(TagRegexSelector.class);
        assertThat(selector.matchesTag("smoke")).isTrue();
    }

    @Test
    @DisplayName("and() creates AND selector")
    void andCreatesSelector() {
        Selector selector = Selector.and(Selector.packageRegex("org\\.paramixel"), Selector.tagRegex("smoke"));

        assertThat(selector).isNotNull();
        assertThat(selector).isInstanceOf(AndSelector.class);
    }

    @Test
    @DisplayName("packageRegex rejects null")
    void packageRegexRejectsNull() {
        assertThatThrownBy(() -> Selector.packageRegex(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("packageRegex rejects blank")
    void packageRegexRejectsBlank() {
        assertThatThrownBy(() -> Selector.packageRegex("")).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Selector.packageRegex("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("packageRegex rejects invalid regex")
    void packageRegexRejectsInvalidRegex() {
        assertThatThrownBy(() -> Selector.packageRegex("("))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("packageRegex");
    }

    @Test
    @DisplayName("classRegex rejects null")
    void classRegexRejectsNull() {
        assertThatThrownBy(() -> Selector.classRegex(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("classRegex rejects blank")
    void classRegexRejectsBlank() {
        assertThatThrownBy(() -> Selector.classRegex("")).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Selector.classRegex("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("classRegex rejects invalid regex")
    void classRegexRejectsInvalidRegex() {
        assertThatThrownBy(() -> Selector.classRegex("("))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("classRegex");
    }

    @Test
    @DisplayName("tagRegex rejects null")
    void tagRegexRejectsNull() {
        assertThatThrownBy(() -> Selector.tagRegex(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("tagRegex rejects blank")
    void tagRegexRejectsBlank() {
        assertThatThrownBy(() -> Selector.tagRegex("")).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Selector.tagRegex("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tagRegex rejects invalid regex")
    void tagRegexRejectsInvalidRegex() {
        assertThatThrownBy(() -> Selector.tagRegex("("))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tagRegex");
    }

    @Test
    @DisplayName("packageTreeOf rejects null")
    void packageTreeOfRejectsNull() {
        assertThatThrownBy(() -> Selector.packageTreeOf(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("packageTreeOf creates anchored package pattern matching same package")
    void packageTreeOfCreatesAnchoredPackagePattern() {
        Selector selector = Selector.packageTreeOf(SelectorArgumentsTest.class);

        assertThat(selector.matchesPackage(SelectorArgumentsTest.class.getPackageName()))
                .isTrue();
        assertThat(selector.matchesPackage(String.class.getPackageName())).isFalse();
    }

    @Test
    @DisplayName("classOf rejects null")
    void classOfRejectsNull() {
        assertThatThrownBy(() -> Selector.classOf(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("classOf creates anchored class pattern matching exact class")
    void classOfCreatesAnchoredClassPattern() {
        Selector selector = Selector.classOf(SelectorArgumentsTest.class);

        assertThat(selector.matchesClass(SelectorArgumentsTest.class.getName())).isTrue();
        assertThat(selector.matchesClass(String.class.getName())).isFalse();
    }

    @Test
    @DisplayName("packageOf rejects null")
    void packageOfRejectsNull() {
        assertThatThrownBy(() -> Selector.packageOf(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("packageOf creates exact package pattern")
    void packageOfCreatesExactPackagePattern() {
        Selector selector = Selector.packageOf(SelectorArgumentsTest.class);

        assertThat(selector.matchesPackage(SelectorArgumentsTest.class.getPackageName()))
                .isTrue();
        assertThat(selector.matchesPackage(SelectorArgumentsTest.class.getPackageName() + ".sub"))
                .isFalse();
    }

    @Test
    @DisplayName("and(varargs) rejects null array")
    void andVarargsRejectsNullArray() {
        assertThatThrownBy(() -> Selector.and((Selector[]) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("and(varargs) rejects null element")
    void andVarargsRejectsNullElement() {
        assertThatThrownBy(() -> Selector.and(Selector.packageRegex("org"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("and(varargs) requires at least 2 selectors")
    void andVarargsRequiresAtLeast2() {
        assertThatThrownBy(() -> Selector.and(Selector.packageRegex("org")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selector.and()");
    }

    @Test
    @DisplayName("and(List) rejects null list")
    void andListRejectsNullList() {
        assertThatThrownBy(() -> Selector.and((List<Selector>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("and(List) requires at least 2 selectors")
    void andListRequiresAtLeast2() {
        assertThatThrownBy(() -> Selector.and(List.of(Selector.packageRegex("org"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selector.and()");
    }

    @Test
    @DisplayName("or(varargs) rejects null array")
    void orVarargsRejectsNullArray() {
        assertThatThrownBy(() -> Selector.or((Selector[]) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("or(varargs) rejects null element")
    void orVarargsRejectsNullElement() {
        assertThatThrownBy(() -> Selector.or(Selector.tagRegex("smoke"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("or(varargs) requires at least 2 selectors")
    void orVarargsRequiresAtLeast2() {
        assertThatThrownBy(() -> Selector.or(Selector.tagRegex("smoke")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selector.or()");
    }

    @Test
    @DisplayName("or(List) requires at least 2 selectors")
    void orListRequiresAtLeast2() {
        assertThatThrownBy(() -> Selector.or(List.of(Selector.tagRegex("smoke"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selector.or()");
    }

    @Test
    @DisplayName("not() rejects null")
    void notRejectsNull() {
        assertThatThrownBy(() -> Selector.not(null)).isInstanceOf(NullPointerException.class);
    }
}

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

@DisplayName("Selector.and()")
class AndSelectorTest {

    @Test
    @DisplayName("matches when all selectors match")
    void matchesWhenAllSelectorsMatch() {
        var selector = Selector.and(Selector.packageRegex("org\\.paramixel"), Selector.tagRegex("smoke"));

        assertThat(selector.matchesPackage("org.paramixel.api")).isTrue();
        assertThat(selector.matchesTag("smoke")).isTrue();
    }

    @Test
    @DisplayName("does not match when one selector does not match")
    void doesNotMatchWhenOneSelectorDoesNotMatch() {
        var selector = Selector.and(Selector.packageRegex("com\\.example"), Selector.tagRegex("smoke"));

        assertThat(selector.matchesPackage("com.example")).isTrue();
        assertThat(selector.matchesTag("integration")).isFalse();
    }

    @Test
    @DisplayName("creates AndSelector from varargs")
    void createsAndSelectorFromVarargs() {
        var selector = Selector.and(Selector.packageRegex("org"), Selector.tagRegex("smoke"));

        assertThat(selector).isInstanceOf(AndSelector.class);
        assertThat(((AndSelector) selector).selectors()).hasSize(2);
    }

    @Test
    @DisplayName("creates AndSelector from List")
    void createsAndSelectorFromList() {
        var selector = Selector.and(List.of(Selector.packageRegex("org"), Selector.tagRegex("smoke")));

        assertThat(selector).isInstanceOf(AndSelector.class);
        assertThat(((AndSelector) selector).selectors()).hasSize(2);
    }

    @Test
    @DisplayName("requires at least 2 selectors (varargs)")
    void requiresAtLeast2Varargs() {
        assertThatThrownBy(() -> Selector.and(Selector.packageRegex("org")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selector.and()")
                .hasMessageContaining("at least 2");
    }

    @Test
    @DisplayName("requires at least 2 selectors (List)")
    void requiresAtLeast2List() {
        assertThatThrownBy(() -> Selector.and(List.of(Selector.packageRegex("org"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selector.and()");
    }

    @Test
    @DisplayName("rejects null varargs array")
    void rejectsNullVarargsArray() {
        assertThatThrownBy(() -> Selector.and((Selector[]) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects null element in varargs")
    void rejectsNullElementInVarargs() {
        assertThatThrownBy(() -> Selector.and(Selector.packageRegex("org"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects null List")
    void rejectsNullList() {
        assertThatThrownBy(() -> Selector.and((List<Selector>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("flattens nested AND selectors")
    void flattensNestedAndSelectors() {
        var inner = Selector.and(Selector.packageRegex("org"), Selector.tagRegex("smoke"));
        var outer = Selector.and(inner, Selector.classRegex("Test"));

        assertThat(outer).isInstanceOf(AndSelector.class);
        assertThat(((AndSelector) outer).selectors()).hasSize(3);
    }

    @Test
    @DisplayName("does not flatten OR selectors inside AND")
    void doesNotFlattenOrInsideAnd() {
        var orInner = Selector.or(Selector.tagRegex("smoke"), Selector.tagRegex("integration"));
        var selector = Selector.and(orInner, Selector.packageRegex("org"));

        assertThat(selector).isInstanceOf(AndSelector.class);
        assertThat(((AndSelector) selector).selectors()).hasSize(2);
    }

    @Test
    @DisplayName("allows duplicate selectors")
    void allowsDuplicateSelectors() {
        var selector = Selector.and(Selector.tagRegex("smoke"), Selector.tagRegex("smoke"));

        assertThat(selector).isInstanceOf(AndSelector.class);
        assertThat(((AndSelector) selector).selectors()).hasSize(2);
    }

    @Test
    @DisplayName("getSelectors returns unmodifiable list")
    void getSelectorsReturnsUnmodifiableList() {
        var selector = Selector.and(Selector.packageRegex("org"), Selector.tagRegex("smoke"));

        assertThatThrownBy(() -> ((AndSelector) selector).selectors().add(Selector.all()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("AND semantics for matchesPackage")
    void andSemanticsForMatchesPackage() {
        var selector = Selector.and(Selector.packageRegex("org"), Selector.packageRegex("paramixel"));

        assertThat(selector.matchesPackage("org.paramixel")).isTrue();
        assertThat(selector.matchesPackage("org.example")).isFalse();
    }

    @Test
    @DisplayName("AND semantics for matchesClass")
    void andSemanticsForMatchesClass() {
        var selector = Selector.and(Selector.classRegex("Test"), Selector.classRegex("Selector"));

        assertThat(selector.matchesClass("TestSelector")).isTrue();
        assertThat(selector.matchesClass("TestRunner")).isFalse();
    }

    @Test
    @DisplayName("AND semantics for matchesTag")
    void andSemanticsForMatchesTag() {
        var selector = Selector.and(Selector.tagRegex("smoke"), Selector.tagRegex("fast"));

        assertThat(selector.matchesTag("smoke-fast")).isTrue();
        assertThat(selector.matchesTag("smoke")).isFalse();
    }
}

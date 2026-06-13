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

@DisplayName("Selector.or()")
class OrSelectorTest {

    @Test
    @DisplayName("matches when any selector matches")
    void matchesWhenAnySelectorMatches() {
        var selector = Selector.or(Selector.tagRegex("smoke"), Selector.tagRegex("integration"));

        assertThat(selector.matchesTag("smoke")).isTrue();
        assertThat(selector.matchesTag("integration")).isTrue();
        assertThat(selector.matchesTag("e2e")).isFalse();
    }

    @Test
    @DisplayName("does not match when no selector matches")
    void doesNotMatchWhenNoSelectorMatches() {
        var selector = Selector.or(Selector.tagRegex("smoke"), Selector.tagRegex("integration"));

        assertThat(selector.matchesTag("e2e")).isFalse();
    }

    @Test
    @DisplayName("creates OrSelector from varargs")
    void createsOrSelectorFromVarargs() {
        var selector = Selector.or(Selector.tagRegex("smoke"), Selector.tagRegex("integration"));

        assertThat(selector).isInstanceOf(OrSelector.class);
        assertThat(((OrSelector) selector).selectors()).hasSize(2);
    }

    @Test
    @DisplayName("creates OrSelector from List")
    void createsOrSelectorFromList() {
        var selector = Selector.or(List.of(Selector.tagRegex("smoke"), Selector.tagRegex("integration")));

        assertThat(selector).isInstanceOf(OrSelector.class);
        assertThat(((OrSelector) selector).selectors()).hasSize(2);
    }

    @Test
    @DisplayName("requires at least 2 selectors (varargs)")
    void requiresAtLeast2Varargs() {
        assertThatThrownBy(() -> Selector.or(Selector.tagRegex("smoke")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selector.or()")
                .hasMessageContaining("at least 2");
    }

    @Test
    @DisplayName("requires at least 2 selectors (List)")
    void requiresAtLeast2List() {
        assertThatThrownBy(() -> Selector.or(List.of(Selector.tagRegex("smoke"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selector.or()");
    }

    @Test
    @DisplayName("rejects null varargs array")
    void rejectsNullVarargsArray() {
        assertThatThrownBy(() -> Selector.or((Selector[]) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects null element in varargs")
    void rejectsNullElementInVarargs() {
        assertThatThrownBy(() -> Selector.or(Selector.tagRegex("smoke"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects null List")
    void rejectsNullList() {
        assertThatThrownBy(() -> Selector.or((List<Selector>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("flattens nested OR selectors")
    void flattensNestedOrSelectors() {
        var inner = Selector.or(Selector.tagRegex("smoke"), Selector.tagRegex("integration"));
        var outer = Selector.or(inner, Selector.tagRegex("e2e"));

        assertThat(outer).isInstanceOf(OrSelector.class);
        assertThat(((OrSelector) outer).selectors()).hasSize(3);
    }

    @Test
    @DisplayName("does not flatten AND selectors inside OR")
    void doesNotFlattenAndInsideOr() {
        var andInner = Selector.and(Selector.packageRegex("org"), Selector.tagRegex("smoke"));
        var selector = Selector.or(andInner, Selector.tagRegex("integration"));

        assertThat(selector).isInstanceOf(OrSelector.class);
        assertThat(((OrSelector) selector).selectors()).hasSize(2);
    }

    @Test
    @DisplayName("allows duplicate selectors")
    void allowsDuplicateSelectors() {
        var selector = Selector.or(Selector.tagRegex("smoke"), Selector.tagRegex("smoke"));

        assertThat(selector).isInstanceOf(OrSelector.class);
        assertThat(((OrSelector) selector).selectors()).hasSize(2);
    }

    @Test
    @DisplayName("getSelectors returns unmodifiable list")
    void getSelectorsReturnsUnmodifiableList() {
        var selector = Selector.or(Selector.tagRegex("smoke"), Selector.tagRegex("integration"));

        assertThatThrownBy(() -> ((OrSelector) selector).selectors().add(Selector.all()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("OR semantics for tag-only selectors: any match returns true")
    void orSemanticsForTagOnlySelectors() {
        var selector = Selector.or(Selector.tagRegex("smoke"), Selector.tagRegex("integration"));

        assertThat(selector.matchesTag("smoke")).isTrue();
        assertThat(selector.matchesTag("integration")).isTrue();
        assertThat(selector.matchesTag("e2e")).isFalse();
    }

    @Test
    @DisplayName("OR semantics for matchesPackage")
    void orSemanticsForMatchesPackage() {
        var selector = Selector.or(Selector.packageRegex("com\\.example"), Selector.packageRegex("org\\.paramixel"));

        assertThat(selector.matchesPackage("com.example")).isTrue();
        assertThat(selector.matchesPackage("org.paramixel")).isTrue();
        assertThat(selector.matchesPackage("net.other")).isFalse();
    }

    @Test
    @DisplayName("OR semantics for matchesClass")
    void orSemanticsForMatchesClass() {
        var selector = Selector.or(Selector.classRegex("TestA"), Selector.classRegex("TestB"));

        assertThat(selector.matchesClass("com.example.TestA")).isTrue();
        assertThat(selector.matchesClass("com.example.TestB")).isTrue();
        assertThat(selector.matchesClass("com.example.TestC")).isFalse();
    }
}

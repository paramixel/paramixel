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

@DisplayName("Selector.not()")
class NotSelectorTest {

    @Test
    @DisplayName("negates matchesPackage")
    void negatesMatchesPackage() {
        var selector = Selector.not(Selector.packageRegex("org\\.paramixel"));

        assertThat(selector.matchesPackage("com.example")).isTrue();
        assertThat(selector.matchesPackage("org.paramixel")).isFalse();
    }

    @Test
    @DisplayName("negates matchesClass")
    void negatesMatchesClass() {
        var selector = Selector.not(Selector.classRegex("com\\.example\\.Test"));

        assertThat(selector.matchesClass("com.example.Runner")).isTrue();
        assertThat(selector.matchesClass("com.example.Test")).isFalse();
    }

    @Test
    @DisplayName("negates matchesTag")
    void negatesMatchesTag() {
        var selector = Selector.not(Selector.tagRegex("smoke"));

        assertThat(selector.matchesTag("integration")).isTrue();
        assertThat(selector.matchesTag("smoke")).isFalse();
    }

    @Test
    @DisplayName("strictly negates all three matches* methods")
    void strictlyNegatesAllThreeMethods() {
        var packageSelector = Selector.packageRegex("org\\.paramixel");
        var notSelector = Selector.not(packageSelector);

        assertThat(notSelector.matchesPackage("org.paramixel.api"))
                .isEqualTo(!packageSelector.matchesPackage("org.paramixel.api"));
        assertThat(notSelector.matchesPackage("com.example")).isEqualTo(!packageSelector.matchesPackage("com.example"));
        assertThat(notSelector.matchesClass("org.paramixel.api.SelectorTest"))
                .isEqualTo(!packageSelector.matchesClass("org.paramixel.api.SelectorTest"));
        assertThat(notSelector.matchesTag("smoke")).isEqualTo(!packageSelector.matchesTag("smoke"));
    }

    @Test
    @DisplayName("preserves double negation (no simplification)")
    void preservesDoubleNegation() {
        var selector = Selector.not(Selector.not(Selector.tagRegex("smoke")));

        assertThat(selector).isInstanceOf(NotSelector.class);
        assertThat(((NotSelector) selector).selector()).isInstanceOf(NotSelector.class);
    }

    @Test
    @DisplayName("double negation preserves semantics")
    void doubleNegationPreservesSemantics() {
        var tagSelector = Selector.tagRegex("smoke");
        var doubleNegated = Selector.not(Selector.not(tagSelector));

        assertThat(doubleNegated.matchesTag("smoke")).isTrue();
        assertThat(doubleNegated.matchesTag("integration")).isFalse();
    }

    @Test
    @DisplayName("not(all()) matches nothing")
    void notAllMatchesNothing() {
        var selector = Selector.not(Selector.all());

        assertThat(selector.matchesPackage("org.paramixel")).isFalse();
        assertThat(selector.matchesClass("org.paramixel.api.SelectorTest")).isFalse();
        assertThat(selector.matchesTag("smoke")).isFalse();
    }

    @Test
    @DisplayName("rejects null selector")
    void rejectsNullSelector() {
        assertThatThrownBy(() -> Selector.not(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("is instance of NotSelector")
    void isInstanceOfNotSelector() {
        var selector = Selector.not(Selector.tagRegex("smoke"));

        assertThat(selector).isInstanceOf(NotSelector.class);
        assertThat(((NotSelector) selector).selector()).isInstanceOf(TagRegexSelector.class);
    }

    @Test
    @DisplayName("no De Morgan transformation: not(and(a, b)) is preserved")
    void noDeMorganTransformation() {
        var andSelector = Selector.and(Selector.packageRegex("org"), Selector.tagRegex("smoke"));
        var notSelector = Selector.not(andSelector);

        assertThat(notSelector).isInstanceOf(NotSelector.class);
        assertThat(((NotSelector) notSelector).selector()).isSameAs(andSelector);
    }
}

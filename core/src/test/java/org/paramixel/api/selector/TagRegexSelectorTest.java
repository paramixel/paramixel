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

@DisplayName("TagRegexSelector")
class TagRegexSelectorTest {

    @Test
    @DisplayName("getPattern returns the compiled pattern")
    void getPatternReturnsCompiledPattern() {
        TagRegexSelector selector = (TagRegexSelector) Selector.tagRegex("smoke");

        assertThat(selector.pattern()).isNotNull();
        assertThat(selector.pattern().pattern()).isEqualTo("smoke");
    }

    @Test
    @DisplayName("matchesTag uses find() semantics")
    void matchesTagUsesFindSemantics() {
        TagRegexSelector selector = (TagRegexSelector) Selector.tagRegex("smoke");

        assertThat(selector.matchesTag("smoke")).isTrue();
        assertThat(selector.matchesTag("smoke-test")).isTrue();
        assertThat(selector.matchesTag("integration")).isFalse();
    }

    @Test
    @DisplayName("matchesPackage returns true")
    void matchesPackageReturnsTrue() {
        TagRegexSelector selector = (TagRegexSelector) Selector.tagRegex("smoke");

        assertThat(selector.matchesPackage("org.paramixel.api")).isTrue();
    }

    @Test
    @DisplayName("matchesClass returns true")
    void matchesClassReturnsTrue() {
        TagRegexSelector selector = (TagRegexSelector) Selector.tagRegex("smoke");

        assertThat(selector.matchesClass("org.paramixel.api.SelectorTest")).isTrue();
    }

    @Test
    @DisplayName("matchesTag rejects null")
    void matchesTagRejectsNull() {
        TagRegexSelector selector = (TagRegexSelector) Selector.tagRegex("smoke");

        assertThatThrownBy(() -> selector.matchesTag(null)).isInstanceOf(NullPointerException.class);
    }
}

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

@DisplayName("ClassRegexSelector")
class ClassRegexSelectorTest {

    @Test
    @DisplayName("getPattern returns the compiled pattern")
    void getPatternReturnsCompiledPattern() {
        ClassRegexSelector selector = (ClassRegexSelector) Selector.classRegex("SelectorTest");

        assertThat(selector.pattern()).isNotNull();
        assertThat(selector.pattern().pattern()).isEqualTo("SelectorTest");
    }

    @Test
    @DisplayName("matchesClass uses find() semantics")
    void matchesClassUsesFindSemantics() {
        ClassRegexSelector selector = (ClassRegexSelector) Selector.classRegex("SelectorTest");

        assertThat(selector.matchesClass("org.paramixel.api.SelectorTest")).isTrue();
    }

    @Test
    @DisplayName("matchesPackage returns true")
    void matchesPackageReturnsTrue() {
        ClassRegexSelector selector = (ClassRegexSelector) Selector.classRegex("SelectorTest");

        assertThat(selector.matchesPackage("org.paramixel.api")).isTrue();
    }

    @Test
    @DisplayName("matchesTag returns true")
    void matchesTagReturnsTrue() {
        ClassRegexSelector selector = (ClassRegexSelector) Selector.classRegex("SelectorTest");

        assertThat(selector.matchesTag("smoke")).isTrue();
    }

    @Test
    @DisplayName("matchesClass rejects null")
    void matchesClassRejectsNull() {
        ClassRegexSelector selector = (ClassRegexSelector) Selector.classRegex("Test");

        assertThatThrownBy(() -> selector.matchesClass(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("classOf produces ClassRegexSelector")
    void classOfProducesClassRegexSelector() {
        Selector selector = Selector.classOf(ClassRegexSelectorTest.class);

        assertThat(selector).isInstanceOf(ClassRegexSelector.class);
    }
}

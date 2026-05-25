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

@DisplayName("PackageRegexSelector")
class PackageRegexSelectorTest {

    @Test
    @DisplayName("getPattern returns the compiled pattern")
    void getPatternReturnsCompiledPattern() {
        PackageRegexSelector selector = (PackageRegexSelector) Selector.packageRegex("org\\.paramixel");

        assertThat(selector.pattern()).isNotNull();
        assertThat(selector.pattern().pattern()).isEqualTo("org\\.paramixel");
    }

    @Test
    @DisplayName("matchesPackage uses find() semantics")
    void matchesPackageUsesFindSemantics() {
        PackageRegexSelector selector = (PackageRegexSelector) Selector.packageRegex("paramixel");

        assertThat(selector.matchesPackage("org.paramixel.api")).isTrue();
    }

    @Test
    @DisplayName("matchesClass returns true")
    void matchesClassReturnsTrue() {
        PackageRegexSelector selector = (PackageRegexSelector) Selector.packageRegex("org\\.paramixel");

        assertThat(selector.matchesClass("org.paramixel.api.SelectorTest")).isTrue();
    }

    @Test
    @DisplayName("matchesTag returns true")
    void matchesTagReturnsTrue() {
        PackageRegexSelector selector = (PackageRegexSelector) Selector.packageRegex("org\\.paramixel");

        assertThat(selector.matchesTag("smoke")).isTrue();
    }

    @Test
    @DisplayName("matchesPackage rejects null")
    void matchesPackageRejectsNull() {
        PackageRegexSelector selector = (PackageRegexSelector) Selector.packageRegex("org");

        assertThatThrownBy(() -> selector.matchesPackage(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("packageTreeOf produces PackageRegexSelector")
    void packageTreeOfProducesPackageRegexSelector() {
        Selector selector = Selector.packageTreeOf(PackageRegexSelectorTest.class);

        assertThat(selector).isInstanceOf(PackageRegexSelector.class);
    }

    @Test
    @DisplayName("packageOf produces PackageRegexSelector")
    void packageOfProducesPackageRegexSelector() {
        Selector selector = Selector.packageOf(PackageRegexSelectorTest.class);

        assertThat(selector).isInstanceOf(PackageRegexSelector.class);
    }
}

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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Step;
import org.paramixel.api.selector.Selector;

@DisplayName("Runner discovery arguments")
class RunnerDiscoveryArgumentsTest {

    @Test
    @DisplayName("rejects blank regex")
    void rejectsBlankRegex() {
        assertThatThrownBy(() -> Selector.tagRegex(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tagRegex regex is blank");
    }

    @Test
    @DisplayName("rejects invalid regex")
    void rejectsInvalidRegex() {
        assertThatThrownBy(() -> Selector.packageRegex("["))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("packageRegex");
    }

    @Test
    @DisplayName("and() composes package and class regex selectors")
    void andComposesPackageAndClassRegex() {
        var selector = Selector.and(
                Selector.packageRegex(RunnerDiscoveryArgumentsTest.class.getPackageName()),
                Selector.classRegex("RunnerDiscoveryArgumentsTest"));

        assertThat(selector.matchesPackage(RunnerDiscoveryArgumentsTest.class.getPackageName()))
                .isTrue();
        assertThat(selector.matchesClass(RunnerDiscoveryArgumentsTest.class.getName()))
                .isTrue();
        assertThat(selector.matchesClass(ActionResolverSmokeFixture.class.getName()))
                .isFalse();
    }

    @Test
    @DisplayName("packageTreeOf matches package and subpackages")
    void packageTreeOfMatchesPackageAndSubpackages() {
        var selector = Selector.packageTreeOf(RunnerDiscoveryArgumentsTest.class);

        assertThat(selector.matchesPackage(RunnerDiscoveryArgumentsTest.class.getPackageName()))
                .isTrue();
        assertThat(selector.matchesPackage(Step.class.getPackageName())).isTrue();
    }

    @Test
    @DisplayName("packageOf (exact) matches exact package only")
    void packageOfExactMatchesExactPackageOnly() {
        var selector = Selector.packageOf(RunnerDiscoveryArgumentsTest.class);

        assertThat(selector.matchesPackage(RunnerDiscoveryArgumentsTest.class.getPackageName()))
                .isTrue();
        assertThat(selector.matchesPackage(Step.class.getPackageName())).isFalse();
    }

    @Test
    @DisplayName("classOf matches exact class")
    void classOfMatchesExactClass() {
        var selector = Selector.classOf(ActionResolverSmokeFixture.class);

        assertThat(selector.matchesClass(ActionResolverSmokeFixture.class.getName()))
                .isTrue();
        assertThat(selector.matchesClass(ActionResolverMultiTagFixture.class.getName()))
                .isFalse();
    }

    @Test
    @DisplayName("packageTreeOf rejects null")
    void packageTreeOfRejectsNull() {
        assertThatThrownBy(() -> Selector.packageTreeOf(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clazz is null");
    }

    @Test
    @DisplayName("classOf rejects null")
    void classOfRejectsNull() {
        assertThatThrownBy(() -> Selector.classOf(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clazz is null");
    }

    @Test
    @DisplayName("run(Selector) rejects null selector")
    void runSelectorRejectsNullSelector() {
        assertThatThrownBy(() -> Runner.builder().build().run((Selector) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selector is null");
    }

    @Test
    @DisplayName("matchesPackage rejects null")
    void matchesPackageRejectsNull() {
        var selector = Selector.packageRegex("test");
        assertThatThrownBy(() -> selector.matchesPackage(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("packageName is null");
    }

    @Test
    @DisplayName("matchesClass rejects null")
    void matchesClassRejectsNull() {
        var selector = Selector.classRegex("test");
        assertThatThrownBy(() -> selector.matchesClass(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("className is null");
    }

    @Test
    @DisplayName("matchesTag rejects null")
    void matchesTagRejectsNull() {
        var selector = Selector.tagRegex("test");
        assertThatThrownBy(() -> selector.matchesTag(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tag is null");
    }
}

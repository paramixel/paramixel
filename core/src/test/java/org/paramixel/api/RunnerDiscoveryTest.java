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

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.selector.Selector;

@DisplayName("Runner discovery")
class RunnerDiscoveryTest {

    @Test
    @DisplayName("run(Selector) honors class and tag selector")
    void runSelectorHonorsClassAndTagSelector() {
        Selector selector =
                Selector.and(Selector.classRegex("ClasspathResolverSmokeFixture"), Selector.tagRegex("^smoke$"));

        Optional<Result> result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().descriptor()).isPresent();
    }

    @Test
    @DisplayName("run(Selector) honors package selector")
    void runSelectorHonorsPackageSelector() {
        Selector selector =
                Selector.and(Selector.packageRegex("^org\\.paramixel\\.api$"), Selector.tagRegex("^smoke$"));

        Optional<Result> result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().descriptor()).isPresent();
    }

    @Test
    @DisplayName("run(Selector) applies selector tag filter")
    void runSelectorAppliesTagFilter() {
        Selector selector =
                Selector.and(Selector.classRegex("ClasspathResolverMultiTagFixture"), Selector.tagRegex("smoke"));

        Optional<Result> result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().descriptor()).isPresent();
    }

    @Test
    @DisplayName("returns empty when selector tag filter excludes all matches")
    void returnsEmptyWhenSelectorTagFilterExcludesAllMatches() {
        Selector selector =
                Selector.and(Selector.classRegex("ClasspathResolverSmokeFixture"), Selector.tagRegex("nomatch"));

        assertThat(Runner.builder().build().run(selector)).isEmpty();
    }

    @Test
    @DisplayName("supports selector-only classOf with tagRegex")
    void runSelectorSupportsClassOfWithTagRegex() {
        Selector selector =
                Selector.and(Selector.classOf(ClasspathResolverSmokeFixture.class), Selector.tagRegex("^smoke$"));

        Optional<Result> result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().descriptor()).isPresent();
    }

    @Test
    @DisplayName("orders resolved actions by discovery metadata")
    void ordersResolvedActionsByDiscoveryMetadata() {
        Selector selector = Selector.classRegex("ClasspathResolverOrdering");
        Optional<Result> result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().descriptor().orElseThrow().children())
                .extracting(child -> child.metadata().name())
                .containsExactly("high", "alpha", "beta", "negative");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for invalid tagRegex")
    void throwsIllegalArgumentExceptionForInvalidTagRegex() {
        assertThatThrownBy(() -> Selector.tagRegex("["))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid regex");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for invalid classRegex")
    void throwsIllegalArgumentExceptionForInvalidClassRegex() {
        assertThatThrownBy(() -> Selector.classRegex("["))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid regex");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for invalid packageRegex")
    void throwsIllegalArgumentExceptionForInvalidPackageRegex() {
        assertThatThrownBy(() -> Selector.packageRegex("["))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid regex");
    }

    @Test
    @DisplayName("continues after blank tag value during classpath discovery")
    void continuesAfterBlankTagValueDuringClasspathDiscovery() {
        Selector selector = Selector.classRegex(
                "ClasspathResolverValidTaggedDiscoveryFixture|ClasspathResolverInvalidBlankTagDiscoveryFixture");

        Optional<Result> result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        var children = result.orElseThrow().descriptor().orElseThrow().children();
        assertThat(children).hasSize(2);
        assertThat(children)
                .anySatisfy(child -> assertThat(child.metadata().name()).isEqualTo("valid-tagged-discovery"));
        assertThat(children)
                .anySatisfy(child -> assertThat(child.metadata().name()).startsWith("Discovery validation failure:"));
    }

    @Test
    @DisplayName("aggregates multiple blank tag values")
    void aggregatesMultipleBlankTagValues() {
        Selector selector = Selector.classRegex("ClasspathResolverValidTaggedDiscoveryFixture"
                + "|ClasspathResolverInvalidBlankTagDiscoveryFixture"
                + "|ClasspathResolverAnotherInvalidBlankTagDiscoveryFixture");

        Optional<Result> result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        var children = result.orElseThrow().descriptor().orElseThrow().children();
        assertThat(children)
                .anySatisfy(child -> assertThat(child.metadata().name())
                        .contains("ClasspathResolverInvalidBlankTagDiscoveryFixture#factory"));
        assertThat(children)
                .anySatisfy(child -> assertThat(child.metadata().name())
                        .contains("ClasspathResolverAnotherInvalidBlankTagDiscoveryFixture#factory"));
    }
}

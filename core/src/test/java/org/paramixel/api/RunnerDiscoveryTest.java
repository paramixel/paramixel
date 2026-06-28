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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.selector.Selector;

@DisplayName("Runner discovery")
class RunnerDiscoveryTest {

    @Test
    @DisplayName("run(Selector) honors class and tag selector")
    void runSelectorHonorsClassAndTagSelector() {
        var selector =
                Selector.and(Selector.classRegex(".*ActionResolverSmokeFixture.*"), Selector.tagRegex("^smoke$"));

        var result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().descriptor()).isPresent();
    }

    @Test
    @DisplayName("run(Selector) honors package selector")
    void runSelectorHonorsPackageSelector() {
        var selector = Selector.and(Selector.packageRegex("^org\\.paramixel\\.api$"), Selector.tagRegex("^smoke$"));

        var result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().descriptor()).isPresent();
    }

    @Test
    @DisplayName("run(Selector) applies selector tag filter")
    void runSelectorAppliesTagFilter() {
        var selector =
                Selector.and(Selector.classRegex(".*ActionResolverMultiTagFixture.*"), Selector.tagRegex("smoke-fast"));

        var result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().descriptor()).isPresent();
    }

    @Test
    @DisplayName("returns empty when selector tag filter excludes all matches")
    void returnsEmptyWhenSelectorTagFilterExcludesAllMatches() {
        var selector =
                Selector.and(Selector.classRegex(".*ActionResolverSmokeFixture.*"), Selector.tagRegex("nomatch"));

        assertThat(Runner.builder().build().run(selector)).isEmpty();
    }

    @Test
    @DisplayName("supports selector-only classOf with tagRegex")
    void runSelectorSupportsClassOfWithTagRegex() {
        var selector = Selector.and(Selector.classOf(ActionResolverSmokeFixture.class), Selector.tagRegex("^smoke$"));

        var result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().descriptor()).isPresent();
    }

    @Test
    @DisplayName("orders resolved actions by discovery metadata")
    void ordersResolvedActionsByDiscoveryMetadata() {
        var selector = Selector.classRegex(".*ActionResolverOrdering.*");
        var result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().descriptor().orElseThrow().children())
                .extracting(child -> child.action().displayName())
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
        var selector = Selector.classRegex(
                ".*ActionResolverValidTaggedDiscoveryFixture.*|.*ActionResolverInvalidBlankTagDiscoveryFixture.*");

        var result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        var children = result.orElseThrow().descriptor().orElseThrow().children();
        assertThat(children).hasSize(2);
        assertThat(children)
                .anySatisfy(child -> assertThat(child.action().displayName()).isEqualTo("valid-tagged-discovery"));
        assertThat(children)
                .anySatisfy(
                        child -> assertThat(child.action().displayName()).startsWith("Discovery validation failure:"));
    }

    @Test
    @DisplayName("aggregates multiple blank tag values")
    void aggregatesMultipleBlankTagValues() {
        var selector = Selector.classRegex(".*ActionResolverValidTaggedDiscoveryFixture.*"
                + "|.*ActionResolverInvalidBlankTagDiscoveryFixture.*"
                + "|.*ActionResolverAnotherInvalidBlankTagDiscoveryFixture.*");

        var result = Runner.builder().build().run(selector);

        assertThat(result).isPresent();
        var children = result.orElseThrow().descriptor().orElseThrow().children();
        assertThat(children)
                .anySatisfy(child -> assertThat(child.action().displayName())
                        .contains("ActionResolverInvalidBlankTagDiscoveryFixture#factory"));
        assertThat(children)
                .anySatisfy(child -> assertThat(child.action().displayName())
                        .contains("ActionResolverAnotherInvalidBlankTagDiscoveryFixture#factory"));
    }

    @Test
    @DisplayName("run() class regex filters by class name")
    void runClassRegexFiltersByClassName() {
        var configuration = Configuration.of(Map.of(Configuration.MATCH_CLASS_REGEX, ".*ActionResolverSmokeFixture.*"));

        int exitCode = Runner.builder().configuration(configuration).build().run();

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("run() package regex filters by package name")
    void runPackageRegexFiltersByPackage() {
        var configuration = Configuration.of(Map.of(
                Configuration.MATCH_PACKAGE_REGEX, "^org\\.paramixel\\.api$",
                Configuration.MATCH_CLASS_REGEX, ".*ActionResolverSmokeFixture.*"));

        int exitCode = Runner.builder().configuration(configuration).build().run();

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("run() tag regex filters by tag value")
    void runTagRegexFiltersByTag() {
        var configuration = Configuration.of(Map.of(
                Configuration.MATCH_CLASS_REGEX, ".*ActionResolverSmokeFixture.*",
                Configuration.MATCH_TAG_REGEX, "^smoke$"));

        int exitCode = Runner.builder().configuration(configuration).build().run();

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("run() combines multiple regex keys with AND (no match)")
    void runCombinesMultipleRegexKeysWithAndNoMatch() {
        var configuration = Configuration.of(Map.of(
                Configuration.MATCH_CLASS_REGEX, ".*ActionResolverSmokeFixture.*",
                Configuration.MATCH_TAG_REGEX, "NONEXISTENT",
                Configuration.FAIL_IF_NO_TESTS, "true"));

        var out = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            int exitCode = Runner.builder().configuration(configuration).build().run();
            assertThat(exitCode).isEqualTo(1);
        } finally {
            System.setOut(originalOut);
        }
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("No Paramixel tests found");
    }

    @Test
    @DisplayName("run() with only class regex is backward compatible")
    void runWithOnlyClassRegexIsBackwardCompatible() {
        var configuration = Configuration.of(Map.of(Configuration.MATCH_CLASS_REGEX, ".*ActionResolverSmokeFixture.*"));

        int exitCode = Runner.builder().configuration(configuration).build().run();

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("run() with no tests calls onRunStarted and onRunCompleted on listener")
    void runWithNoTestsCallsListenerCallbacks() {
        var calls = new ArrayList<String>();
        var capturedResult = new AtomicReference<Result>();
        var configuration = Configuration.of(Map.of(
                Configuration.MATCH_CLASS_REGEX, "nonexistent.ClassName",
                Configuration.FAIL_IF_NO_TESTS, "true"));

        var listener = new Listener() {

            @Override
            public void onRunStarted() {
                calls.add("onRunStarted");
            }

            @Override
            public void onRunCompleted(final Result result) {
                capturedResult.set(result);
                calls.add("onRunCompleted");
            }
        };

        int exitCode = Runner.builder()
                .configuration(configuration)
                .listener(listener)
                .build()
                .run();

        assertThat(exitCode).isEqualTo(1);
        assertThat(calls).containsExactly("onRunStarted", "onRunCompleted");
        var result = capturedResult.get();
        assertThat(result.descriptor()).isEmpty();
        assertThat(result.isFailed()).isTrue();
    }
}

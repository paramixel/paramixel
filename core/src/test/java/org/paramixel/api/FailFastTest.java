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

import java.util.Map;
import java.util.regex.Pattern;
import nonapi.org.paramixel.discovery.FactoryScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.discoveryfixtures.DiscoveryFixtures;
import org.paramixel.api.discoveryfixtures.FailFastTriggerFixture;
import org.paramixel.api.selector.Selector;

/**
 * Run-level fail-fast: only a failed root task stops admission of later roots; deliberate skips, false conditions and
 * assumption aborts do not.
 */
class FailFastTest {

    @BeforeEach
    void reset() {
        DiscoveryFixtures.reset();
    }

    /**
     * Runs the trigger factory followed by two followers through discovery, one factory at a time.
     *
     * @param mode how the trigger ends
     * @param failFast whether fail-fast is enabled
     * @return the result
     */
    private static Result run(final String mode, final boolean failFast) {
        DiscoveryFixtures.failFastMode = mode;
        final var selector = Selector.builder()
                .packageTreeOf(FailFastTriggerFixture.class)
                .classPattern(Pattern.compile(".*FailFast(Trigger|Follower1|Follower2)Fixture"))
                .build();
        return Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(
                        Map.of(Configuration.ANSI, "false", Configuration.FAIL_FAST, Boolean.toString(failFast))))
                .pool(Pool.named(FactoryScanner.DISCOVERY_POOL), 1)
                .build()
                .run(selector);
    }

    /**
     * Returns the discovered factory group: {@code Paramixel > run > actions}.
     *
     * @param result a discovery run result
     * @return the actions group
     */
    private static TaskResult actions(final Result result) {
        return result.root().orElseThrow().children().get(0).children().stream()
                .filter(child -> child.name().equals("actions"))
                .findFirst()
                .orElseThrow();
    }

    private static TaskResult factory(final Result result, final String name) {
        return actions(result).children().stream()
                .filter(child -> child.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("FAILFAST-01: a deliberately skipped root does not stop later roots")
    void skippedRootDoesNotStopLaterRoots() {
        final var result = run("skip", true);

        assertThat(factory(result, "trigger").status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(DiscoveryFixtures.RECORDED).contains("follower-1", "follower-2");
    }

    @Test
    @DisplayName("FAILFAST-02: a root whose condition is false does not stop later roots")
    void falseConditionRootDoesNotStopLaterRoots() {
        final var result = run("condition", true);

        assertThat(factory(result, "trigger").status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(DiscoveryFixtures.RECORDED).doesNotContain("trigger").contains("follower-1", "follower-2");
    }

    @Test
    @DisplayName("FAILFAST-03: an aborted root does not stop later roots")
    void abortedRootDoesNotStopLaterRoots() {
        final var result = run("abort", true);

        assertThat(factory(result, "trigger").status().kind()).isEqualTo(Status.Kind.ABORTED);
        assertThat(DiscoveryFixtures.RECORDED).contains("follower-1", "follower-2");
    }

    @Test
    @DisplayName("FAILFAST-04: a failed root stops every later root, each skipped with a fail-fast reason")
    void failedRootStopsLaterRoots() {
        final var result = run("fail", true);

        assertThat(factory(result, "trigger").status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(DiscoveryFixtures.RECORDED).contains("trigger").doesNotContain("follower-1", "follower-2");
        for (String follower : new String[] {"follower-1", "follower-2"}) {
            final var node = factory(result, follower);
            assertThat(node.status().kind()).isEqualTo(Status.Kind.SKIPPED);
            assertThat(node.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("failFast"));
        }
    }

    @Test
    @DisplayName("FAILFAST-05: a timed-out root is a failure and stops later roots")
    void timedOutRootStopsLaterRoots() {
        final var result = run("timeout", true);

        assertThat(factory(result, "trigger").status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(factory(result, "trigger").timedOut()).isTrue();
        assertThat(DiscoveryFixtures.RECORDED).contains("trigger").doesNotContain("follower-1", "follower-2");
    }

    @Test
    @DisplayName("FAILFAST-06: without fail-fast, a failed root does not stop later roots")
    void failedRootDoesNotStopLaterRootsWithoutFailFast() {
        final var result = run("fail", false);

        assertThat(factory(result, "trigger").status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(DiscoveryFixtures.RECORDED).contains("trigger", "follower-1", "follower-2");
    }
}

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
import static org.paramixel.api.Tasks.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import nonapi.org.paramixel.discovery.FactoryScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.discoveryfixtures.DiscoveryFixtures;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

/**
 * Factory discovery, hook behavior, and selection.
 */
class DiscoveryTest {

    /** Number of distinct shuffle seeds exercised by the reproducibility sweep. */
    private static final int SHUFFLE_SEED_SWEEP = 8;

    private static Selector fixtures() {
        return Selector.builder()
                .packageTreeOf(DiscoveryFixtures.class)
                .classPattern(Pattern.compile(
                        ".*(AlphaFixture|BetaFixture|HookFixture|SkippingFactoryFixture|DisabledFixture)"))
                .build();
    }

    private static Selector shuffleFixtures() {
        return Selector.builder()
                .packageTreeOf(DiscoveryFixtures.class)
                .classPattern(Pattern.compile(".*(AlphaFixture|BetaFixture|GammaFixture)"))
                .build();
    }

    private static Runner runner() {
        return Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.ANSI, "false")))
                .build();
    }

    private static Runner shuffled(final long seed) {
        return Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.ANSI, "false")))
                .shuffle(seed)
                .build();
    }

    /**
     * Returns the discovered factory group: {@code Paramixel > run > actions}.
     *
     * @param result a discovery run result
     * @return the actions group
     */
    private static TaskResult discoveryActions(final Result result) {
        return result.root().orElseThrow().children().get(0).children().get(1);
    }

    /**
     * Returns the discovered factory names in plan order. Plan order carries the shuffle order and does not depend on
     * which factory finishes first.
     *
     * @param result a discovery run result
     * @return the factory names in plan order
     */
    private static List<String> discoveredOrder(final Result result) {
        return discoveryActions(result).executionOrder();
    }

    @BeforeEach
    void reset() {
        DiscoveryFixtures.reset();
    }

    @Test
    @DisplayName("discovered factories run through the composed root with hooks")
    void discoveryComposesRoot() {
        final var result = runner().run(fixtures());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        // Discovered factories share a parallel pool, so only the hook boundaries and the participant set are defined.
        assertThat(DiscoveryFixtures.RECORDED).hasSize(4);
        assertThat(DiscoveryFixtures.RECORDED.get(0)).isEqualTo("beforeAll");
        assertThat(DiscoveryFixtures.RECORDED.get(3)).isEqualTo("afterAll");
        assertThat(DiscoveryFixtures.RECORDED.subList(1, 3)).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(result.root().orElseThrow().name()).isEqualTo("Paramixel");
    }

    @Test
    @DisplayName("factory priority orders the discovery group")
    void factoryPriorityOrdersAdmission() {
        // A single-permit discovery pool serializes the group, so admission order becomes execution order.
        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.ANSI, "false")))
                .pool(Pool.named(FactoryScanner.DISCOVERY_POOL), 1)
                .build()
                .run(fixtures());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(discoveredOrder(result)).containsExactly("beta", "alpha");
        assertThat(DiscoveryFixtures.RECORDED).containsExactly("beforeAll", "beta", "alpha", "afterAll");
    }

    @Test
    @DisplayName("display paths exclude framework scaffolding")
    void displayPathsExcludeScaffolding() {
        final var result = runner().run(fixtures());

        final var root = result.root().orElseThrow();
        assertThat(root.path()).isEqualTo("Paramixel");
        final var run = root.children().get(0);
        assertThat(run.path()).isEqualTo("run");
        final var actions = run.children().get(1);
        assertThat(actions.path()).isEqualTo("actions");
        final var factoryPaths =
                actions.children().stream().map(TaskResult::path).toList();
        assertThat(factoryPaths).contains("alpha", "beta");
        final var alphaStep = actions.children().stream()
                .filter(node -> node.name().equals("alpha"))
                .findFirst()
                .orElseThrow()
                .children()
                .get(0);
        assertThat(alphaStep.path()).isEqualTo("alpha > run");
        assertThat(factoryPaths).noneMatch(path -> path.startsWith("Paramixel > run > actions"));
    }

    @Test
    @DisplayName("a null factory is an explicit skip and does not fail the run")
    void nullFactoryIsSkipped() {
        final var result = runner().run(fixtures());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(DiscoveryFixtures.RECORDED).doesNotContain("skipping");
    }

    @Test
    @DisplayName("tag selection filters factories")
    void tagSelectionFilters() {
        final var selector = Selector.builder()
                .packageTreeOf(DiscoveryFixtures.class)
                .classPattern(Pattern.compile(".*(AlphaFixture|BetaFixture|HookFixture)"))
                .tagPattern(Pattern.compile("^alpha$"))
                .build();

        final var result = runner().run(selector);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(DiscoveryFixtures.RECORDED).contains("alpha").doesNotContain("beta");
    }

    @Test
    @DisplayName("a failing before-all hook skips the factories but after-all still runs")
    void beforeAllFailureStillRunsAfterAll() {
        DiscoveryFixtures.failBeforeAll = true;

        final var result = runner().run(fixtures());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(DiscoveryFixtures.RECORDED).contains("beforeAll", "afterAll").doesNotContain("alpha", "beta");
    }

    @Test
    @DisplayName("an invalid factory is rejected before any discovered work runs")
    void invalidFactoryIsRejected() {
        final var selector = Selector.builder()
                .classOf(org.paramixel.api.discoveryfixtures.InvalidFactoryFixture.class)
                .build();

        assertThatThrownBy(() -> runner().run(selector))
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("InvalidFactoryFixture");
        assertThat(DiscoveryFixtures.RECORDED).isEmpty();
    }

    @Test
    @DisplayName("direct execution never scans the classpath or runs hooks")
    void directRunSkipsDiscovery() {
        final var result = runner().run(task("direct").step("work", () -> {}).build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(DiscoveryFixtures.RECORDED).isEmpty();
    }

    @Test
    @DisplayName("discovering nothing reports skipped and fails by default")
    void noTestsFailsByDefault() {
        final var selector = Selector.builder().classOf(DiscoveryTest.class).build();

        final var result = runner().run(selector);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.root()).isEmpty();
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("No @Paramixel.Factory"));
    }

    @Test
    @DisplayName("discovering nothing passes when failIfNoTests is disabled")
    void noTestsCanPass() {
        final var runner = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(
                        Configuration.FAIL_IF_NO_TESTS, "false",
                        Configuration.FAILURE_ON_SKIP, "false")))
                .build();
        final var selector = Selector.builder().classOf(DiscoveryTest.class).build();

        final var result = runner.run(selector);

        assertThat(result.exitCode()).isZero();
        assertThat(result.root()).isEmpty();
    }

    @Test
    @DisplayName("discovery shuffling reorders factories, reproducibly, and reports the seed")
    void discoveryShuffleIsReproducible() {
        final var selector = shuffleFixtures();
        final var unshuffled = runner().run(selector);

        assertThat(unshuffled.shuffleSeed()).isEmpty();
        final var baseline = discoveredOrder(unshuffled);
        assertThat(baseline).containsExactly("beta", "alpha", "gamma");

        final var orders = new LinkedHashMap<Long, List<String>>();
        for (long seed = 0; seed < SHUFFLE_SEED_SWEEP; seed++) {
            final var result = shuffled(seed).run(selector);

            assertThat(result.shuffleSeed()).hasValue(seed);
            assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
            final var order = discoveredOrder(result);
            assertThat(order).containsExactlyInAnyOrderElementsOf(baseline);
            orders.put(seed, order);
        }

        // Reporting a seed while keeping priority order would make shuffling a no-op.
        final var reordered = orders.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(baseline))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no seed reordered the discovered factories: " + orders));

        // The same seed must reproduce the same order, so a reported seed can replay a run.
        assertThat(discoveredOrder(shuffled(reordered.getKey()).run(selector))).isEqualTo(reordered.getValue());
    }

    @Test
    @DisplayName("a small seed still reorders a two-factory group")
    void smallSeedReordersTwoFactories() {
        final var selector = fixtures();
        final var baseline = discoveredOrder(runner().run(selector));
        assertThat(baseline).containsExactly("beta", "alpha");

        final var orders = new ArrayList<List<String>>();
        for (long seed = 0; seed < SHUFFLE_SEED_SWEEP; seed++) {
            final var order = discoveredOrder(shuffled(seed).run(selector));
            assertThat(order).containsExactlyInAnyOrderElementsOf(baseline);
            orders.add(order);
        }

        // Two children reduce a shuffle to a single bounded draw. java.util.Random answers that draw from the high
        // bits of its first value, which are constant across small seeds, so every seed below 4096 kept priority
        // order; the sweep must reach both orders.
        assertThat(orders).anyMatch(order -> !order.equals(baseline));
    }

    @Test
    @DisplayName("concurrent runs on one runner are rejected")
    void concurrentRunsAreRejected() {
        final var runner = runner();
        final var started = new java.util.concurrent.CountDownLatch(1);
        final var release = new java.util.concurrent.CountDownLatch(1);
        final var definition = task("slow")
                .step("work", () -> {
                    started.countDown();
                    release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                })
                .build();

        final var thread = new Thread(() -> runner.run(definition));
        thread.start();
        try {
            assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> runner.run(definition))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already in progress");
            release.countDown();
            thread.join(5000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(thread.isAlive()).isFalse();
    }

    @Test
    @DisplayName("a closed runner rejects further runs")
    void closedRunnerRejectsRuns() {
        final var runner = runner();
        runner.close();

        assertThatThrownBy(() -> runner.run(task("x").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }
}

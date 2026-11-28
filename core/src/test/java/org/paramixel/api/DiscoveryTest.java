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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import nonapi.org.paramixel.discovery.FactoryScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.discoveryfixtures.AlphaFixture;
import org.paramixel.api.discoveryfixtures.CloseDuringDiscoveryFixture;
import org.paramixel.api.discoveryfixtures.DisabledConflictingFactoryFixture;
import org.paramixel.api.discoveryfixtures.DiscoveryFixtures;
import org.paramixel.api.discoveryfixtures.InvalidFactoryFixture;
import org.paramixel.api.discoveryfixtures.InvalidTagFixture;
import org.paramixel.api.discoveryfixtures.SkippingFactoryFixture;
import org.paramixel.api.exception.CompilationException;
import org.paramixel.api.exception.ConfigurationException;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

/**
 * Factory discovery, hook behavior, and selection.
 */
class DiscoveryTest {

    /** Number of distinct shuffle seeds exercised by the reproducibility sweep. */
    private static final int SHUFFLE_SEED_SWEEP = 8;

    private static final ConcurrentLinkedQueue<List<String>> REPEAT_TAGS = new ConcurrentLinkedQueue<>();
    private static final Task SHARED_TAGGED_TASK = Repeat.builder("shared")
            .maxIterations(2)
            .until((context, status) -> {
                REPEAT_TAGS.add(context.tags());
                return true;
            })
            .tags("local", "overlap")
            .step(Sequential.builder("child").tags("child").step("work", () -> {}))
            .build();

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
     * Returns the discovered factory group: {@code Paramixel > run > tasks}.
     *
     * @param result a discovery run result
     * @return the tasks group
     */
    private static TaskResult discoveryTasks(final Result result) {
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
        return discoveryTasks(result).executionOrder();
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
    void aFailingAfterAllHookDoesNotPreventTheNextAfterAllHookFromRunning() {
        DiscoveryFixtures.failAfterAll = true;

        final var result = runner().run(fixtures());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(DiscoveryFixtures.RECORDED).endsWith("failingAfterAll", "afterAll");
    }

    @Test
    @DisplayName("display paths exclude framework scaffolding")
    void displayPathsExcludeScaffolding() {
        final var result = runner().run(fixtures());

        final var root = result.root().orElseThrow();
        assertThat(root.path()).isEqualTo("Paramixel");
        final var run = root.children().get(0);
        assertThat(run.path()).isEqualTo("run");
        final var tasks = run.children().get(1);
        assertThat(tasks.path()).isEqualTo("tasks");
        final var factoryPaths = tasks.children().stream().map(TaskResult::path).toList();
        assertThat(factoryPaths).contains("alpha", "beta");
        final var alphaStep = tasks.children().stream()
                .filter(node -> node.name().equals("alpha"))
                .findFirst()
                .orElseThrow()
                .children()
                .get(0);
        assertThat(alphaStep.path()).isEqualTo("alpha > run");
        assertThat(factoryPaths).noneMatch(path -> path.startsWith("Paramixel > run > tasks"));
    }

    @Test
    @DisplayName("a null factory is an explicit skip and does not fail the run")
    void nullFactoryIsSkipped() {
        final var result = runner().run(fixtures());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(DiscoveryFixtures.RECORDED).doesNotContain("skipping");
    }

    @Test
    @DisplayName("a null factory records why it contributed nothing")
    void nullFactoryRecordsReason() {
        final var warnings = new ArrayList<String>();
        final var runner = Runner.builder()
                .listener(new Listener() {
                    @Override
                    public void onWarning(final String message) {
                        warnings.add(message);
                    }
                })
                .configuration(Configuration.of(Map.of(Configuration.ANSI, "false")))
                .build();

        final var result = runner.run(fixtures());

        final var location = SkippingFactoryFixture.class.getName() + "#factory";
        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(warnings)
                .anySatisfy(warning -> assertThat(warning).contains(location).contains("returned null"));
        assertThat(result.diagnostics())
                .anySatisfy(
                        diagnostic -> assertThat(diagnostic).contains(location).contains("returned null"));
    }

    @Test
    void onlyNullFactoriesFollowTheNoTestsPolicy() {
        final var selector =
                Selector.builder().classOf(SkippingFactoryFixture.class).build();
        for (boolean failIfNoTests : new boolean[] {true, false}) {
            final var configuration = Configuration.of(Map.of(
                    Configuration.FAIL_IF_NO_TESTS, Boolean.toString(failIfNoTests),
                    Configuration.FAILURE_ON_SKIP, Boolean.toString(!failIfNoTests)));
            try (var runner = Runner.builder()
                    .configuration(configuration)
                    .listener(Listener.NOOP)
                    .build()) {
                final var result = runner.run(selector);

                assertThat(result.status().kind()).isEqualTo(Status.Kind.SKIPPED);
                assertThat(result.root()).isEmpty();
                assertThat(result.exitCode()).isEqualTo(failIfNoTests ? 1 : 0);
                assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.contains("returned null"));
                assertThat(DiscoveryFixtures.RECORDED).isEmpty();
            }
        }
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
    @DisplayName("a factory inherited by a subclass is discovered and invoked once")
    void inheritedFactoryRunsOnce() {
        final var selector = Selector.builder()
                .packageTreeOf(DiscoveryFixtures.class)
                .classPattern(Pattern.compile(".*InheritedFactory(Base|Sub)Fixture"))
                .build();

        final var result = runner().run(selector);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(discoveredOrder(result)).containsExactly("inherited");
        assertThat(DiscoveryFixtures.RECORDED).containsOnlyOnce("inherited");
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
        final var selector =
                Selector.builder().classOf(InvalidFactoryFixture.class).build();

        assertThatThrownBy(() -> runner().run(selector))
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("InvalidFactoryFixture");
        assertThat(DiscoveryFixtures.RECORDED).isEmpty();
    }

    @Test
    @DisplayName("direct execution never scans the classpath or runs hooks")
    void directRunSkipsDiscovery() {
        final var result =
                runner().run(Sequential.builder("direct").step("work", () -> {}).build());

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
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("No @Paramixel.TaskFactory"));
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
    @DisplayName("FAIL-11: disabling failIfNoTests alone makes an empty discovery pass")
    void noTestsPassesWhenOnlyFailIfNoTestsIsDisabled() {
        final var runner = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(
                        Configuration.ANSI, "false",
                        Configuration.FAIL_IF_NO_TESTS, "false")))
                .build();
        final var selector = Selector.builder().classOf(DiscoveryTest.class).build();

        final var result = runner.run(selector);

        assertThat(result.exitCode()).isZero();
        assertThat(result.root()).isEmpty();
    }

    @Test
    @DisplayName("FAIL-11: an empty discovery fails when failIfNoTests is set even if skips do not fail")
    void noTestsFailsWhenFailIfNoTestsIsSetAndSkipsDoNotFail() {
        final var runner = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(
                        Configuration.ANSI, "false",
                        Configuration.FAIL_IF_NO_TESTS, "true",
                        Configuration.FAILURE_ON_SKIP, "false")))
                .build();
        final var selector = Selector.builder().classOf(DiscoveryTest.class).build();

        assertThat(runner.run(selector).exitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("closing a runner while its factories are being discovered cancels that run")
    void closeDuringDiscoveryCancelsRun() {
        final var runner = runner();
        DiscoveryFixtures.factoryHook = runner::close;
        final var selector =
                Selector.builder().classOf(CloseDuringDiscoveryFixture.class).build();

        final var result = runner.run(selector);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.CANCELED);
        assertThat(DiscoveryFixtures.RECORDED).doesNotContain("close-during-discovery");
    }

    @Test
    void closeDuringDiscoverySkipsTheNextFactory() {
        try (var runner = runner()) {
            DiscoveryFixtures.factoryHook = runner::close;
            final var result = runner.run(Selector.builder()
                    .classOf(CloseDuringDiscoveryFixture.class)
                    .classOf(LaterFactory.class)
                    .build());

            assertThat(result.status().isCanceled()).isTrue();
            assertThat(DiscoveryFixtures.RECORDED).doesNotContain("later-factory");
        }
    }

    @Test
    void canceledDiscoveryDiscardsValidAndInvalidDefinitions() {
        for (Class<?> fixture :
                List.of(CloseDuringDiscoveryFixture.class, CanceledPlanFactory.class, CanceledBuilderFactory.class)) {
            final var completed = new ArrayList<Optional<Task>>();
            try (var runner = Runner.builder()
                    .configuration(Configuration.of(Map.of(Configuration.FAIL_IF_NO_TESTS, "false")))
                    .listener(new Listener() {
                        @Override
                        public void onDiscoveryCompleted(final Optional<Task> root) {
                            completed.add(root);
                        }
                    })
                    .build()) {
                DiscoveryFixtures.factoryHook = runner::close;
                final var result =
                        runner.run(Selector.builder().classOf(fixture).build());
                assertThat(completed).containsExactly(Optional.empty());
                assertThat(result.root()).isEmpty();
                assertThat(result.status().isCanceled()).isTrue();
                assertThat(result.exitCode()).isEqualTo(1);
                assertThat(result.isQuiescent()).isTrue();
            }
        }
        assertThat(DiscoveryFixtures.RECORDED).isEmpty();
    }

    @Test
    void aProviderCompilationFailureStillSurfacesAfterClose() {
        try (var runner = runner()) {
            DiscoveryFixtures.factoryHook = runner::close;
            assertThatThrownBy(() -> runner.run(Selector.builder()
                            .classOf(ClosingThrowingFactory.class)
                            .build()))
                    .isInstanceOf(CompilationException.class)
                    .hasMessageContaining("provider compilation failure");
        }
    }

    @Test
    void factoryTagsFollowThePlacementThroughShuffleAndAttempts() {
        REPEAT_TAGS.clear();
        final var first = List.of("local", "overlap", "first");
        final var second = List.of("local", "overlap", "second");
        final var selector = Selector.builder()
                .classOf(FirstTaggedFactory.class)
                .classOf(SecondTaggedFactory.class)
                .tagPattern(Pattern.compile("first|second"))
                .build();
        try (var runner = shuffled(2)) {
            final var result = runner.run(selector);
            final var roots = discoveryTasks(result).children();
            assertThat(roots).extracting(TaskResult::tags).containsExactly(second, first);
            for (TaskResult root : roots) {
                assertThat(root.children()).singleElement().satisfies(attempt -> {
                    assertThat(attempt.tags()).isEqualTo(root.tags());
                    assertThat(attempt.children()).extracting(TaskResult::tags).containsExactly(List.of("child"));
                });
            }
            assertThat(REPEAT_TAGS).containsExactlyInAnyOrder(first, second);

            final var direct = runner.run(SHARED_TAGGED_TASK);
            assertThat(direct.root().orElseThrow().tags()).containsExactly("local", "overlap");
            assertThat(REPEAT_TAGS).contains(List.of("local", "overlap"));
        }
    }

    static final class CanceledPlanFactory {
        @Paramixel.TaskFactory
        public static Task factory() {
            DiscoveryFixtures.factoryHook.run();
            return Parallel.builder("invalid", Pool.named("missing")).build();
        }
    }

    static final class CanceledBuilderFactory {
        @Paramixel.TaskFactory
        public static TaskBuilder<?> factory() {
            DiscoveryFixtures.factoryHook.run();
            return Sequential.builder("invalid").step("missing");
        }
    }

    static final class ClosingThrowingFactory {
        @Paramixel.TaskFactory
        public static Task factory() {
            DiscoveryFixtures.factoryHook.run();
            throw new CompilationException("provider compilation failure");
        }
    }

    static final class FirstTaggedFactory {
        @Paramixel.TaskFactory
        @Paramixel.Tag("overlap")
        @Paramixel.Tag("first")
        @Paramixel.Tag("first")
        public static Task factory() {
            return SHARED_TAGGED_TASK;
        }
    }

    static final class SecondTaggedFactory {
        @Paramixel.TaskFactory
        @Paramixel.Tag("second")
        public static Task factory() {
            return SHARED_TAGGED_TASK;
        }
    }

    @Test
    void aDualRoleMethodIsRejectedOnlyWhenSelectedAsAFactory() {
        try (var runner = runner()) {
            final var result =
                    runner.run(Selector.builder().classOf(AlphaFixture.class).build());

            assertThat(result.status().isPassed()).isTrue();
            assertThat(DiscoveryFixtures.RECORDED).contains("beforeAll", "alpha", "afterAll");
            assertThat(DiscoveryFixtures.RECORDED).doesNotContain("conflicting-method");

            assertThatThrownBy(() -> runner.run(
                            Selector.builder().classOf(ConflictingFactory.class).build()))
                    .isInstanceOf(ResolverException.class)
                    .hasMessageContaining("cannot be both @Paramixel.TaskFactory and a run hook");
        }
    }

    @Paramixel.Priority(-1)
    static final class LaterFactory {

        @Paramixel.TaskFactory
        public static TaskBuilder<?> factory() {
            DiscoveryFixtures.RECORDED.add("later-factory");
            return Sequential.builder("later");
        }
    }

    static final class ConflictingFactory {

        @Paramixel.TaskFactory
        @Paramixel.BeforeAll
        public static TaskBuilder<?> factory() {
            DiscoveryFixtures.RECORDED.add("conflicting-method");
            return Sequential.builder("conflicting");
        }
    }

    @Test
    void closeDuringDiscoveryCancelsEvenIfNoFactoryContributedATask() {
        final var selector =
                Selector.builder().classOf(SkippingFactoryFixture.class).build();
        try (var runner = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.FAIL_IF_NO_TESTS, "false")))
                .build()) {
            DiscoveryFixtures.factoryHook = runner::close;
            final var result = runner.run(selector);

            assertThat(result.status().kind()).isEqualTo(Status.Kind.CANCELED);
            assertThat(result.exitCode()).isEqualTo(1);
            assertThat(result.root()).isEmpty();
            assertThat(result.isQuiescent()).isTrue();
        }
    }

    @Test
    void closeDuringDiscoveryDoesNotAdmitTheExecutionRoot() {
        final var admitted = new ArrayList<String>();
        try (var runner = Runner.builder()
                .listener(new Listener() {
                    @Override
                    public void onTaskEvent(final TaskEvent event) {
                        if (event.phase() == TaskPhase.ADMITTED) {
                            admitted.add(event.name());
                        }
                    }
                })
                .build()) {
            DiscoveryFixtures.factoryHook = runner::close;
            final var selector = Selector.builder()
                    .classOf(CloseDuringDiscoveryFixture.class)
                    .build();

            final var result = runner.run(selector);

            assertThat(result.status().kind()).isEqualTo(Status.Kind.CANCELED);
            assertThat(admitted).isEmpty();
        }
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
    void concurrentRunsAreRejected() throws InterruptedException {
        final var runner = runner();
        final var started = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var definition = Sequential.builder("slow")
                .step("work", () -> {
                    started.countDown();
                    release.await(5, TimeUnit.SECONDS);
                })
                .build();

        final var thread = new Thread(() -> runner.run(definition));
        thread.start();
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> runner.run(definition))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already in progress");
            assertThatThrownBy(() -> runner.run(throwingBinding()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already in progress");
        } finally {
            release.countDown();
            thread.join(5000L);
            runner.close();
        }

        assertThat(thread.isAlive()).isFalse();
    }

    @Test
    @DisplayName("a nonpositive discovery parallelism is rejected instead of being raised to one")
    void nonpositiveDiscoveryParallelismIsRejected() {
        for (String value : List.of("0", "-3")) {
            final var runner = Runner.builder()
                    .listener(Listener.NOOP)
                    .configuration(Configuration.of(
                            Map.of(Configuration.ANSI, "false", Configuration.RUNNER_PARALLELISM, value)))
                    .build();

            assertThatThrownBy(() -> runner.run(fixtures()))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(Configuration.RUNNER_PARALLELISM);
            assertThat(DiscoveryFixtures.RECORDED).isEmpty();
        }
    }

    @Test
    @DisplayName("invalid engine configuration is rejected before any factory is invoked")
    void invalidEngineConfigurationFailsBeforeFactories() {
        final var selector =
                Selector.builder().classOf(CloseDuringDiscoveryFixture.class).build();
        DiscoveryFixtures.factoryHook = () -> DiscoveryFixtures.RECORDED.add("factory invoked");
        for (String key :
                List.of(Configuration.WORKERS, Configuration.SHUTDOWN_GRACE_MILLIS, Configuration.RUNNER_PARALLELISM)) {
            final var runner = Runner.builder()
                    .listener(Listener.NOOP)
                    .configuration(Configuration.of(Map.of(Configuration.ANSI, "false", key, "0")))
                    .build();

            assertThatThrownBy(() -> runner.run(selector))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(key);
            assertThat(DiscoveryFixtures.RECORDED).as(key).isEmpty();
        }
    }

    @Test
    void classExcludedBySelectorCannotFailDiscoveryWithAnInvalidTag() {
        final var selector =
                Selector.builder().classOf(CloseDuringDiscoveryFixture.class).build();
        DiscoveryFixtures.factoryHook = () -> DiscoveryFixtures.RECORDED.add("selected factory invoked");
        try (var runner = runner()) {
            assertThat(runner.run(selector).status().kind()).isEqualTo(Status.Kind.PASSED);
            assertThat(DiscoveryFixtures.RECORDED).contains("selected factory invoked");
        }
    }

    @Test
    void invalidTagOnSelectedClassStillFailsDiscovery() {
        final var selector = Selector.builder().classOf(InvalidTagFixture.class).build();
        try (var runner = runner()) {
            assertThatThrownBy(() -> runner.run(selector))
                    .isInstanceOf(ResolverException.class)
                    .hasMessageContaining(InvalidTagFixture.class.getName())
                    .hasMessageContaining("value is blank");
        }
    }

    @Test
    void disabledFactoryIsExcludedBeforeCheckingConflictingAnnotations() {
        final var selector = Selector.builder()
                .classOf(DisabledConflictingFactoryFixture.class)
                .build();
        try (var runner = runner()) {
            final var result = runner.run(selector);
            assertThat(result.root()).isEmpty();
            assertThat(result.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        }
    }

    @Test
    void invalidDiscoveryPoolCapacityFailsBeforeFactories() {
        final var selector =
                Selector.builder().classOf(CloseDuringDiscoveryFixture.class).build();
        final var key = Configuration.poolKey("discovery");
        DiscoveryFixtures.factoryHook = () -> DiscoveryFixtures.RECORDED.add("factory invoked");
        try (var runner = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(key, "@cores/0")))
                .build()) {
            assertThatThrownBy(() -> runner.run(selector))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(key);
            assertThat(DiscoveryFixtures.RECORDED).isEmpty();
        }
    }

    @Test
    void discoveryPoolBuilderOverrideDoesNotValidateTheUnusedProperty() {
        final var selector =
                Selector.builder().classOf(CloseDuringDiscoveryFixture.class).build();
        DiscoveryFixtures.factoryHook = () -> DiscoveryFixtures.RECORDED.add("factory invoked");
        try (var runner = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.poolKey("discovery"), "@cores/0")))
                .pool(Pool.named("discovery"), 1)
                .build()) {
            assertThat(runner.run(selector).status().kind()).isEqualTo(Status.Kind.PASSED);
            assertThat(DiscoveryFixtures.RECORDED).contains("factory invoked");
        }
    }

    @Test
    void discoveryRejectsMalformedRunnerFlagsBeforeInvokingFactories() {
        final var selector =
                Selector.builder().classOf(CloseDuringDiscoveryFixture.class).build();
        DiscoveryFixtures.factoryHook = () -> DiscoveryFixtures.RECORDED.add("factory invoked");
        for (String key : List.of(
                Configuration.FAIL_FAST,
                Configuration.FAILURE_ON_SKIP,
                Configuration.FAILURE_ON_ABORT,
                Configuration.FAIL_IF_NO_TESTS)) {
            final var configuration = Configuration.of(Map.of(key, "not-true"));
            try (var runner = Runner.builder()
                    .listener(Listener.NOOP)
                    .configuration(configuration)
                    .build()) {
                assertThat(configuration.getBoolean(key)).as(key).hasValue(false);
                assertThatThrownBy(() -> runner.run(selector))
                        .isInstanceOf(ConfigurationException.class)
                        .hasMessageContaining(key);
            }
        }
        assertThat(DiscoveryFixtures.RECORDED).isEmpty();
    }

    @Test
    @DisplayName("a closed runner rejects further runs")
    void closedRunnerRejectsRuns() {
        final var runner = runner();
        runner.close();

        assertThatThrownBy(() -> runner.run(Sequential.builder("x").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
        assertThatThrownBy(() -> runner.run(throwingBinding()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void buildFailureReleasesTheRunSlot() {
        try (var runner = runner()) {
            assertThatThrownBy(() -> runner.run(Sequential.builder("invalid").step("missing")))
                    .isInstanceOf(CompilationException.class);
            assertThat(runner.run(Sequential.builder("valid")).status().isPassed())
                    .isTrue();
        }
    }

    private static TaskBuilder<?> throwingBinding() {
        return Sequential.builder("must not build").bind("sentinel", new Object() {
            @Override
            public String toString() {
                throw new AssertionError("the rejected builder rendered a bound value");
            }
        });
    }
}

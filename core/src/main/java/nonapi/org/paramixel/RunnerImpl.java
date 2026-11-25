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

package nonapi.org.paramixel;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import nonapi.org.paramixel.compiler.PlanCompiler;
import nonapi.org.paramixel.compiler.PoolSizes;
import nonapi.org.paramixel.discovery.FactoryScanner;
import nonapi.org.paramixel.execution.ExecutionEngine;
import nonapi.org.paramixel.reporting.ReportingDispatcher;
import nonapi.org.paramixel.selector.ConcreteSelector;
import nonapi.org.paramixel.support.FastUUID;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Pool;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.Task;
import org.paramixel.api.TaskBuilder;
import org.paramixel.api.exception.ConfigurationException;
import org.paramixel.api.selector.Selector;

/**
 * The single runner implementation.
 *
 * <p>A runner executes one run at a time. Each run creates its own workers, timers, and listener dispatcher, compiles a
 * fresh immutable plan, and returns an immutable result, so nothing from a previous run leaks into the next one and two
 * runner instances never share mutable framework state.
 */
public final class RunnerImpl implements Runner {

    private final Configuration configuration;
    private final Listener listener;
    private final boolean shuffled;
    private final long shuffleSeed;
    private final Map<Pool, Integer> poolOverrides;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<ExecutionEngine> activeEngine = new AtomicReference<>();
    private volatile boolean closed;

    /**
     * Creates a runner.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @param listener the listener; must not be {@code null}
     * @param shuffled whether discovery ordering is shuffled
     * @param shuffleSeed the discovery shuffle seed
     * @param poolOverrides explicit pool capacities; must not be {@code null}
     * @throws NullPointerException if an argument is {@code null}
     */
    public RunnerImpl(
            final Configuration configuration,
            final Listener listener,
            final boolean shuffled,
            final long shuffleSeed,
            final Map<Pool, Integer> poolOverrides) {
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
        this.listener = Objects.requireNonNull(listener, "listener is null");
        this.shuffled = shuffled;
        this.shuffleSeed = shuffleSeed;
        this.poolOverrides = Map.copyOf(Objects.requireNonNull(poolOverrides, "poolOverrides is null"));
    }

    @Override
    public Configuration configuration() {
        return configuration;
    }

    @Override
    public Result run(final Task definition) {
        Objects.requireNonNull(definition, "definition is null");
        acquire();
        try {
            return execute(definition, false, null);
        } finally {
            running.set(false);
        }
    }

    @Override
    public Result run(final TaskBuilder<?> definition) {
        Objects.requireNonNull(definition, "definition is null");
        return run(definition.build());
    }

    @Override
    public Result run(final Selector selector) {
        Objects.requireNonNull(selector, "selector is null");
        acquire();
        try {
            return discoverAndExecute(ConcreteSelector.require(selector));
        } finally {
            running.set(false);
        }
    }

    @Override
    public Result run() {
        acquire();
        try {
            return discoverAndExecute(ConcreteSelector.configured());
        } finally {
            running.set(false);
        }
    }

    @Override
    public void close() {
        closed = true;
        final var engine = activeEngine.get();
        if (engine != null) {
            engine.requestCancellation("runner closed");
        }
    }

    private void acquire() {
        if (closed) {
            throw new IllegalStateException("Runner is closed");
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "A run is already in progress on this Runner; create a separate Runner for concurrent runs");
        }
    }

    private static final Set<String> KNOWN_KEYS = Set.of(
            Configuration.RUNNER_PARALLELISM,
            Configuration.WORKERS,
            Configuration.SHUTDOWN_GRACE_MILLIS,
            Configuration.LISTENER_QUEUE_CAPACITY,
            Configuration.LISTENER_DRAIN_TIMEOUT_MILLIS,
            Configuration.ANSI,
            Configuration.FAILURE_ON_SKIP,
            Configuration.FAILURE_ON_ABORT,
            Configuration.FAIL_IF_NO_TESTS,
            Configuration.FAIL_FAST,
            Configuration.REPORT_FILE,
            Configuration.MATCH_PACKAGE_REGEX,
            Configuration.MATCH_CLASS_REGEX,
            Configuration.MATCH_TAG_REGEX,
            Configuration.LISTENER_EXCLUDE,
            "paramixel.run.id",
            "paramixel.skipTests",
            "paramixel.strictThreadLifecycle");

    private void warnAboutUnknownKeys(final ReportingDispatcher dispatcher) {
        for (String key : configuration.keySet()) {
            if (!key.startsWith("paramixel.")
                    || KNOWN_KEYS.contains(key)
                    || key.startsWith(Configuration.PARALLELISM_POOL_PREFIX)) {
                continue;
            }
            dispatcher.warning("Unknown Paramixel configuration key '" + key + "' is ignored");
        }
    }

    private Result discoverAndExecute(final ConcreteSelector selector) {
        final var runId = FastUUID.generateUuid();
        try (var dispatcher = new ReportingDispatcher(listener, configuration)) {
            dispatcher.runStarted(configuration);
            warnAboutUnknownKeys(dispatcher);
            warnAboutIgnoredSelection(selector, dispatcher);
            // Validation first: invalid configuration must fail before discovery invokes any factory.
            ExecutionEngine.validateConfiguration(configuration);
            final var overrides = new LinkedHashMap<>(poolOverrides);
            seedDiscoveryPool(overrides);
            dispatcher.discoveryStarted();
            final var effectiveSelector = selector.resolve(configuration);
            final var scanner = new FactoryScanner(configuration, effectiveSelector, shuffled, shuffleSeed);
            final var discovered = scanner.discover();
            scanner.skipped().forEach(dispatcher::warning);
            dispatcher.discoveryCompleted(discovered);
            if (discovered.isEmpty()) {
                return finishEmptyDiscovery(runId, dispatcher);
            }
            return executeDiscovered(runId, discovered.orElseThrow(), overrides, true, dispatcher);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while delivering listener events", e);
        }
    }

    private Result execute(final Task definition, final boolean discoveryRun, final ReportingDispatcher existing) {
        final var runId = FastUUID.generateUuid();
        if (existing != null) {
            return executeDiscovered(runId, definition, new LinkedHashMap<>(poolOverrides), discoveryRun, existing);
        }
        try (var dispatcher = new ReportingDispatcher(listener, configuration)) {
            dispatcher.runStarted(configuration);
            warnAboutUnknownKeys(dispatcher);
            return executeDiscovered(runId, definition, new LinkedHashMap<>(poolOverrides), discoveryRun, dispatcher);
        }
    }

    private Result executeDiscovered(
            final String runId,
            final Task definition,
            final Map<Pool, Integer> overrides,
            final boolean discoveryRun,
            final ReportingDispatcher dispatcher) {
        final var plan = new PlanCompiler(configuration, overrides).compile(definition);
        plan.warnings().forEach(warning -> dispatcher.warning(warning.toString()));
        final var engine = new ExecutionEngine(
                runId, plan, configuration, dispatcher, discoveryRun && shuffled ? shuffleSeed : null);
        activeEngine.set(engine);
        if (closed) {
            // close() may have run before the engine was published (for example during discovery) and found nothing
            // to cancel; publishing first and re-checking guarantees one of the two sides requests cancellation.
            engine.requestCancellation("runner closed");
        }
        try {
            final var result = engine.run();
            try {
                return dispatcher.finish(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while draining listener events", e);
            }
        } finally {
            activeEngine.set(null);
        }
    }

    private void seedDiscoveryPool(final Map<Pool, Integer> overrides) {
        final var pool = Pool.named(FactoryScanner.DISCOVERY_POOL);
        if (overrides.containsKey(pool)) {
            return;
        }
        final var key = Configuration.poolKey(pool.name());
        final var configured = configuration.getString(key);
        if (configured.isPresent()) {
            final var value = configured.orElseThrow();
            try {
                overrides.put(pool, PoolSizes.parse(value));
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Invalid configuration for '" + key + "': " + e.getMessage(), e);
            }
            return;
        }
        final var capacity = configuration
                .getInteger(Configuration.RUNNER_PARALLELISM)
                .orElse(Runtime.getRuntime().availableProcessors());
        if (capacity <= 0) {
            throw new ConfigurationException("Invalid configuration for '" + Configuration.RUNNER_PARALLELISM
                    + "': must be positive but was " + capacity);
        }
        overrides.put(pool, capacity);
    }

    private Result finishEmptyDiscovery(final String runId, final ReportingDispatcher dispatcher)
            throws InterruptedException {
        final Status status;
        final int exitCode;
        final List<String> warnings;
        if (closed) {
            status = Status.canceled("runner closed");
            exitCode = 1;
            warnings = List.of();
            dispatcher.warning("Run cancellation requested: runner closed");
        } else {
            // An empty discovery follows the separately configured failIfNoTests rule, not the skip mapping.
            status = Status.skipped("no task factories were discovered");
            exitCode = configuration.getBoolean(Configuration.FAIL_IF_NO_TESTS).orElse(true) ? 1 : 0;
            warnings = List.of("No @Paramixel.TaskFactory method contributed a task for the requested selection");
        }
        final var result = Result.builder()
                .runId(runId)
                .status(status, exitCode, true)
                .timings(Instant.now(), Instant.now())
                .diagnostics(warnings)
                .shuffleSeed(shuffled ? shuffleSeed : null)
                .build();
        return dispatcher.finish(result);
    }

    private void warnAboutIgnoredSelection(final ConcreteSelector selector, final ReportingDispatcher dispatcher) {
        if (selector.usesConfiguration()) {
            return;
        }
        final var keys = List.of(
                Configuration.MATCH_PACKAGE_REGEX, Configuration.MATCH_CLASS_REGEX, Configuration.MATCH_TAG_REGEX);
        for (String key : keys) {
            if (configuration.getString(key).isPresent()) {
                dispatcher.warning("Configuration '" + key + "' is ignored because the supplied Selector is explicit");
            }
        }
    }
}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.listener.SafeListener;
import nonapi.org.paramixel.selector.ConcreteSelector;
import nonapi.org.paramixel.support.StackTracePruner;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.selector.Selector;

/**
 * Default runner implementation that returns results containing descriptor trees and effective aggregate status.
 */
public final class ConcreteRunner implements Runner {

    private static final String NO_TESTS_FOUND = "No Paramixel tests found";

    private static final String PARAMIXEL_PREFIX = "paramixel.";

    private final ReentrantLock lock = new ReentrantLock();
    private final SafeListener safeListener;
    private final Configuration configuration;
    private final boolean shuffled;
    private final long seed;

    /**
     * Creates a runner with the supplied configuration and listener.
     *
     * @param configuration the configuration, or {@code null} to load defaults
     * @param listener the listener; must not be {@code null}
     */
    public ConcreteRunner(final Configuration configuration, final Listener listener) {
        this(configuration, listener, false, 0L);
    }

    /**
     * Creates a runner with the supplied configuration, listener, and shuffle settings.
     *
     * <p>When {@code shuffled} is {@code true}, discovered actions are shuffled using
     * {@code seed} at resolution time. The existing two-argument constructor delegates
     * to this one with {@code shuffled = false} and {@code seed = 0L}.
     *
     * @param configuration the configuration, or {@code null} to load defaults
     * @param listener the listener; must not be {@code null}
     * @param shuffled whether to shuffle discovered actions
     * @param seed the PRNG seed when {@code shuffled} is {@code true}
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public ConcreteRunner(
            final Configuration configuration, final Listener listener, final boolean shuffled, final long seed) {
        Objects.requireNonNull(listener, "listener is null");
        this.safeListener = listener instanceof SafeListener safe ? safe : new SafeListener(listener);
        this.configuration = configuration != null ? configuration : Configuration.defaultConfiguration();
        this.shuffled = shuffled;
        this.seed = seed;
    }

    @Override
    public Configuration configuration() {
        return configuration;
    }

    @Override
    public Result run(final Action action) {
        Objects.requireNonNull(action, "action is null");
        return execute(() -> Optional.of(action), unknownKeyWarnings());
    }

    @Override
    public Result run(final Selector selector) {
        Objects.requireNonNull(selector, "selector is null");
        var concrete = ConcreteSelector.require(selector);
        var warnings = new ArrayList<>(unknownKeyWarnings());
        if (!concrete.usesConfiguration()) {
            warnings.addAll(ignoredMatchKeyWarnings());
        }
        return execute(
                () -> new ActionResolver(configuration, concrete.resolve(configuration), shuffled, seed)
                        .resolveRootAction(),
                warnings);
    }

    @Override
    public Result run() {
        return execute(
                () -> new ActionResolver(
                                configuration, ConcreteSelector.fromConfiguration(configuration), shuffled, seed)
                        .resolveRootAction(),
                unknownKeyWarnings());
    }

    /**
     * Runs one entry point: resolves the action, then executes it with the no-tests lifecycle when resolution finds
     * nothing. The listener is initialized and warned exactly once, before discovery, for every outcome.
     */
    private Result execute(final Supplier<Optional<Action>> resolution, final List<String> warnings) {
        lock.lock();
        try {
            var resolved = resolution.get();
            return resolved.map(action -> runInternal(action, warnings)).orElseGet(() -> noTestsResult(warnings));
        } finally {
            lock.unlock();
        }
    }

    private static final Set<String> KNOWN_KEYS = Set.of(
            Configuration.RUNNER_PARALLELISM,
            Configuration.ANSI,
            Configuration.FAILURE_ON_SKIP,
            Configuration.FAILURE_ON_ABORT,
            Configuration.FAIL_IF_NO_TESTS,
            Configuration.FAIL_FAST,
            Configuration.REPORT_FILE,
            Configuration.MATCH_PACKAGE_REGEX,
            Configuration.MATCH_CLASS_REGEX,
            Configuration.MATCH_TAG_REGEX,
            Configuration.LISTENER_EXCLUDE);

    /**
     * Returns one warning per {@code paramixel.*} key the framework does not recognize, so a typo in a properties
     * file or a removed key does not fail silently.
     */
    private List<String> unknownKeyWarnings() {
        var warnings = new ArrayList<String>();
        configuration.keySet().stream()
                .filter(key -> key.startsWith(PARAMIXEL_PREFIX) && !isKnownKey(key))
                .sorted()
                .forEach(key -> warnings.add("Unknown configuration key '" + key + "' has no effect"));
        return warnings;
    }

    private static boolean isKnownKey(final String key) {
        return KNOWN_KEYS.contains(key) || key.startsWith(Configuration.PARALLELISM_POOL_PREFIX);
    }

    private List<String> ignoredMatchKeyWarnings() {
        var warnings = new ArrayList<String>();
        for (var key : List.of(
                Configuration.MATCH_PACKAGE_REGEX, Configuration.MATCH_CLASS_REGEX, Configuration.MATCH_TAG_REGEX)) {
            if (configuration.getString(key).isPresent()) {
                warnings.add(key + " is set but the selector given to run(Selector) does not use"
                        + " Selector.configured(); the key is ignored");
            }
        }
        return warnings;
    }

    private Result runInternal(final Action action, final List<String> warnings) {
        initializeListener(warnings);
        safeListener.onDiscoveryStarted();
        ConcreteDescriptor root = null;
        Engine engine = null;
        boolean discoveryCompleted = false;
        try {
            var plan = CompiledPlan.compile(action);
            root = plan.root().descriptor;
            safeListener.onDiscoveryCompleted(Optional.of(root));
            discoveryCompleted = true;
            warnAboutUnusedPoolKeys(plan);
            engine = new Engine(new Pools(configuration));
            engine.setFailFast(configuration.getBoolean(Configuration.FAIL_FAST).orElse(false));
            engine.execute(plan, configuration, safeListener);
            var unrecoverableFailure = engine.unrecoverableFailure();
            if (unrecoverableFailure instanceof Error error) {
                throw error;
            }
            var result = new ConcreteResult(root, configuration, engine.poolStats());
            safeListener.onRunCompleted(result);
            return result;
        } catch (Throwable t) {
            if (UnrecoverableErrors.isUnrecoverable(t) && t instanceof Error error) {
                throw error;
            }
            StackTracePruner.prune(t);
            final var message = t.getMessage() != null ? t.getMessage() : "Runner failed";
            if (root != null) {
                var status = root.status();
                if (status.isPending()) {
                    root.setStatus(Status.RUNNING);
                }
                root.setStatus(Status.failed(message, t));
            }
            final var result = root != null
                    ? new ConcreteResult(root, configuration)
                    : new ConcreteResult(configuration, Status.failed(message, t));
            if (!discoveryCompleted) {
                safeListener.onDiscoveryCompleted(Optional.ofNullable(root));
            }
            safeListener.onRunCompleted(result);
            return result;
        } finally {
            closeEngine(engine);
        }
    }

    /**
     * Warns about every {@code paramixel.parallelism.<name>} key that names a pool no {@code Parallel} in the plan
     * uses, which is the usual symptom of a typo in a properties file.
     */
    private void warnAboutUnusedPoolKeys(final CompiledPlan plan) {
        var used = plan.poolNames();
        configuration.keySet().stream()
                .filter(key -> key.startsWith(Configuration.PARALLELISM_POOL_PREFIX))
                .sorted()
                .forEach(key -> {
                    var name = key.substring(Configuration.PARALLELISM_POOL_PREFIX.length());
                    if (!used.contains(name)) {
                        safeListener.onWarning("Configuration key '" + key + "' names pool '" + name
                                + "', but no Parallel in this run uses it");
                    }
                });
    }

    private void initializeListener(final List<String> warnings) {
        safeListener.onRunStarted(configuration);
        for (var warning : warnings) {
            safeListener.onWarning(warning);
        }
    }

    private static void closeEngine(final Engine engine) {
        if (engine != null) {
            engine.close();
        }
    }

    private Result noTestsResult(final List<String> warnings) {
        initializeListener(warnings);
        safeListener.onDiscoveryStarted();
        safeListener.onDiscoveryCompleted(Optional.empty());
        var result = new ConcreteResult(configuration);
        safeListener.onRunCompleted(result);
        return result;
    }
}

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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.listener.SafeListener;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Mode;
import org.paramixel.api.action.Spec;
import org.paramixel.api.exception.ConfigurationException;
import org.paramixel.api.selector.Selector;

/**
 * Default runner implementation that returns results containing descriptor trees and effective aggregate status.
 */
public final class ConcreteRunner implements Runner {

    private static final String NO_TESTS_FOUND = "No Paramixel tests found";

    private final ReentrantLock lock = new ReentrantLock();
    private final SafeListener safeListener;
    private final Configuration configuration;
    private final Map<String, String> configurationMap;
    private final int parallelism;
    private final int schedulerQueueCapacity;

    /**
     * Creates a runner with the supplied configuration and listener.
     *
     * @param configuration the configuration, or {@code null} to load defaults
     * @param listener the listener; must not be {@code null}
     * @param explicitListener whether the listener was explicitly supplied
     */
    public ConcreteRunner(final Configuration configuration, final Listener listener, final boolean explicitListener) {
        Objects.requireNonNull(listener, "listener is null");
        this.safeListener = listener instanceof SafeListener safe ? safe : new SafeListener(listener);
        this.configuration = configuration != null ? configuration : Configuration.defaultConfiguration();
        this.configurationMap =
                this.configuration instanceof ConcreteConfiguration dc ? dc.toMap() : Map.<String, String>of();
        this.parallelism = resolveParallelism(this.configuration);
        this.schedulerQueueCapacity = resolveSchedulerQueueCapacity(this.configuration);
    }

    @Override
    public Configuration configuration() {
        return configuration;
    }

    @Override
    public Result run(final Spec<?> spec) {
        lock.lock();
        try {
            return runInternal(spec);
        } finally {
            lock.unlock();
        }
    }

    private Result runInternal(final Spec<?> spec) {
        Objects.requireNonNull(spec, "spec is null");
        final var action = spec.resolve();
        safeListener.initialize(configuration);
        safeListener.onRunStarted();
        safeListener.onDiscoveryStarted();
        MutableDescriptor root = null;
        Scheduler scheduler = null;
        try {
            root = new DescriptorBuilder(configuration).discover(action);
            safeListener.onDiscoveryCompleted(root);
            scheduler = new Scheduler(parallelism, schedulerQueueCapacity);
            var topLevelParallelThrottle = new TopLevelParallelThrottle(parallelism);
            var context = new ConcreteContext(
                    configuration, safeListener, root, scheduler, new InstanceHolder(), topLevelParallelThrottle);
            root.markScheduled(Mode.RUN);
            scheduler.executeDescriptor(root, context);
            var result = new ConcreteResult(root, configuration);
            safeListener.onRunCompleted(result);
            return result;
        } catch (Throwable t) {
            if (UnrecoverableErrors.isUnrecoverable(t) && t instanceof Error error) {
                throw error;
            }
            var result = root != null ? new ConcreteResult(root, configuration) : new ConcreteResult(configuration);
            safeListener.onRunCompleted(result);
            return result;
        } finally {
            closeScheduler(scheduler);
        }
    }

    private static int resolveParallelism(final Configuration configuration) {
        final var optionalParallelism = configuration.getInteger(Configuration.RUNNER_PARALLELISM);
        if (optionalParallelism.isEmpty()) {
            return Runtime.getRuntime().availableProcessors();
        }
        final int value = optionalParallelism.get();
        if (value <= 0) {
            throw new ConfigurationException("Invalid configuration for '" + Configuration.RUNNER_PARALLELISM
                    + "': expected positive integer but was '" + value + "'");
        }
        return value;
    }

    private static int resolveSchedulerQueueCapacity(final Configuration configuration) {
        final var optionalCapacity = configuration.getInteger(Configuration.SCHEDULER_QUEUE_CAPACITY);
        if (optionalCapacity.isEmpty()) {
            return 1024;
        }
        final int value = optionalCapacity.get();
        if (value <= 0) {
            throw new ConfigurationException("Invalid configuration for '" + Configuration.SCHEDULER_QUEUE_CAPACITY
                    + "': expected positive integer but was '" + value + "'");
        }
        return value;
    }

    private static void closeScheduler(final Scheduler scheduler) {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    @Override
    public Optional<Result> run(final Selector selector) {
        lock.lock();
        try {
            Objects.requireNonNull(selector, "selector is null");
            var optionalAction = new ClasspathResolver(configuration, selector).resolveActions();
            return optionalAction.map(this::runInternal);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int runAndReturnExitCode(final Spec<?> spec) {
        lock.lock();
        try {
            Objects.requireNonNull(spec, "spec is null");
            return exitCode(runInternal(spec));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int runAndReturnExitCode(final Selector selector) {
        lock.lock();
        try {
            Objects.requireNonNull(selector, "selector is null");
            var optionalAction = new ClasspathResolver(configuration, selector).resolveActions();
            return optionalAction.map(this::runAndReturnExitCodeInternal).orElseGet(this::noTestsExitCode);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void runAndExit(final Spec<?> spec) {
        lock.lock();
        try {
            Objects.requireNonNull(spec, "spec is null");
            System.exit(exitCode(runInternal(spec)));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void runAndExit(final Selector selector) {
        lock.lock();
        try {
            Objects.requireNonNull(selector, "selector is null");
            var optionalAction = new ClasspathResolver(configuration, selector).resolveActions();
            System.exit(optionalAction.map(this::runAndReturnExitCodeInternal).orElseGet(this::noTestsExitCode));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int run() {
        lock.lock();
        try {
            var optionalAction = new ClasspathResolver(configuration, buildSelector(configuration)).resolveActions();
            if (optionalAction.isEmpty()) {
                if (noTestsExitCode() == 1) {
                    System.err.println(NO_TESTS_FOUND);
                    return 1;
                }
                System.out.println(NO_TESTS_FOUND);
                return 0;
            }
            return exitCode(runInternal(optionalAction.get()));
        } finally {
            lock.unlock();
        }
    }

    private int runAndReturnExitCodeInternal(final Spec<?> spec) {
        return exitCode(runInternal(spec));
    }

    private static Selector buildSelector(final Configuration config) {
        var pkg = config.getString(Configuration.MATCH_PACKAGE_REGEX);
        var cls = config.getString(Configuration.MATCH_CLASS_REGEX);
        var tag = config.getString(Configuration.MATCH_TAG_REGEX);

        if (pkg.isEmpty() && cls.isEmpty() && tag.isEmpty()) {
            return Selector.all();
        }
        var selectors = new ArrayList<Selector>();
        try {
            pkg.filter(s -> !s.isBlank()).ifPresent(s -> selectors.add(Selector.packageRegex(s)));
        } catch (RuntimeException e) {
            throw new ConfigurationException(
                    "Invalid regex for '" + Configuration.MATCH_PACKAGE_REGEX + "': " + e.getMessage(), e);
        }
        try {
            cls.filter(s -> !s.isBlank()).ifPresent(s -> selectors.add(Selector.classRegex(s)));
        } catch (RuntimeException e) {
            throw new ConfigurationException(
                    "Invalid regex for '" + Configuration.MATCH_CLASS_REGEX + "': " + e.getMessage(), e);
        }
        try {
            tag.filter(s -> !s.isBlank()).ifPresent(s -> selectors.add(Selector.tagRegex(s)));
        } catch (RuntimeException e) {
            throw new ConfigurationException(
                    "Invalid regex for '" + Configuration.MATCH_TAG_REGEX + "': " + e.getMessage(), e);
        }
        return selectors.size() == 1 ? selectors.get(0) : Selector.and(selectors);
    }

    private int noTestsExitCode() {
        final var failIfNoTests =
                configuration.getBoolean(Configuration.FAIL_IF_NO_TESTS).orElse(false);
        return failIfNoTests ? 1 : 0;
    }

    private int exitCode(final Result result) {
        final var status = result.status();
        if (status.isPassed()) {
            return 0;
        }
        if (status.isSkipped()) {
            return 0;
        }
        if (status.isAborted()) {
            final var failureOnAbort =
                    configuration.getBoolean(Configuration.FAILURE_ON_ABORT).orElse(true);
            return failureOnAbort ? 1 : 0;
        }
        return 1;
    }

    /**
     * Returns the captured configuration as a map for compatibility with existing diagnostics.
     *
     * @return immutable configuration map
     */
    Map<String, String> configurationMap() {
        return configurationMap;
    }
}

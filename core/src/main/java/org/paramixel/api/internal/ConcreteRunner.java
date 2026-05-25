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

package org.paramixel.api.internal;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Spec;
import org.paramixel.api.exception.ConfigurationException;
import org.paramixel.api.internal.action.DescriptorBuilder;
import org.paramixel.api.internal.action.MutableDescriptor;
import org.paramixel.api.internal.listener.SafeListener;
import org.paramixel.api.internal.support.UnrecoverableErrors;
import org.paramixel.api.selector.Selector;
import org.paramixel.spi.action.Mode;

/**
 * Default runner implementation that returns results containing descriptor trees and effective aggregate status.
 */
public final class ConcreteRunner implements Runner {

    private static final String NO_TESTS_FOUND = "No Paramixel tests found";

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
        Objects.requireNonNull(listener, "listener must not be null");
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
    public synchronized Result run(final Spec<?> spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        final var action = spec.resolve();
        safeListener.onRunStarted();
        MutableDescriptor root = null;
        AsyncScheduler scheduler = null;
        try {
            root = new DescriptorBuilder(configuration).discover(action);
            safeListener.onDiscoveryCompleted(root);
            scheduler = new AsyncScheduler(parallelism, schedulerQueueCapacity);
            var context =
                    new ConcreteExecutionContext(configuration, safeListener, root, scheduler, new InstanceHolder());
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

    private static void closeScheduler(final AsyncScheduler scheduler) {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    @Override
    public Optional<Result> run(final Selector selector) {
        Objects.requireNonNull(selector, "selector must not be null");
        var optionalAction = new ClasspathResolver(configuration, selector).resolveActions();
        return optionalAction.map(this::run);
    }

    @Override
    public int runAndReturnExitCode(final Spec<?> spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        return exitCode(run(spec));
    }

    @Override
    public int runAndReturnExitCode(final Selector selector) {
        Objects.requireNonNull(selector, "selector must not be null");
        var optionalAction = new ClasspathResolver(configuration, selector).resolveActions();
        return optionalAction.map(this::runAndReturnExitCode).orElseGet(this::noTestsExitCode);
    }

    @Override
    public void runAndExit(final Spec<?> spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        System.exit(runAndReturnExitCode(spec));
    }

    @Override
    public void runAndExit(final Selector selector) {
        Objects.requireNonNull(selector, "selector must not be null");
        System.exit(runAndReturnExitCode(selector));
    }

    @Override
    public int run() {
        final var optionalAction = new ClasspathResolver(configuration, Selector.all()).resolveActions();
        if (optionalAction.isEmpty()) {
            if (noTestsExitCode() == 1) {
                System.err.println(NO_TESTS_FOUND);
                return 1;
            }
            System.out.println(NO_TESTS_FOUND);
            return 0;
        }
        return runAndReturnExitCode(optionalAction.get());
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

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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.locks.ReentrantLock;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.listener.SafeListener;
import nonapi.org.paramixel.support.StackTracePruner;
import nonapi.org.paramixel.support.Throwables;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
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
    private final int parallelism;
    private final int schedulerQueueCapacity;
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
        this.parallelism = resolveParallelism(this.configuration);
        this.schedulerQueueCapacity = resolveSchedulerQueueCapacity(this.configuration);
        this.shuffled = shuffled;
        this.seed = seed;
    }

    @Override
    public Configuration configuration() {
        return configuration;
    }

    @Override
    public Result run(final Action action) {
        lock.lock();
        try {
            Objects.requireNonNull(action, "action is null");
            var effective = new ActionResolver(configuration, Selector.all(), shuffled, seed).resolveRootAction(action);
            return runInternal(effective.orElseThrow());
        } finally {
            lock.unlock();
        }
    }

    private Result runInternal(final Action action) {
        Objects.requireNonNull(action, "action is null");
        safeListener.initialize(configuration);
        safeListener.onRunStarted();
        safeListener.onDiscoveryStarted();
        MutableDescriptor root = null;
        Scheduler scheduler = null;
        boolean discoveryCompleted = false;
        try {
            root = new DescriptorBuilder().discover(action);
            safeListener.onDiscoveryCompleted(root);
            discoveryCompleted = true;
            scheduler = new Scheduler(parallelism, schedulerQueueCapacity);
            scheduler.setFailFast(
                    configuration.getBoolean(Configuration.FAIL_FAST).orElse(false));
            var context = new ConcreteContext(configuration, safeListener, root, scheduler, new InstanceHolder());
            root.markScheduled();
            var rootFuture = root.scheduledFuture();
            scheduler.executeDescriptor(root, context, ExecutionMode.RUN);
            // executeDescriptor waits for deferred coordination roots via the scheduler's
            // managedJoin(nodeCompletion) path. The root future should therefore already be done;
            // join() remains as an idempotent safety guard for unusual external-completion paths.
            //
            // join() blocks indefinitely — timeouts are the responsibility of the
            // Timeout action, not the runner. join() is interruptible: if the calling
            // thread is interrupted (e.g. by JUnit @Timeout), the interrupt propagates
            // and close() in the finally block handles executor shutdown.
            if (rootFuture != null) {
                try {
                    rootFuture.join();
                } catch (CompletionException e) {
                    var cause = Throwables.unwrap(e);
                    if (UnrecoverableErrors.isUnrecoverable(cause) && cause instanceof Error error) {
                        throw error;
                    }
                    // Root failed; status is already set on the descriptor.
                }
            }
            var unrecoverableFailure = scheduler.unrecoverableFailure();
            if (unrecoverableFailure instanceof Error error) {
                throw error;
            }
            var result = new ConcreteResult(root, configuration);
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
                safeListener.onDiscoveryCompleted(root);
            }
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
            return 1_024;
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
            var optionalAction = new ActionResolver(configuration, selector, shuffled, seed).resolveRootAction();
            if (optionalAction.isEmpty()) {
                fireNoTestsLifecycle();
                return Optional.empty();
            }
            return Optional.of(runInternal(optionalAction.get()));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int runAndReturnExitCode(final Action action) {
        lock.lock();
        try {
            Objects.requireNonNull(action, "action is null");
            var effective = new ActionResolver(configuration, Selector.all(), shuffled, seed).resolveRootAction(action);
            return exitCode(runInternal(effective.orElseThrow()));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int runAndReturnExitCode(final Selector selector) {
        lock.lock();
        try {
            Objects.requireNonNull(selector, "selector is null");
            var optionalAction = new ActionResolver(configuration, selector, shuffled, seed).resolveRootAction();
            if (optionalAction.isEmpty()) {
                fireNoTestsLifecycle();
                return noTestsExitCode();
            }
            return exitCode(runInternal(optionalAction.get()));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void runAndExit(final Action action) {
        final int exitCode;
        lock.lock();
        try {
            Objects.requireNonNull(action, "action is null");
            var effective = new ActionResolver(configuration, Selector.all(), shuffled, seed).resolveRootAction(action);
            exitCode = exitCode(runInternal(effective.orElseThrow()));
        } finally {
            lock.unlock();
        }
        System.exit(exitCode);
    }

    @Override
    public void runAndExit(final Selector selector) {
        final int exitCode;
        lock.lock();
        try {
            Objects.requireNonNull(selector, "selector is null");
            var optionalAction = new ActionResolver(configuration, selector, shuffled, seed).resolveRootAction();
            if (optionalAction.isEmpty()) {
                fireNoTestsLifecycle();
                exitCode = noTestsExitCode();
            } else {
                exitCode = exitCode(runInternal(optionalAction.get()));
            }
        } finally {
            lock.unlock();
        }
        System.exit(exitCode);
    }

    @Override
    public int run() {
        lock.lock();
        try {
            var optionalAction =
                    new ActionResolver(configuration, buildSelector(configuration), shuffled, seed).resolveRootAction();
            if (optionalAction.isEmpty()) {
                fireNoTestsLifecycle();
                return noTestsExitCode();
            }
            return exitCode(runInternal(optionalAction.get()));
        } finally {
            lock.unlock();
        }
    }

    private void fireNoTestsLifecycle() {
        safeListener.initialize(configuration);
        safeListener.onRunStarted();
        safeListener.onDiscoveryStarted();
        safeListener.onDiscoveryCompleted(null);
        var result = new ConcreteResult(configuration);
        safeListener.onRunCompleted(result);
    }

    private static Selector buildSelector(final Configuration configuration) {
        var pkg = configuration.getString(Configuration.MATCH_PACKAGE_REGEX);
        var cls = configuration.getString(Configuration.MATCH_CLASS_REGEX);
        var tag = configuration.getString(Configuration.MATCH_TAG_REGEX);

        if (pkg.isEmpty() && cls.isEmpty() && tag.isEmpty()) {
            return Selector.all();
        }
        var selectors = new ArrayList<Selector>();
        var pkgRegex = pkg.filter(s -> !s.isBlank()).orElse(null);
        if (pkgRegex != null) {
            try {
                selectors.add(Selector.packageRegex(pkgRegex));
            } catch (Exception e) {
                throw new ConfigurationException(
                        "Invalid package regex pattern '" + pkgRegex + "': " + e.getMessage(), e);
            }
        }
        var clsRegex = cls.filter(s -> !s.isBlank()).orElse(null);
        if (clsRegex != null) {
            try {
                selectors.add(Selector.classRegex(clsRegex));
            } catch (Exception e) {
                throw new ConfigurationException(
                        "Invalid class regex pattern '" + clsRegex + "': " + e.getMessage(), e);
            }
        }
        var tagRegex = tag.filter(s -> !s.isBlank()).orElse(null);
        if (tagRegex != null) {
            try {
                selectors.add(Selector.tagRegex(tagRegex));
            } catch (Exception e) {
                throw new ConfigurationException("Invalid tag regex pattern '" + tagRegex + "': " + e.getMessage(), e);
            }
        }
        return selectors.size() == 1 ? selectors.get(0) : Selector.and(selectors);
    }

    private int noTestsExitCode() {
        final var failIfNoTests =
                configuration.getBoolean(Configuration.FAIL_IF_NO_TESTS).orElse(false);
        return failIfNoTests ? 1 : 0;
    }

    private int exitCode(final Result result) {
        if (result.isPassed()) {
            return 0;
        }
        if (result.isSkipped()) {
            final var failureOnSkip =
                    configuration.getBoolean(Configuration.FAILURE_ON_SKIP).orElse(false);
            return failureOnSkip ? 1 : 0;
        }
        if (result.isAborted()) {
            final var failureOnAbort =
                    configuration.getBoolean(Configuration.FAILURE_ON_ABORT).orElse(true);
            return failureOnAbort ? 1 : 0;
        }
        return 1;
    }
}

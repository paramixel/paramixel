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

package org.paramixel.core.internal;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import org.paramixel.core.Action;
import org.paramixel.core.Configuration;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.exception.ConfigurationException;
import org.paramixel.core.internal.listener.SafeListener;

/**
 * Default {@link Runner} implementation used for standard Paramixel execution.
 *
 * <p>This SPI implementation validates configuration, creates execution infrastructure, detects invalid action-graph
 * conditions such as cycles and thread-starvation deadlocks, and drives listener callbacks around the root action.
 *
 * <p>{@code DefaultRunner} instances are not thread-safe and not reusable across multiple {@link #run(Action)}
 * calls. Each invocation of {@link #run(Action)} is {@code synchronized} to enforce single-thread access.
 * Create a fresh instance for each execution boundary.
 *
 * <p>Concurrent execution across different runner instances is not supported. The framework uses shared global state
 * for action hierarchy indexing during execution; concurrent runs would corrupt this state and produce incorrect
 * reporting output.
 */
public final class DefaultRunner implements Runner {
    private final Listener listener;
    private final DefaultConfiguration configuration;

    /**
     * Creates a runner with the supplied configuration and listener.
     *
     * @param configuration the configuration map to use, or {@code null} to load
     *     {@link Configuration#defaultProperties()}
     * @param listener the listener to receive lifecycle callbacks
     * @throws NullPointerException if {@code listener} is {@code null}
     * @throws ConfigurationException if {@code configuration} contains an invalid runner
     *     parallelism value
     */
    public DefaultRunner(Map<String, String> configuration, Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.configuration = new DefaultConfiguration(configuration);
        this.configuration.resolveParallelism();
    }

    @Override
    public Map<String, String> getConfiguration() {
        return configuration.asMap();
    }

    @Override
    public Listener getListener() {
        return listener;
    }

    /**
     * Cascades {@code close()} to the listener so that listeners holding resources are cleaned up when the runner is
     * used in try-with-resources.
     */
    @Override
    public void close() {
        listener.close();
    }

    /**
     * This method is {@code synchronized} to enforce single-thread access. Before execution, the action graph is
     * validated for cycles and thread-starvation deadlocks.
     */
    @Override
    public synchronized Result run(Action action) {
        Objects.requireNonNull(action, "action must not be null");

        Listener safeListener = listener instanceof SafeListener ? listener : new SafeListener(listener);

        var rootResult = new DefaultResult(action);

        try (ActionHierarchy.Scope ignored = ActionHierarchy.install(action);
                var scheduler = new DefaultAsyncScheduler(configuration)) {
            new CycleDetector().validateNoCycles(action);
            safeListener.runStarted(this);

            var context = new DefaultContext(configuration, safeListener, scheduler);
            Result executeResult = scheduler.runAsync(action, context).join();

            rootResult.complete(executeResult.getStatus(), executeResult.getRunDuration());
            for (Result child : executeResult.getChildren()) {
                rootResult.addChild(child);
            }

            safeListener.runCompleted(this, rootResult);
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
        return rootResult;
    }
}

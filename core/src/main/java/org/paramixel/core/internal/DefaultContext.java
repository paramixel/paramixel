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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.paramixel.core.Action;
import org.paramixel.core.AsyncScheduler;
import org.paramixel.core.Configuration;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Store;
import org.paramixel.core.exception.AncestorNotFoundException;

/**
 * Provides scoped runtime state to actions, with independent stores per context and shared configuration, listener, and scheduler across the ancestry chain.
 *
 * <p>Each context owns an independent local {@link Store} while sharing configuration, listener,
 * and scheduler state with its ancestry chain.
 */
public final class DefaultContext implements Context {

    private final Context parent;
    private final Map<String, String> configuration;
    private final Listener listener;
    private final AsyncScheduler scheduler;
    private final Store store;

    private DefaultContext(
            final Context parent,
            final Map<String, String> configuration,
            final Listener listener,
            final AsyncScheduler scheduler) {
        this(parent, configuration, listener, scheduler, new DefaultStore());
    }

    private DefaultContext(
            final Context parent,
            final Map<String, String> configuration,
            final Listener listener,
            final AsyncScheduler scheduler,
            final Store store) {
        this.parent = parent;
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    /**
     * Creates a root context with the supplied configuration, listener, and scheduler.
     *
     * @param configuration the immutable configuration properties for this context
     * @param listener the listener receiving lifecycle callbacks
     * @param scheduler the scheduler available to actions
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultContext(
            final DefaultConfiguration configuration, final Listener listener, final AsyncScheduler scheduler) {
        this(
                null,
                Objects.requireNonNull(configuration, "configuration must not be null")
                        .asMap(),
                listener,
                scheduler);
    }

    /**
     * Creates a root context with the supplied configuration map, listener, and scheduler.
     *
     * @param configuration the configuration properties, or {@code null} to load
     *     {@link Configuration#defaultProperties()}
     * @param listener the listener receiving lifecycle callbacks
     * @param scheduler the scheduler available to actions
     * @throws NullPointerException if {@code listener} or {@code scheduler} is {@code null}
     */
    public DefaultContext(
            final Map<String, String> configuration, final Listener listener, final AsyncScheduler scheduler) {
        this(
                null,
                configuration != null ? Map.copyOf(configuration) : Map.copyOf(Configuration.defaultProperties()),
                listener,
                scheduler);
    }

    private DefaultContext(final Context parent) {
        this(
                Objects.requireNonNull(parent, "parent must not be null"),
                parent.getConfiguration(),
                parent.getListener(),
                ((DefaultContext) parent).scheduler);
    }

    DefaultContext withScheduler(final AsyncScheduler scheduler) {
        return new DefaultContext(parent, configuration, listener, scheduler, store);
    }

    @Override
    public Context getParent() {
        if (parent == null) {
            throw AncestorNotFoundException.of("parent does not exist: this context is the root");
        }
        return parent;
    }

    @Override
    public Optional<Context> findParent() {
        return Optional.ofNullable(parent);
    }

    @Override
    public Context getAncestor(final String path) {
        return findAncestor(path)
                .orElseThrow(() -> AncestorNotFoundException.of("ancestor does not exist for path: " + path));
    }

    @Override
    public Optional<Context> findAncestor(final String path) {
        Objects.requireNonNull(path, "path must not be null");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        if ("/".equals(path)) {
            Context current = this;
            while (current.findParent().isPresent()) {
                current = current.findParent().orElseThrow();
            }
            return Optional.of(current);
        }
        String[] segments = path.split("/");
        int hops = 0;
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (".".equals(segment)) {
                throw new IllegalArgumentException("path must not contain '.' segments: " + path);
            }
            if ("..".equals(segment)) {
                hops++;
            } else {
                throw new IllegalArgumentException("path must not contain named segments: " + path);
            }
        }
        Context current = this;
        for (int i = 0; i < hops; i++) {
            Optional<Context> parentContext = current.findParent();
            if (parentContext.isEmpty()) {
                return Optional.empty();
            }
            current = parentContext.orElseThrow();
        }
        return Optional.of(current);
    }

    @Override
    public Map<String, String> getConfiguration() {
        return configuration;
    }

    @Override
    public Listener getListener() {
        return listener;
    }

    @Override
    public Store getStore() {
        return store;
    }

    @Override
    public Context createChild() {
        return new DefaultContext(this);
    }

    @Override
    public CompletableFuture<Result> runAsync(final Action action) {
        Objects.requireNonNull(action, "action must not be null");
        return scheduler.runAsync(action, this);
    }

    @Override
    public String toString() {
        return "Context[" + (parent == null ? "root" : "child") + "]";
    }
}

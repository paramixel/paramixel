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

package org.paramixel.core.spi;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.paramixel.core.Configuration;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Store;
import org.paramixel.core.support.Arguments;

/**
 * Default {@link Context} implementation used by Paramixel runners.
 *
 * <p>Each context owns an independent local {@link Store} while sharing configuration, listener,
 * and executor service state with its ancestry chain.
 */
public final class DefaultContext implements Context {

    private final Context parent;
    private final Map<String, String> configuration;
    private final Listener listener;
    private final ExecutorService executorService;
    private final Store store;

    private DefaultContext(
            final Context parent,
            final Map<String, String> configuration,
            final Listener listener,
            final ExecutorService executorService) {
        this.parent = parent;
        this.configuration =
                configuration != null ? Map.copyOf(configuration) : Map.copyOf(Configuration.defaultProperties());
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.executorService = Objects.requireNonNull(executorService, "executorService must not be null");
        this.store = new DefaultStore();
    }

    public DefaultContext(
            final Map<String, String> configuration, final Listener listener, final ExecutorService executorService) {
        this(null, configuration, listener, executorService);
    }

    private DefaultContext(final Context parent) {
        this(parent, parent.getConfiguration(), parent.getListener(), parent.getExecutorService());
    }

    @Override
    public Optional<Context> getParent() {
        return Optional.ofNullable(parent);
    }

    @Override
    public Optional<Context> findAncestor(final int levelUp) {
        Arguments.requireNonNegative(levelUp, "levelUp must be non-negative");
        Context current = this;
        for (int i = 0; i < levelUp; i++) {
            Optional<Context> parentContext = current.getParent();
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
    public ExecutorService getExecutorService() {
        return executorService;
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
    public String toString() {
        return "Context[" + (parent == null ? "root" : "child") + "]";
    }
}

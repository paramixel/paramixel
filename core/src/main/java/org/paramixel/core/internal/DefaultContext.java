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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.paramixel.core.Attachment;
import org.paramixel.core.Configuration;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;

/**
 * The default internal implementation of {@link Context}.
 */
public final class DefaultContext implements Context {

    private final DefaultContext parent;
    private Object attachment;
    private final Map<String, String> configuration;
    private final Listener listener;
    private final ExecutorService executorService;

    public DefaultContext(Map<String, String> configuration, Listener listener, ExecutorService executorService) {
        this(null, configuration, listener, executorService);
    }

    public DefaultContext(
            DefaultContext parent,
            Map<String, String> configuration,
            Listener listener,
            ExecutorService executorService) {
        this.parent = parent;
        this.configuration = configuration != null ? Map.copyOf(configuration) : Configuration.defaultProperties();
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.executorService = Objects.requireNonNull(executorService, "executorService must not be null");
    }

    public DefaultContext(DefaultContext parent) {
        this(parent, parent.configuration, parent.listener, parent.executorService);
    }

    @Override
    public Optional<Context> getParent() {
        return Optional.ofNullable(parent);
    }

    @Override
    public <T> Context setAttachment(T attachment) {
        this.attachment = attachment;
        return this;
    }

    @Override
    public Optional<Attachment> getAttachment() {
        if (attachment == null) {
            return Optional.empty();
        }

        return Optional.of(new DefaultAttachment(attachment));
    }

    @Override
    public Optional<Attachment> removeAttachment() {
        if (attachment == null) {
            return Optional.empty();
        }

        Object removed = attachment;
        attachment = null;
        return Optional.of(new DefaultAttachment(removed));
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
    public Optional<Context> findContext(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("level must not be negative: " + level);
        }

        Context current = this;
        for (int i = 0; i < level; i++) {
            Optional<Context> parent = current.getParent();
            if (parent.isEmpty()) {
                throw new NoSuchElementException("Context ancestor not found at level " + level);
            }
            current = parent.get();
        }

        return Optional.of(current);
    }

    @Override
    public Optional<Attachment> findAttachment(int level) {
        return findContext(level).flatMap(Context::getAttachment);
    }

    @Override
    public String toString() {
        return "DefaultContext[" + (parent == null ? "root" : "child") + "]";
    }
}

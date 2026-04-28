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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;

/**
 * The default implementation of {@link Context}.
 */
public final class DefaultContext implements Context {

    private final Action action;
    private final Context parent;
    private Object attachment;
    private final Runner runner;
    private final Listener listener;

    DefaultContext(Action action, Context parent, Runner runner) {
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.parent = parent;
        this.attachment = null;
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.listener = runner.listener();
    }

    /**
     * Creates a root context.
     *
     * @param action The action.
     * @param runner The runner.
     * @return A new DefaultContext.
     */
    public static DefaultContext create(Action action, Runner runner) {
        return new DefaultContext(action, null, runner);
    }

    @Override
    public Optional<Context> parent() {
        return Optional.ofNullable(parent);
    }

    @Override
    public Action action() {
        return action;
    }

    @Override
    public <T> Context setAttachment(T attachment) {
        this.attachment = attachment;
        return this;
    }

    @Override
    public <T> Optional<T> attachment(Class<T> type) {
        if (attachment == null || !type.isInstance(attachment)) {
            return Optional.empty();
        }
        return Optional.of(type.cast(attachment));
    }

    @Override
    public Optional<Object> removeAttachment() {
        Object removed = this.attachment;
        this.attachment = null;
        return Optional.ofNullable(removed);
    }

    @Override
    public Context createChild(Action child) {
        return new DefaultContext(child, this, runner);
    }

    @Override
    public Result execute(Action child) {
        return child.execute(createChild(child));
    }

    @Override
    public CompletableFuture<Result> executeAsync(Action child) {
        if (!(runner instanceof DefaultRunner defaultRunner)) {
            CompletableFuture<Result> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("ExecutorService access requires DefaultRunner"));
            return future;
        }
        int parallelism = Integer.parseInt(
                runner.configuration().getOrDefault(org.paramixel.core.Configuration.RUNNER_PARALLELISM, "4"));
        java.util.concurrent.ExecutorService executorService = defaultRunner.getOrCreateExecutorService(parallelism);
        return CompletableFuture.supplyAsync(() -> child.execute(createChild(child)), executorService);
    }

    @Override
    public void beforeAction(Context context, Action action) {
        listener.beforeAction(context, action);
    }

    @Override
    public void afterAction(Context context, Action action, Result result) {
        listener.afterAction(context, action, result);
    }

    @Override
    public String toString() {
        return "DefaultContext[action=" + action.name() + "]";
    }
}

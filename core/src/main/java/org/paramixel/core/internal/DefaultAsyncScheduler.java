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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.core.Action;
import org.paramixel.core.AsyncScheduler;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Status;
import org.paramixel.core.Store;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Parallel;

/**
 * Default scheduler implementation that owns all Paramixel worker execution.
 */
public final class DefaultAsyncScheduler implements AsyncScheduler, AutoCloseable {

    private static final String THREAD_NAME = "paramixel-scheduler";

    private final ExecutorService executorService;
    private final int globalParallelism;
    private final ArrayDeque<RunnableTask> ready = new ArrayDeque<>();
    private int running;

    /**
     * Creates a scheduler using the configured default parallelism.
     *
     * @param configuration the configuration used to resolve worker concurrency
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public DefaultAsyncScheduler(DefaultConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        this.globalParallelism = configuration.resolveParallelism();
        this.executorService = createExecutorService(globalParallelism);
    }

    @Override
    public CompletableFuture<Result> runAsync(Action action, Context context) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(context, "context must not be null");
        return schedule(action, context);
    }

    @Override
    public void close() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private CompletableFuture<Result> schedule(Action action, Context parentContext) {
        if (action instanceof Parallel parallel) {
            return scheduleParallel(parallel, parentContext);
        }
        if (action instanceof Container container) {
            return scheduleContainer(container, parentContext);
        }
        return scheduleExecutable(action, parentContext);
    }

    private CompletableFuture<Result> scheduleExecutable(Action action, Context context) {
        var future = new CompletableFuture<Result>();
        enqueue(new RunnableTask(action, context, future));
        return future;
    }

    private void enqueue(RunnableTask task) {
        synchronized (this) {
            ready.addLast(task);
            drainLocked();
        }
    }

    private void drain() {
        synchronized (this) {
            drainLocked();
        }
    }

    private void drainLocked() {
        while (running < globalParallelism && !ready.isEmpty()) {
            RunnableTask task = ready.removeFirst();
            running++;
            executorService.execute(() -> executeTask(task));
        }
    }

    private void executeTask(RunnableTask task) {
        try {
            task.future().complete(task.action().execute(task.context()));
        } catch (OutOfMemoryError | StackOverflowError e) {
            task.future().completeExceptionally(e);
        } catch (Error e) {
            task.future().complete(failureResult(task.action(), task.context(), e));
        } catch (Throwable t) {
            task.future().completeExceptionally(t);
        } finally {
            synchronized (this) {
                running--;
                drainLocked();
            }
        }
    }

    private CompletableFuture<Result> scheduleParallel(Parallel parallel, Context parentContext) {
        Context context = contextFor(parallel, parentContext);
        SchedulerOverride override = parallel;
        Context baseContext = context;
        context = override.schedulerOverride()
                .map(scheduler -> withScheduler(baseContext, scheduler))
                .orElse(context);
        var result = new DefaultResult(parallel);
        Listener listener = context.getListener();
        listener.beforeAction(result);
        Instant start = Instant.now();
        var children = parallel.getChildren();
        var completion = new CompletableFuture<Result>();
        var state = new ParallelState(parallel, context, result, completion, start, children);
        state.drain();
        return completion;
    }

    private CompletableFuture<Result> scheduleContainer(Container container, Context parentContext) {
        Context context = contextFor(container, parentContext);
        var result = new DefaultResult(container);
        Listener listener = context.getListener();
        listener.beforeAction(result);
        Instant start = Instant.now();
        var completion = new CompletableFuture<Result>();
        var state = new ContainerState(container, context, result, completion, start);
        state.start();
        return completion;
    }

    private Context contextFor(Action action, Context context) {
        return action.getContextMode() == Action.ContextMode.ISOLATED ? context.createChild() : context;
    }

    private Context withScheduler(Context context, AsyncScheduler scheduler) {
        if (context instanceof DefaultContext defaultContext) {
            return defaultContext.withScheduler(scheduler);
        }
        return new SchedulerContext(context, scheduler);
    }

    private Result failureResult(Action action, Context context, Throwable throwable) {
        var result = new DefaultResult(action);
        context.getListener().actionThrowable(result, throwable);
        result.complete(new DefaultStatus(DefaultStatus.Kind.FAILURE, throwable), Duration.ZERO);
        context.getListener().afterAction(result);
        return result;
    }

    private static ExecutorService createExecutorService(int parallelism) {
        var counter = new AtomicInteger(1);
        var threadPoolExecutor = new ThreadPoolExecutor(
                parallelism, parallelism, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, THREAD_NAME + "-" + counter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                });
        threadPoolExecutor.prestartAllCoreThreads();
        return threadPoolExecutor;
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private static Status computeStatus(List<Result> results) {
        for (Result childResult : results) {
            var status = childResult.getStatus();
            if (status.isFailure()) {
                return status;
            }
        }
        for (Result childResult : results) {
            var status = childResult.getStatus();
            if (status.isSkip()) {
                return status;
            }
        }
        return DefaultStatus.PASS;
    }

    private record RunnableTask(Action action, Context context, CompletableFuture<Result> future) {}

    private record SchedulerContext(Context delegate, AsyncScheduler scheduler) implements Context {

        private SchedulerContext {
            Objects.requireNonNull(delegate, "delegate must not be null");
            Objects.requireNonNull(scheduler, "scheduler must not be null");
        }

        @Override
        public java.util.Map<String, String> getConfiguration() {
            return delegate.getConfiguration();
        }

        @Override
        public java.util.Optional<Context> getParent() {
            return delegate.getParent();
        }

        @Override
        public Store getStore() {
            return delegate.getStore();
        }

        @Override
        public Listener getListener() {
            return delegate.getListener();
        }

        @Override
        public CompletableFuture<Result> runAsync(Action action) {
            return scheduler.runAsync(action, this);
        }

        @Override
        public java.util.Optional<Context> findAncestor(int levelUp) {
            return delegate.findAncestor(levelUp);
        }

        @Override
        public Context createChild() {
            return new SchedulerContext(delegate.createChild(), scheduler);
        }
    }

    private final class ParallelState {
        private final Parallel parallel;
        private final Context context;
        private final DefaultResult result;
        private final CompletableFuture<Result> completion;
        private final Instant start;
        private final List<Action> children;
        private int nextIndex;
        private int active;
        private int completed;
        private boolean failed;
        private boolean done;

        private ParallelState(
                Parallel parallel,
                Context context,
                DefaultResult result,
                CompletableFuture<Result> completion,
                Instant start,
                List<Action> children) {
            this.parallel = parallel;
            this.context = context;
            this.result = result;
            this.completion = completion;
            this.start = start;
            this.children = children;
        }

        private void drain() {
            List<Action> toSchedule = new ArrayList<>();
            synchronized (this) {
                while (!failed && active < parallel.getParallelism() && nextIndex < children.size()) {
                    active++;
                    toSchedule.add(children.get(nextIndex++));
                }
                if (completed == children.size()) {
                    complete();
                    return;
                }
            }
            for (Action child : toSchedule) {
                context.runAsync(child).whenComplete((childResult, throwable) -> {
                    Throwable unwrapped = throwable == null ? null : unwrap(throwable);
                    if (unwrapped instanceof OutOfMemoryError || unwrapped instanceof StackOverflowError) {
                        completion.completeExceptionally(unwrapped);
                        return;
                    }
                    synchronized (this) {
                        active--;
                        completed++;
                        if (done) {
                            return;
                        }
                        if (unwrapped != null) {
                            failed = true;
                            result.addChild(failureResult(child, context, unwrapped));
                            completeExceptionally(unwrapped);
                            return;
                        } else {
                            result.addChild(childResult);
                        }
                    }
                    drain();
                    DefaultAsyncScheduler.this.drain();
                });
            }
        }

        private void complete() {
            if (done) {
                return;
            }
            done = true;
            result.complete(computeStatus(result.getChildren()), Duration.between(start, Instant.now()));
            context.getListener().afterAction(result);
            completion.complete(result);
        }

        private void completeExceptionally(Throwable throwable) {
            if (done) {
                return;
            }
            done = true;
            result.complete(computeStatus(result.getChildren()), Duration.between(start, Instant.now()));
            context.getListener().afterAction(result);
            completion.completeExceptionally(throwable);
        }
    }

    private final class ContainerState {
        private final Container container;
        private final Context context;
        private final DefaultResult result;
        private final CompletableFuture<Result> completion;
        private final Instant start;
        private final List<Result> statusResults = new ArrayList<>();
        private final List<Action> bodyChildren;

        private ContainerState(
                Container container,
                Context context,
                DefaultResult result,
                CompletableFuture<Result> completion,
                Instant start) {
            this.container = container;
            this.context = context;
            this.result = result;
            this.completion = completion;
            this.start = start;
            this.bodyChildren = container.orderedBodyChildren();
        }

        private void start() {
            if (container.getBefore().isPresent()) {
                scheduleChild(container.getBefore().orElseThrow(), beforeResult -> {
                    if (beforeResult.getStatus().isFailure()
                            || beforeResult.getStatus().isSkip()) {
                        skipBodyThenAfter();
                    } else {
                        runBody(0);
                    }
                });
            } else {
                runBody(0);
            }
        }

        private void runBody(int index) {
            if (index >= bodyChildren.size()) {
                runAfter();
                return;
            }
            Action child = bodyChildren.get(index);
            scheduleChild(child, childResult -> {
                var childStatus = childResult.getStatus();
                if (container.getPolicy().childMode() == Container.ChildMode.DEPENDENT
                        && (childStatus.isFailure() || childStatus.isSkip())) {
                    for (Action remaining : bodyChildren.subList(index + 1, bodyChildren.size())) {
                        Result skipResult = remaining.skip(context);
                        result.addChild(skipResult);
                        statusResults.add(skipResult);
                    }
                    runAfter();
                } else {
                    runBody(index + 1);
                }
            });
        }

        private void skipBodyThenAfter() {
            for (Action child : bodyChildren) {
                Result skipResult = child.skip(context);
                result.addChild(skipResult);
                statusResults.add(skipResult);
            }
            runAfter();
        }

        private void runAfter() {
            if (container.getAfter().isPresent()) {
                scheduleChild(container.getAfter().orElseThrow(), ignored -> complete());
            } else {
                complete();
            }
        }

        private void scheduleChild(Action child, java.util.function.Consumer<Result> next) {
            DefaultAsyncScheduler.this.schedule(child, context).whenComplete((childResult, throwable) -> {
                Throwable unwrapped = throwable == null ? null : unwrap(throwable);
                if (unwrapped instanceof OutOfMemoryError || unwrapped instanceof StackOverflowError) {
                    completion.completeExceptionally(unwrapped);
                    return;
                }
                Result resolved = unwrapped == null ? childResult : failureResult(child, context, unwrapped);
                result.addChild(resolved);
                statusResults.add(resolved);
                next.accept(resolved);
                DefaultAsyncScheduler.this.drain();
            });
        }

        private void complete() {
            result.complete(computeStatus(statusResults), Duration.between(start, Instant.now()));
            context.getListener().afterAction(result);
            completion.complete(result);
        }
    }
}

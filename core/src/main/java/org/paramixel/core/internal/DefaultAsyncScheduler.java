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
 * Schedules action runs on a bounded thread pool, enforcing global and per-parallel parallelism limits.
 */
public final class DefaultAsyncScheduler implements AsyncScheduler, AutoCloseable {

    private static final String THREAD_NAME = "paramixel-scheduler";

    private final ExecutorService executorService;
    private final int globalParallelism;
    private final ArrayDeque<RunnableTask> ready = new ArrayDeque<>();
    private final ArrayDeque<Runnable> permitWaiters = new ArrayDeque<>();
    private final ThreadLocal<Boolean> schedulerWorker = ThreadLocal.withInitial(() -> false);
    private int running;
    private int activePermits;

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
        if (globalParallelism == 1 && schedulerWorker.get()) {
            return executeInline(action, context);
        }
        return schedule(action, context, null);
    }

    /**
     * Shuts down the worker pool, waiting up to 5 seconds before forcing termination.
     */
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

    private CompletableFuture<Result> schedule(Action action, Context parentContext, Permit permit) {
        if (action instanceof Parallel parallel) {
            return scheduleParallel(parallel, parentContext, permit);
        }
        if (action instanceof Container container) {
            return scheduleContainer(container, parentContext, permit);
        }
        return scheduleExecutable(action, parentContext, permit);
    }

    private CompletableFuture<Result> scheduleExecutable(Action action, Context context, Permit permit) {
        var future = new CompletableFuture<Result>();
        enqueue(new RunnableTask(action, context, future));
        return future;
    }

    private CompletableFuture<Result> executeInline(Action action, Context context) {
        try {
            return CompletableFuture.completedFuture(action.run(context));
        } catch (OutOfMemoryError | StackOverflowError e) {
            return CompletableFuture.failedFuture(e);
        } catch (Error e) {
            return CompletableFuture.completedFuture(failureResult(action, context, e));
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
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
        schedulerWorker.set(true);
        try {
            task.future().complete(task.action().run(task.context()));
        } catch (OutOfMemoryError | StackOverflowError e) {
            task.future().completeExceptionally(e);
        } catch (Error e) {
            task.future().complete(failureResult(task.action(), task.context(), e));
        } catch (Throwable t) {
            task.future().completeExceptionally(t);
        } finally {
            schedulerWorker.remove();
            synchronized (this) {
                running--;
                drainLocked();
            }
        }
    }

    private CompletableFuture<Result> scheduleParallel(Parallel parallel, Context parentContext, Permit permit) {
        SchedulerOverride override = parallel;
        boolean usesDefaultScheduler = override.schedulerOverride().isEmpty();
        Context context = override.schedulerOverride()
                .map(scheduler -> withScheduler(parentContext, scheduler))
                .orElse(parentContext);
        var result = new DefaultResult(parallel);
        Listener listener = context.getListener();
        listener.beforeAction(result);
        Instant start = Instant.now();
        var children = parallel.getChildren();
        var completion = new CompletableFuture<Result>();
        var state =
                new ParallelState(parallel, context, result, completion, start, children, permit, usesDefaultScheduler);
        state.drain();
        return completion;
    }

    private CompletableFuture<Result> scheduleContainer(Container container, Context parentContext, Permit permit) {
        var result = new DefaultResult(container);
        Listener listener = parentContext.getListener();
        listener.beforeAction(result);
        Instant start = Instant.now();
        var completion = new CompletableFuture<Result>();
        var state = new ContainerState(container, parentContext, result, completion, start, permit);
        state.start();
        return completion;
    }

    private Permit tryAcquirePermit() {
        synchronized (this) {
            if (activePermits >= globalParallelism) {
                return null;
            }
            activePermits++;
            return new Permit();
        }
    }

    private void releasePermit(Permit permit) {
        if (permit == null) {
            return;
        }
        Runnable waiter;
        synchronized (this) {
            activePermits--;
            waiter = permitWaiters.pollFirst();
        }
        if (waiter != null) {
            waiter.run();
        }
    }

    private void awaitPermit(Runnable waiter) {
        synchronized (this) {
            permitWaiters.addLast(waiter);
        }
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

    private static final class Permit {}

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
        public Context getParent() {
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
        public java.util.Optional<Context> findParent() {
            return delegate.findParent();
        }

        @Override
        public Context getAncestor(String path) {
            return delegate.getAncestor(path);
        }

        @Override
        public java.util.Optional<Context> findAncestor(String path) {
            return delegate.findAncestor(path);
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
        private final Permit inheritedPermit;
        private final boolean usesDefaultScheduler;
        private int nextIndex;
        private int active;
        private int completed;
        private boolean inheritedPermitAvailable;
        private boolean failed;
        private boolean done;
        private boolean waitingForPermit;

        private ParallelState(
                Parallel parallel,
                Context context,
                DefaultResult result,
                CompletableFuture<Result> completion,
                Instant start,
                List<Action> children,
                Permit inheritedPermit,
                boolean usesDefaultScheduler) {
            this.parallel = parallel;
            this.context = context;
            this.result = result;
            this.completion = completion;
            this.start = start;
            this.children = children;
            this.inheritedPermit = inheritedPermit;
            this.inheritedPermitAvailable = inheritedPermit != null;
            this.usesDefaultScheduler = usesDefaultScheduler;
        }

        private void drain() {
            List<AdmittedChild> toSchedule = new ArrayList<>();
            synchronized (this) {
                while (!failed && active < parallel.getParallelism() && nextIndex < children.size()) {
                    Permit permit = admitPermit();
                    if (usesDefaultScheduler && permit == null) {
                        if (!waitingForPermit) {
                            waitingForPermit = true;
                            awaitPermit(this::drain);
                        }
                        break;
                    }
                    waitingForPermit = false;
                    active++;
                    toSchedule.add(new AdmittedChild(children.get(nextIndex++), permit));
                }
                if (completed == children.size()) {
                    complete();
                    return;
                }
            }
            for (AdmittedChild admittedChild : toSchedule) {
                Action child = admittedChild.action();
                scheduleChild(child, admittedChild.permit()).whenComplete((childResult, throwable) -> {
                    Throwable unwrapped = throwable == null ? null : unwrap(throwable);
                    if (unwrapped instanceof OutOfMemoryError || unwrapped instanceof StackOverflowError) {
                        releaseChildPermit(admittedChild.permit());
                        completion.completeExceptionally(unwrapped);
                        return;
                    }
                    boolean continueDraining;
                    synchronized (this) {
                        active--;
                        completed++;
                        if (done) {
                            continueDraining = false;
                        } else if (unwrapped != null) {
                            failed = true;
                            result.addChild(failureResult(child, context, unwrapped));
                            completeExceptionally(unwrapped);
                            continueDraining = false;
                        } else {
                            result.addChild(childResult);
                            continueDraining = true;
                        }
                    }
                    releaseChildPermit(admittedChild.permit());
                    if (!continueDraining) {
                        return;
                    }
                    drain();
                    DefaultAsyncScheduler.this.drain();
                });
            }
        }

        private CompletableFuture<Result> scheduleChild(Action child, Permit permit) {
            Context childContext = context.createChild();
            if (!usesDefaultScheduler) {
                return childContext.runAsync(child);
            }
            return DefaultAsyncScheduler.this.schedule(child, childContext, permit);
        }

        private Permit admitPermit() {
            if (!usesDefaultScheduler) {
                return null;
            }
            if (inheritedPermitAvailable) {
                inheritedPermitAvailable = false;
                return inheritedPermit;
            }
            return tryAcquirePermit();
        }

        private void releaseChildPermit(Permit permit) {
            if (!usesDefaultScheduler || permit == null) {
                return;
            }
            if (permit == inheritedPermit) {
                inheritedPermitAvailable = true;
            } else {
                releasePermit(permit);
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

    private record AdmittedChild(Action action, Permit permit) {}

    private final class ContainerState {
        private final Container container;
        private final Context context;
        private final DefaultResult result;
        private final CompletableFuture<Result> completion;
        private final Instant start;
        private final List<Result> statusResults = new ArrayList<>();
        private final List<Action> bodyChildren;
        private final Permit permit;

        private ContainerState(
                Container container,
                Context context,
                DefaultResult result,
                CompletableFuture<Result> completion,
                Instant start,
                Permit permit) {
            this.container = container;
            this.context = context;
            this.result = result;
            this.completion = completion;
            this.start = start;
            this.bodyChildren = container.orderedBodyChildren();
            this.permit = permit;
        }

        private void start() {
            if (container.getBefore().isPresent()) {
                scheduleLifecycleChild(container.getBefore().orElseThrow(), beforeResult -> {
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
            scheduleBodyChild(child, childResult -> {
                var childStatus = childResult.getStatus();
                if (container.getPolicy().childMode() == Container.ChildMode.DEPENDENT
                        && (childStatus.isFailure() || childStatus.isSkip())) {
                    for (Action remaining : bodyChildren.subList(index + 1, bodyChildren.size())) {
                        Result skipResult = remaining.skip(context.createChild());
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
                Result skipResult = child.skip(context.createChild());
                result.addChild(skipResult);
                statusResults.add(skipResult);
            }
            runAfter();
        }

        private void runAfter() {
            if (container.getAfter().isPresent()) {
                scheduleLifecycleChild(container.getAfter().orElseThrow(), ignored -> complete());
            } else {
                complete();
            }
        }

        private void scheduleLifecycleChild(Action child, java.util.function.Consumer<Result> next) {
            DefaultAsyncScheduler.this.schedule(child, context, permit).whenComplete((childResult, throwable) -> {
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

        private void scheduleBodyChild(Action child, java.util.function.Consumer<Result> next) {
            Context childContext = context.createChild();
            DefaultAsyncScheduler.this.schedule(child, childContext, permit).whenComplete((childResult, throwable) -> {
                Throwable unwrapped = throwable == null ? null : unwrap(throwable);
                if (unwrapped instanceof OutOfMemoryError || unwrapped instanceof StackOverflowError) {
                    completion.completeExceptionally(unwrapped);
                    return;
                }
                Result resolved = unwrapped == null ? childResult : failureResult(child, childContext, unwrapped);
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

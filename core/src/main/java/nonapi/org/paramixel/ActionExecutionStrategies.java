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

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.action.StatusAccumulator;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Context;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Assert;
import org.paramixel.api.action.Conditional;
import org.paramixel.api.action.Delay;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Repeat;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Static;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;
import org.paramixel.api.action.Until;

@SuppressWarnings({"deprecation", "removal"})
final class ActionExecutionStrategies {

    private static final Map<Class<? extends Action>, ActionExecutionStrategy<? extends Action>> STRATEGIES =
            Map.ofEntries(
                    strategy(Step.class, ActionExecutionStrategies::runStep),
                    strategy(Assert.class, ActionExecutionStrategies::runAssert),
                    strategy(Delay.class, ActionExecutionStrategies::runDelay),
                    strategy(Parallel.class, ActionExecutionStrategies::runParallel),
                    strategy(Timeout.class, ActionExecutionStrategies::runTimeout),
                    strategy(Sequence.class, ActionExecutionStrategies::runSequence),
                    strategy(Sequential.class, ActionExecutionStrategies::runSequential),
                    strategy(Loop.class, ActionExecutionStrategies::runLoop),
                    strategy(Repeat.class, ActionExecutionStrategies::runRepeat),
                    strategy(Until.class, ActionExecutionStrategies::runUntil),
                    strategy(Scope.class, ActionExecutionStrategies::runLifecycle),
                    strategy(Static.class, ActionExecutionStrategies::runLifecycle),
                    strategy(Instance.class, ActionExecutionStrategies::runInstance),
                    strategy(Isolated.class, ActionExecutionStrategies::runIsolated),
                    strategy(Conditional.class, ActionExecutionStrategies::runConditional));

    private ActionExecutionStrategies() {}

    static Status execute(final Action action, final ConcreteContext context) {
        Objects.requireNonNull(action, "action is null");
        Objects.requireNonNull(context, "context is null");
        var strategy = STRATEGIES.get(action.getClass());
        if (strategy == null) {
            return runDependentChildren(context, true);
        }
        return execute(strategy, action, context);
    }

    static boolean supports(final Class<? extends Action> type) {
        return STRATEGIES.containsKey(Objects.requireNonNull(type, "type is null"));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Action> Status execute(
            final ActionExecutionStrategy<T> strategy, final Action action, final ConcreteContext context) {
        return strategy.execute((T) action, context);
    }

    private static <T extends Action>
            Map.Entry<Class<? extends Action>, ActionExecutionStrategy<? extends Action>> strategy(
                    final Class<T> type, final ActionExecutionStrategy<T> strategy) {
        return Map.entry(type, strategy);
    }

    private static Status runStep(final Step step, final ConcreteContext context) {
        try {
            step.throwableConsumer().accept(context);
            return Status.PASSED;
        } catch (Throwable t) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (t instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(t);
        }
    }

    private static Status runAssert(final Assert assertAction, final ConcreteContext context) {
        try {
            assertAction.throwableConsumer().accept(context);
            return Status.PASSED;
        } catch (Throwable t) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (t instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(t);
        }
    }

    private static Status runDelay(final Delay delay, final ConcreteContext context) {
        try {
            delay.throwableConsumer().accept(context);
            return Status.PASSED;
        } catch (Throwable t) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (t instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(t);
        }
    }

    private static Status runSequence(final Sequence sequence, final ConcreteContext context) {
        return runDependentChildren(context, sequence.isDependent());
    }

    private static Status runSequential(final Sequential sequential, final ConcreteContext context) {
        return runDependentChildren(context, sequential.isDependent());
    }

    private static Status runDependentChildren(final ConcreteContext context, final boolean dependent) {
        var aggregated = new StatusAccumulator();
        var mode = ExecutionMode.RUN;

        for (var descriptor : context.descriptor().children()) {
            var childResult = context.runChild(descriptor, mode);
            aggregated.include(childResult);

            if (mode == ExecutionMode.RUN && dependent && !childResult.isPassed() && !childResult.isAborted()) {
                mode = ExecutionMode.SKIP;
            }
        }

        return aggregated.status();
    }

    private static Status runLifecycle(final Action ignored, final ConcreteContext context) {
        var descriptor = context.descriptor();
        var aggregated = new StatusAccumulator();
        var runChildren = true;

        try {
            if (descriptor.before().isPresent()) {
                var beforeDescriptor = descriptor.before().get();
                try {
                    var beforeResult = context.runChild(beforeDescriptor);
                    aggregated.include(beforeResult);
                    if (!beforeResult.isPassed()) {
                        runChildren = false;
                    }
                } catch (RuntimeException e) {
                    var failedStatus = Status.failed(e.getMessage(), e);
                    Arguments.requireInstanceOf(
                                    beforeDescriptor,
                                    MutableDescriptor.class,
                                    "before descriptor must be a MutableDescriptor")
                            .setStatus(failedStatus);
                    aggregated.include(failedStatus);
                    runChildren = false;
                }
            }

            if (runChildren) {
                descriptor.children().forEach(child -> aggregated.include(context.runChild(child)));
            } else {
                descriptor.children().forEach(child -> {
                    aggregated.include(context.runChild(child, ExecutionMode.SKIP));
                });
            }

        } finally {
            descriptor.after().ifPresent(afterDescriptor -> {
                try {
                    aggregated.include(context.runChild(afterDescriptor));
                } catch (RuntimeException e) {
                    var failedStatus = Status.failed(e.getMessage(), e);
                    Arguments.requireInstanceOf(
                                    afterDescriptor,
                                    MutableDescriptor.class,
                                    "after descriptor must be a MutableDescriptor")
                            .setStatus(failedStatus);
                    aggregated.include(failedStatus);
                }
            });
        }

        return aggregated.status();
    }

    private static Status runInstance(final Instance instance, final ConcreteContext context) {
        return runLifecycle(instance, context.withInstanceHolder(new InstanceHolder()));
    }

    private static Status runIsolated(final Isolated isolated, final ConcreteContext context) {
        var lock = context.scheduler().getLock(isolated.lockName());
        lock.lock();
        try {
            var child = Arguments.requireInstanceOf(
                    context.descriptor().children().get(0),
                    MutableDescriptor.class,
                    "child descriptor must be a MutableDescriptor");
            context.runChild(child, ExecutionMode.RUN);
            return child.status();
        } finally {
            lock.unlock();
        }
    }

    private static Status runConditional(final Conditional conditional, final ConcreteContext context) {
        boolean conditionResult;
        try {
            conditionResult = conditional.condition().test(context);
        } catch (Throwable t) {
            context.descriptor().setStatus(Status.failed("condition evaluation failed: " + t.getMessage(), t));
            context.runChildren(ExecutionMode.SKIP);
            return Status.FAILED;
        }

        if (!conditionResult) {
            context.descriptor().setStatus(Status.PASSED);
            context.runChildren(ExecutionMode.SKIP);
            return Status.PASSED;
        }

        var child = Arguments.requireInstanceOf(
                context.descriptor().children().get(0),
                MutableDescriptor.class,
                "child descriptor must be a MutableDescriptor");
        context.runChild(child, ExecutionMode.RUN);
        return child.status();
    }

    private static Status runLoop(final Loop loopAction, final ConcreteContext context) {
        var children = context.descriptor().children();

        if (loopAction.until().isEmpty()) {
            var aggregated = new StatusAccumulator();
            for (int i = 0; i < children.size(); i++) {
                var child = children.get(i);
                var childResult = context.runChild(child, ExecutionMode.RUN);
                aggregated.include(childResult);
                if (childResult.isAborted()) {
                    for (int j = i + 1; j < children.size(); j++) {
                        context.runChild(children.get(j), ExecutionMode.SKIP);
                    }
                    return Status.ABORTED;
                }
                if (i < children.size() - 1) {
                    Status delayResult = applyLoopDelay(loopAction, i + 1);
                    if (delayResult != null) {
                        for (int j = i + 1; j < children.size(); j++) {
                            context.runChild(children.get(j), ExecutionMode.SKIP);
                        }
                        return delayResult;
                    }
                }
            }
            return aggregated.status();
        }

        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            var childResult = context.runChild(child, ExecutionMode.RUN);

            if (childResult.isAborted()) {
                for (int j = i + 1; j < children.size(); j++) {
                    context.runChild(children.get(j), ExecutionMode.SKIP);
                }
                return Status.ABORTED;
            }

            boolean satisfied = evaluateUntilPredicate(loopAction.until().get(), context);

            if (satisfied) {
                for (int j = i + 1; j < children.size(); j++) {
                    context.runChild(children.get(j), ExecutionMode.SKIP);
                }
                return Status.PASSED;
            }

            if (i < children.size() - 1) {
                Status delayResult = applyLoopDelay(loopAction, i + 1);
                if (delayResult != null) {
                    for (int j = i + 1; j < children.size(); j++) {
                        context.runChild(children.get(j), ExecutionMode.SKIP);
                    }
                    return delayResult;
                }
            }
        }

        return Status.FAILED;
    }

    /**
     * Applies the inter-iteration delay if configured.
     *
     * @param loopAction the loop action
     * @param completedIteration 1-based number of the iteration just completed
     * @return {@code null} on success, or {@link Status#ABORTED} if the delay thread was interrupted
     */
    private static Status applyLoopDelay(final Loop loopAction, final int completedIteration) {
        if (loopAction.delay().isEmpty()) {
            return null;
        }
        long delayMs =
                loopAction.delay().get().delayForIteration(completedIteration).toMillis();
        if (delayMs <= 0) {
            return null;
        }
        try {
            Thread.sleep(delayMs);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Status.ABORTED;
        }
    }

    private static Status runRepeat(final Repeat ignored, final ConcreteContext context) {

        var aggregated = new StatusAccumulator();
        for (var child : context.descriptor().children()) {
            aggregated.include(context.runChild(child));
        }
        return aggregated.status();
    }

    private static Status runUntil(final Until untilAction, final ConcreteContext context) {
        var children = context.descriptor().children();

        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            var childResult = context.runChild(child, ExecutionMode.RUN);

            if (childResult.isAborted()) {
                for (int j = i + 1; j < children.size(); j++) {
                    context.runChild(children.get(j), ExecutionMode.SKIP);
                }
                return Status.ABORTED;
            }

            boolean satisfied;
            if (untilAction.until().isPresent()) {
                satisfied = evaluateUntilPredicate(untilAction.until().get(), context);
            } else {
                satisfied = childResult.isPassed();
            }

            if (satisfied) {
                for (int j = i + 1; j < children.size(); j++) {
                    context.runChild(children.get(j), ExecutionMode.SKIP);
                }
                return Status.PASSED;
            }
        }

        return Status.FAILED;
    }

    private static boolean evaluateUntilPredicate(final Predicate<Context> predicate, final ConcreteContext context) {
        try {
            return predicate.test(context);
        } catch (Throwable t) {
            return false;
        }
    }

    private static Status runParallel(final Parallel parallel, final ConcreteContext context) {
        var descriptor = context.descriptor();
        var children = descriptor.children();
        if (children.isEmpty()) {
            return Status.PASSED;
        }
        var effectiveParallelism =
                Math.min(parallel.parallelism(), context.scheduler().parallelism());
        effectiveParallelism =
                Math.min(effectiveParallelism, context.scheduler().queueCapacity());
        effectiveParallelism = Math.max(1, effectiveParallelism);

        var activeFutures = new ArrayDeque<CompletableFuture<Descriptor>>();
        var childIterator = children.iterator();

        // Fill initial window
        for (var i = 0; i < effectiveParallelism && childIterator.hasNext(); i++) {
            var child = Arguments.requireInstanceOf(
                    childIterator.next(), MutableDescriptor.class, "child must be a MutableDescriptor");
            activeFutures.add(context.scheduleAsync(child));
        }

        // Rolling window: replace completed futures immediately
        while (!activeFutures.isEmpty()) {
            try {
                context.scheduler().managedJoinWaitAny(activeFutures);
            } catch (CompletionException ignored) {
                // At least one future completed exceptionally — find it below
            }
            // Find any completed future (successfully or exceptionally) and remove it
            CompletableFuture<Descriptor> completedFuture = null;
            for (var future : activeFutures) {
                if (future.isDone()) {
                    completedFuture = future;
                    break;
                }
            }
            if (completedFuture == null) {
                // Should not happen — managedJoinWaitAny returned, so a future must be done
                break;
            }
            activeFutures.remove(completedFuture);

            // Schedule next child if available
            if (childIterator.hasNext()) {
                var child = Arguments.requireInstanceOf(
                        childIterator.next(), MutableDescriptor.class, "child must be a MutableDescriptor");
                activeFutures.add(context.scheduleAsync(child));
            }
        }

        // Aggregate statuses from all children (read descriptor stored status, not future)
        var aggregated = new StatusAccumulator();
        for (var child : children) {
            aggregated.include(child);
        }
        return aggregated.status();
    }

    private static Status runTimeout(final Timeout timeout, final ConcreteContext context) {
        var childDescriptor = Arguments.requireInstanceOf(
                context.descriptor().children().get(0),
                MutableDescriptor.class,
                "child descriptor must be a MutableDescriptor");

        var childFuture = context.scheduleAsync(childDescriptor);
        var timedFuture = childFuture.orTimeout(timeout.timeout().toMillis(), TimeUnit.MILLISECONDS);

        try {
            context.scheduler().managedJoin(timedFuture);
            return childDescriptor.status();
        } catch (CompletionException e) {
            var cause = e.getCause();
            if (cause instanceof TimeoutException) {
                return handleTimeout(timeout, childDescriptor);
            }
            if (cause instanceof Error err) {
                throw err;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        }
    }

    private static Status handleTimeout(final Timeout timeout, final MutableDescriptor childDescriptor) {
        if (!childDescriptor.isCompleted()) {
            childDescriptor.interruptExecutingThread();
            childDescriptor.setStatus(
                    Status.failed("timed out after " + timeout.timeout().toMillis() + " ms"));
            childDescriptor.completeFuture();
        }
        return Status.FAILED;
    }
}

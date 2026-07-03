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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ExecutionNode;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.action.StatusAccumulator;
import nonapi.org.paramixel.support.Arguments;
import nonapi.org.paramixel.support.UnrecoverableErrors;
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

/**
 * Continuation-based execution strategies for Paramixel actions.
 *
 * <p>Coordination strategies (Parallel, Sequential, Scope, Timeout, etc.) create an
 * {@link ExecutionNode}, schedule their initial children, and return immediately
 * without blocking. Each child completion triggers a continuation
 * that advances the parent's state machine (admit next child, transition phase, or
 * finalize). No scheduler worker thread blocks waiting for descendants.
 *
 * <p>The two exceptions are {@code runIsolated} and {@code runConditional}, which block a worker
 * in {@code Scheduler.managedJoin(...)} to preserve the existing synchronous-wrapper semantics
 * for {@link org.paramixel.api.action.Isolated} and {@link org.paramixel.api.action.Conditional}.
 * Work-stealing inside {@code managedJoin} keeps these deadlock-free under nested blocking.
 */
@SuppressWarnings({"deprecation", "removal"})
final class ActionExecutionStrategies {

    private static final Map<Class<? extends Action>, ActionExecutionStrategy<? extends Action>> STRATEGIES =
            Map.ofEntries(
                    strategy(Step.class, ActionExecutionStrategies::runStep),
                    strategy(Assert.class, ActionExecutionStrategies::runAssert),
                    strategy(Delay.class, ActionExecutionStrategies::runDelay),
                    strategy(Parallel.class, ActionExecutionStrategies::startParallel),
                    strategy(Timeout.class, ActionExecutionStrategies::startTimeout),
                    strategy(Sequence.class, (seq, ctx) -> startSequentialDependent(ctx, seq.isDependent())),
                    strategy(Sequential.class, (seq, ctx) -> startSequentialDependent(ctx, seq.isDependent())),
                    strategy(Loop.class, ActionExecutionStrategies::startLoop),
                    strategy(Repeat.class, ActionExecutionStrategies::startRepeat),
                    strategy(Until.class, ActionExecutionStrategies::startUntil),
                    strategy(Scope.class, ActionExecutionStrategies::startLifecycle),
                    strategy(Static.class, ActionExecutionStrategies::startLifecycle),
                    strategy(Instance.class, ActionExecutionStrategies::startInstance),
                    strategy(Isolated.class, ActionExecutionStrategies::runIsolated),
                    strategy(Conditional.class, ActionExecutionStrategies::runConditional));

    private ActionExecutionStrategies() {}

    static Status execute(final Action action, final ConcreteContext context) {
        Objects.requireNonNull(action, "action is null");
        Objects.requireNonNull(context, "context is null");
        var strategy = STRATEGIES.get(action.getClass());
        if (strategy == null) {
            return startSequentialDependent(context);
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

    // ── Leaf strategies (synchronous) ──────────────────────────────────

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

    // ── Synchronous wrappers ───────────────────────────────────────────

    private static Status runIsolated(final Isolated isolated, final ConcreteContext context) {
        var lock = context.scheduler().getLock(isolated.lockName());
        context.scheduler().enterIsolation(isolated.lockName(), lock, context.descriptor());
        try {
            var child = Arguments.requireInstanceOf(
                    context.descriptor().children().get(0),
                    MutableDescriptor.class,
                    "child descriptor must be a MutableDescriptor");
            context.runChild(child, ExecutionMode.RUN);
            if (!child.isCompleted() && child.scheduledFuture() != null) {
                // Child deferred (e.g. Parallel body). Wait for it using
                // managedJoin, which work-steals from the executor queue while
                // waiting, preventing deadlock when worker threads are scarce.
                context.scheduler().managedJoin(child.scheduledFuture());
            }
            return child.status();
        } finally {
            context.scheduler().exitIsolation(isolated.lockName(), lock, context.descriptor());
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
        if (!child.isCompleted() && child.scheduledFuture() != null) {
            context.scheduler().managedJoin(child.scheduledFuture());
        }
        return child.status();
    }

    // ── Async Parallel ─────────────────────────────────────────────────

    private static Status startParallel(final Parallel parallel, final ConcreteContext context) {
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

        var node = createNode(descriptor, context);
        node.children = new ArrayList<>(children);
        node.cap = effectiveParallelism;
        node.childIndex = 0;
        node.runningChildren = 0;
        node.continueOnEveryChildCompletion = true;
        node.aggregator = new StatusAccumulator();
        node.continuation = () -> continueParallel(node, context);
        descriptor.setExecutionNode(node);

        synchronized (node) {
            admitParallelChildren(node, context);
        }

        if (node.attemptedChildren == 0) {
            completeParallel(node);
            return node.aggregator.status();
        }
        return Status.RUNNING;
    }

    private static void admitParallelChildren(final ExecutionNode node, final ConcreteContext context) {
        var children = node.children;
        while (node.runningChildren < node.cap && node.childIndex < children.size()) {
            var child = Arguments.requireInstanceOf(
                    children.get(node.childIndex), MutableDescriptor.class, "child must be a MutableDescriptor");
            node.childIndex++;
            node.runningChildren++;
            if (!scheduleChild(child, ExecutionMode.RUN, node, context)) {
                node.runningChildren--;
            }
        }
    }

    private static void continueParallel(final ExecutionNode node, final ConcreteContext context) {
        synchronized (node) {
            if (node.descriptor.executionNode() != node) {
                return;
            }
            if (node.runningChildren > 0) {
                node.runningChildren--;
            }

            admitParallelChildren(node, context);

            if (node.pendingChildCount() == 0) {
                completeParallel(node);
            }
        }
    }

    private static void completeParallel(final ExecutionNode node) {
        var descriptor = node.descriptor;
        for (var child : node.children) {
            node.aggregator.include(child);
        }
        descriptor.setStatus(node.aggregator.status());
        descriptor.setExecutionNode(null);
    }

    // ── Async Sequential / Sequence ────────────────────────────────────

    private static Status startSequentialDependent(final ConcreteContext context) {
        return startSequentialDependent(context, true);
    }

    private static Status startSequentialDependent(final ConcreteContext context, final boolean dependent) {
        var descriptor = context.descriptor();
        var children = descriptor.children();
        if (children.isEmpty()) {
            return Status.PASSED;
        }

        var node = createNode(descriptor, context);
        node.children = new ArrayList<>(children);
        node.childIndex = 0;
        node.aggregator = new StatusAccumulator();
        node.childMode = ExecutionMode.RUN;
        node.cap = dependent ? 1 : 0; // reuse cap field as dependent flag
        node.continuation = () -> continueSequential(node, context, dependent);
        descriptor.setExecutionNode(node);

        scheduleNextSequentialChild(node, context);
        return runningIfChildrenAttempted(node);
    }

    private static void scheduleNextSequentialChild(final ExecutionNode node, final ConcreteContext context) {
        if (node.childIndex >= node.children.size()) {
            completeSequential(node);
            return;
        }

        var child = Arguments.requireInstanceOf(
                node.children.get(node.childIndex), MutableDescriptor.class, "child must be a MutableDescriptor");
        node.childIndex++;
        scheduleChild(child, node.childMode, node, context);
    }

    private static void continueSequential(
            final ExecutionNode node, final ConcreteContext context, final boolean dependent) {
        if (node.descriptor.executionNode() != node) {
            return;
        }
        // Record the just-completed child's status.
        var completedIndex = node.childIndex - 1;
        if (completedIndex >= 0 && completedIndex < node.children.size()) {
            var completedChild = node.children.get(completedIndex);
            node.aggregator.include(completedChild);

            if (dependent
                    && node.childMode == ExecutionMode.RUN
                    && !completedChild.isPassed()
                    && !completedChild.isAborted()) {
                node.childMode = ExecutionMode.SKIP;
            }
        }

        scheduleNextSequentialChild(node, context);
    }

    private static void completeSequential(final ExecutionNode node) {
        node.descriptor.setStatus(node.aggregator.status());
        node.descriptor.setExecutionNode(null);
    }

    // ── Async Lifecycle / Scope / Static / Instance ────────────────────

    private static Status startLifecycle(final Action ignored, final ConcreteContext context) {
        return startLifecycleInternal(context);
    }

    private static Status startInstance(final Instance ignored, final ConcreteContext context) {
        return startLifecycleInternal(context.withInstanceHolder(new InstanceHolder()));
    }

    private static Status startLifecycleInternal(final ConcreteContext context) {
        var descriptor = context.descriptor();
        var node = createNode(descriptor, context);
        node.aggregator = new StatusAccumulator();
        node.phase = ExecutionNode.PHASE_BEFORE;
        node.continuation = () -> continueLifecycle(node, context);
        descriptor.setExecutionNode(node);

        advanceLifecyclePhase(node, context);
        return runningIfChildrenAttempted(node);
    }

    private static void advanceLifecyclePhase(final ExecutionNode node, final ConcreteContext context) {
        var descriptor = node.descriptor;

        switch (node.phase) {
            case ExecutionNode.PHASE_BEFORE:
                node.phase = ExecutionNode.PHASE_BODY;
                var beforeDesc = descriptor.before().orElse(null);
                if (beforeDesc != null) {
                    scheduleChild(beforeDesc, ExecutionMode.RUN, node, context);
                } else {
                    advanceLifecyclePhase(node, context);
                }
                break;

            case ExecutionNode.PHASE_BODY:
                node.phase = ExecutionNode.PHASE_AFTER;
                var before = descriptor.before().orElse(null);
                var runBody = true;
                if (before != null) {
                    node.aggregator.include(before);
                    if (!before.isPassed()) {
                        runBody = false;
                    }
                }

                var bodyChildren = descriptor.children();
                if (runBody && !bodyChildren.isEmpty()) {
                    for (var child : bodyChildren) {
                        scheduleChild(child, ExecutionMode.RUN, node, context);
                    }
                } else if (!runBody) {
                    if (bodyChildren.isEmpty()) {
                        advanceLifecyclePhase(node, context);
                    } else {
                        for (var child : bodyChildren) {
                            scheduleChild(child, ExecutionMode.SKIP, node, context);
                        }
                    }
                } else {
                    // No body children.
                    advanceLifecyclePhase(node, context);
                }
                break;

            case ExecutionNode.PHASE_AFTER:
                node.phase = ExecutionNode.PHASE_COMPLETE;
                var afterDesc = descriptor.after().orElse(null);
                if (afterDesc != null) {
                    scheduleChild(afterDesc, ExecutionMode.RUN, node, context);
                } else {
                    advanceLifecyclePhase(node, context);
                }
                break;

            case ExecutionNode.PHASE_COMPLETE:
                completeLifecycle(node);
                break;

            default:
                break;
        }
    }

    private static void continueLifecycle(final ExecutionNode node, final ConcreteContext context) {
        if (node.descriptor.executionNode() != node) {
            return;
        }
        advanceLifecyclePhase(node, context);
    }

    // before is already included in advanceLifecyclePhase(PHASE_BODY) — do not double-tally.
    private static void completeLifecycle(final ExecutionNode node) {
        var descriptor = node.descriptor;
        for (var child : descriptor.children()) {
            node.aggregator.include(child);
        }
        var after = descriptor.after().orElse(null);
        if (after != null) {
            node.aggregator.include(after);
        }
        descriptor.setStatus(node.aggregator.status());
        descriptor.setExecutionNode(null);
    }

    // ── Async Timeout ──────────────────────────────────────────────────

    private static Status startTimeout(final Timeout timeout, final ConcreteContext context) {
        var descriptor = context.descriptor();
        var children = descriptor.children();
        if (children.isEmpty()) {
            return Status.PASSED;
        }

        var childDescriptor = Arguments.requireInstanceOf(
                children.get(0), MutableDescriptor.class, "child descriptor must be a MutableDescriptor");

        var node = createNode(descriptor, context);
        node.aggregator = new StatusAccumulator();
        var timedOut = new AtomicBoolean(false);
        node.continuation = () -> completeTimeout(node, timeout, childDescriptor, timedOut);
        descriptor.setExecutionNode(node);

        node.incrementPendingChildren();
        node.attemptedChildren++;
        final java.util.concurrent.CompletableFuture<Descriptor> childFuture;
        try {
            childFuture = context.scheduleAsync(childDescriptor);
        } catch (Throwable t) {
            signalChildSchedulingFailure(node);
            return Status.RUNNING;
        }
        childFuture.orTimeout(timeout.timeout().toMillis(), TimeUnit.MILLISECONDS);

        childFuture.whenComplete((result, ex) -> {
            if (ex instanceof TimeoutException && timedOut.compareAndSet(false, true)) {
                // orTimeout's internal TimeoutException has a null message, which produces
                // "cancelled by ancestor: null" in abort's descendant status. Create a
                // properly-messaged exception to pass through the cascade.
                var cause = new TimeoutException(
                        "timeout exceeded: " + timeout.timeout().toMillis() + " ms");
                handleTimeout(timeout, childDescriptor, cause);
                if (node.drainPendingChildren()) {
                    node.scheduleContinuation();
                }
            }
        });

        return Status.RUNNING;
    }

    /**
     * Handles timeout by cascading cancellation through the child's subtree.
     *
     * <p>Idempotent: returns existing terminal status if the child is already complete.
     *
     * @param timeout the timeout action
     * @param childDescriptor the child descriptor to abort
     * @param cause the timeout cause with a non-null message for the descendant status
     * @return the child descriptor's status
     */
    static Status handleTimeout(
            final Timeout timeout, final MutableDescriptor childDescriptor, final TimeoutException cause) {
        synchronized (childDescriptor) {
            if (childDescriptor.isCompleted()) {
                return childDescriptor.status();
            }
        }
        var timeoutStatus = Status.failed("timed out after " + timeout.timeout().toMillis() + " ms");
        childDescriptor.abort(timeoutStatus, cause);
        return childDescriptor.status();
    }

    private static void completeTimeout(
            final ExecutionNode node,
            final Timeout ignoredTimeout,
            final MutableDescriptor childDescriptor,
            final AtomicBoolean ignoredTimedOut) {
        // Timeout has exactly one child, so its aggregate status is the child's status. The node
        // aggregator is intentionally not consulted here (it would only hold the single child).
        node.descriptor.setStatus(childDescriptor.status());
        node.descriptor.setExecutionNode(null);
    }

    // ── Async Loop ─────────────────────────────────────────────────────

    private static Status startLoop(final Loop loopAction, final ConcreteContext context) {
        var descriptor = context.descriptor();
        var children = descriptor.children();
        if (children.isEmpty()) {
            return Status.PASSED;
        }

        var node = createNode(descriptor, context);
        node.children = new ArrayList<>(children);
        node.childIndex = 0;
        node.aggregator = new StatusAccumulator();
        node.continuation = () -> continueLoop(node, context, loopAction);
        descriptor.setExecutionNode(node);

        scheduleNextLoopChild(node, context, loopAction);
        return runningIfChildrenAttempted(node);
    }

    private static void scheduleNextLoopChild(
            final ExecutionNode node, final ConcreteContext context, final Loop loopAction) {
        if (node.childIndex >= node.children.size()) {
            completeLoop(node, loopAction);
            return;
        }

        var child = Arguments.requireInstanceOf(
                node.children.get(node.childIndex), MutableDescriptor.class, "child must be a MutableDescriptor");
        node.childIndex++;
        scheduleChild(child, ExecutionMode.RUN, node, context);
    }

    private static void continueLoop(final ExecutionNode node, final ConcreteContext context, final Loop loopAction) {
        if (node.descriptor.executionNode() != node) {
            return;
        }
        var completedIndex = node.childIndex - 1;
        if (completedIndex >= 0 && completedIndex < node.children.size()) {
            var completedChild = node.children.get(completedIndex);
            node.aggregator.include(completedChild);

            if (completedChild.isAborted()) {
                skipRemainingLoopChildren(node, context);
                node.descriptor.setStatus(Status.ABORTED);
                node.descriptor.setExecutionNode(null);
                return;
            }

            if (loopAction.until().isPresent()) {
                boolean satisfied;
                try {
                    satisfied = loopAction.until().get().test(context);
                } catch (Throwable t) {
                    UnrecoverableErrors.rethrowIfUnrecoverable(t);
                    skipRemainingLoopChildren(node, context);
                    node.descriptor.setStatus(Status.failed("until predicate failed: " + t.getMessage(), t));
                    node.descriptor.setExecutionNode(null);
                    return;
                }
                if (satisfied) {
                    skipRemainingLoopChildren(node, context);
                    node.descriptor.setStatus(Status.PASSED);
                    node.descriptor.setExecutionNode(null);
                    return;
                }
            }

            if (loopAction.delay().isPresent() && node.childIndex < node.children.size()) {
                long delayMs = loopAction
                        .delay()
                        .get()
                        .delayForIteration(completedIndex + 1)
                        .toMillis();
                if (delayMs > 0) {
                    // NOTE: this sleep runs on the scheduler worker executing the continuation.
                    // For typical short inter-iteration delays this is acceptable; under low
                    // parallelism a long delay ties up a worker that could run other ready tasks.
                    // A future enhancement could schedule post-delay admission on a
                    // ScheduledExecutorService to free the worker.
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        skipRemainingLoopChildren(node, context);
                        node.descriptor.setStatus(Status.ABORTED);
                        node.descriptor.setExecutionNode(null);
                        return;
                    }
                }
            }
        }

        scheduleNextLoopChild(node, context, loopAction);
    }

    private static void skipRemainingLoopChildren(final ExecutionNode node, final ConcreteContext context) {
        for (var i = node.childIndex; i < node.children.size(); i++) {
            var child = node.children.get(i);
            node.aggregator.include(context.runChild(child, ExecutionMode.SKIP));
        }
        node.childIndex = node.children.size();
    }

    private static void completeLoop(final ExecutionNode node, final Loop loopAction) {
        if (loopAction.until().isPresent()) {
            node.descriptor.setStatus(Status.FAILED);
        } else {
            node.descriptor.setStatus(node.aggregator.status());
        }
        node.descriptor.setExecutionNode(null);
    }

    // ── Async Repeat ───────────────────────────────────────────────────

    private static Status startRepeat(final Repeat ignored, final ConcreteContext context) {
        var descriptor = context.descriptor();
        var children = descriptor.children();
        if (children.isEmpty()) {
            return Status.PASSED;
        }

        var node = createNode(descriptor, context);
        node.children = new ArrayList<>(children);
        node.childIndex = 0;
        node.aggregator = new StatusAccumulator();
        node.continuation = () -> continueRepeat(node, context);
        descriptor.setExecutionNode(node);

        scheduleNextRepeatChild(node, context);

        return runningIfChildrenAttempted(node);
    }

    private static void scheduleNextRepeatChild(final ExecutionNode node, final ConcreteContext context) {
        if (node.childIndex >= node.children.size()) {
            completeRepeat(node);
            return;
        }

        var child = Arguments.requireInstanceOf(
                node.children.get(node.childIndex), MutableDescriptor.class, "child must be a MutableDescriptor");
        node.childIndex++;
        scheduleChild(child, ExecutionMode.RUN, node, context);
    }

    private static void continueRepeat(final ExecutionNode node, final ConcreteContext context) {
        if (node.descriptor.executionNode() != node) {
            return;
        }
        var completedIndex = node.childIndex - 1;
        if (completedIndex >= 0 && completedIndex < node.children.size()) {
            node.aggregator.include(node.children.get(completedIndex));
        }

        scheduleNextRepeatChild(node, context);
    }

    private static void completeRepeat(final ExecutionNode node) {
        node.descriptor.setStatus(node.aggregator.status());
        node.descriptor.setExecutionNode(null);
    }

    // ── Async Until ────────────────────────────────────────────────────

    private static Status startUntil(final Until untilAction, final ConcreteContext context) {
        var descriptor = context.descriptor();
        var children = descriptor.children();
        if (children.isEmpty()) {
            return Status.PASSED;
        }

        var node = createNode(descriptor, context);
        node.children = new ArrayList<>(children);
        node.childIndex = 0;
        node.aggregator = new StatusAccumulator();
        node.continuation = () -> continueUntil(node, context, untilAction);
        descriptor.setExecutionNode(node);

        scheduleNextUntilChild(node, context);
        return runningIfChildrenAttempted(node);
    }

    private static void scheduleNextUntilChild(final ExecutionNode node, final ConcreteContext context) {
        if (node.childIndex >= node.children.size()) {
            node.descriptor.setStatus(Status.FAILED);
            node.descriptor.setExecutionNode(null);
            return;
        }

        var child = Arguments.requireInstanceOf(
                node.children.get(node.childIndex), MutableDescriptor.class, "child must be a MutableDescriptor");
        node.childIndex++;
        scheduleChild(child, ExecutionMode.RUN, node, context);
    }

    private static void continueUntil(
            final ExecutionNode node, final ConcreteContext context, final Until untilAction) {
        if (node.descriptor.executionNode() != node) {
            return;
        }
        var completedIndex = node.childIndex - 1;
        if (completedIndex >= 0 && completedIndex < node.children.size()) {
            var completedChild = node.children.get(completedIndex);
            node.aggregator.include(completedChild);

            if (completedChild.isAborted()) {
                skipRemainingUntilChildren(node, context);
                node.descriptor.setStatus(Status.ABORTED);
                node.descriptor.setExecutionNode(null);
                return;
            }

            boolean satisfied;
            if (untilAction.until().isPresent()) {
                try {
                    satisfied = untilAction.until().get().test(context);
                } catch (Throwable t) {
                    UnrecoverableErrors.rethrowIfUnrecoverable(t);
                    skipRemainingUntilChildren(node, context);
                    node.descriptor.setStatus(Status.failed("until predicate failed: " + t.getMessage(), t));
                    node.descriptor.setExecutionNode(null);
                    return;
                }
            } else {
                satisfied = completedChild.isPassed();
            }

            if (satisfied) {
                skipRemainingUntilChildren(node, context);
                node.descriptor.setStatus(Status.PASSED);
                node.descriptor.setExecutionNode(null);
                return;
            }
        }

        scheduleNextUntilChild(node, context);
    }

    private static void skipRemainingUntilChildren(final ExecutionNode node, final ConcreteContext context) {
        for (var i = node.childIndex; i < node.children.size(); i++) {
            var child = node.children.get(i);
            node.aggregator.include(context.runChild(child, ExecutionMode.SKIP));
        }
        node.childIndex = node.children.size();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static ExecutionNode createNode(final MutableDescriptor descriptor, final ConcreteContext context) {
        return new ExecutionNode(descriptor, context.scheduler());
    }

    /**
     * Schedules a child descriptor for execution.
     *
     * <p>The pending count is incremented before scheduling so even immediate
     * child completion or scheduler rejection can safely notify this node. The
     * scheduler calls {@link Scheduler#childCompleted(Descriptor)} for rejected
     * submissions, so returned futures are not inspected here; a future can
     * complete exceptionally because the child genuinely ran and failed before
     * this method returns.
     *
     * @return {@code true} if the schedule call was accepted by the scheduler,
     *     or {@code false} if scheduling threw before the scheduler could take
     *     ownership
     */
    private static boolean scheduleChild(
            final Descriptor child,
            final ExecutionMode mode,
            final ExecutionNode parentNode,
            final ConcreteContext context) {
        parentNode.incrementPendingChildren();
        parentNode.attemptedChildren++;
        try {
            context.scheduleAsync(child, mode);
            return true;
        } catch (Throwable t) {
            signalChildSchedulingFailure(parentNode);
            return false;
        }
    }

    private static void signalChildSchedulingFailure(final ExecutionNode node) {
        var remainingChildren = node.decrementPendingChildren();
        if (remainingChildren < 0) {
            return;
        }
        if (node.continueOnEveryChildCompletion || remainingChildren == 0) {
            node.scheduleContinuation();
        }
    }

    private static Status runningIfChildrenAttempted(final ExecutionNode node) {
        if (node.attemptedChildren > 0) {
            return Status.RUNNING;
        }
        return node.descriptor.status();
    }
}

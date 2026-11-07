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

import java.time.Duration;
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
import nonapi.org.paramixel.support.Throwables;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.ContextConsumer;
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
        return runLeaf(step.throwableConsumer(), context);
    }

    private static Status runAssert(final Assert assertAction, final ConcreteContext context) {
        return runLeaf(assertAction.throwableConsumer(), context);
    }

    private static Status runDelay(final Delay delay, final ConcreteContext context) {
        return runLeaf(delay.throwableConsumer(), context);
    }

    /**
     * Runs a leaf action's user-supplied body and normalizes checked throwables.
     *
     * <p>{@link RuntimeException} and {@link Error} propagate unchanged so that failure kinds the
     * caller can already interpret are not rewrapped; any other {@link Throwable} is wrapped in a
     * {@link RuntimeException}.
     *
     * @param consumer the action body; must not be {@code null}
     * @param context the execution context; must not be {@code null}
     * @return {@link Status#PASSED} when the body returns normally
     */
    private static Status runLeaf(final ContextConsumer consumer, final ConcreteContext context) {
        try {
            consumer.accept(context);
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

        // Every child of a parallel node is ready at once. The node's declared parallelism is not an
        // admission window: it is a cap on how many leaf tasks in this subtree may be running at the
        // same time, enforced by the scheduler's leaf gate against the descriptor's action. A
        // parallel node therefore never withholds a child, and a child subtree's own fan-out is
        // bounded by its own caps and the global caps, never by this node's readiness.
        var node = createNode(descriptor, context);
        node.children = new ArrayList<>(children);
        node.childIndex = 0;
        node.continueOnEveryChildCompletion = true;
        node.aggregator = new StatusAccumulator();
        node.continuation = () -> continueParallel(node, context);
        node.admissionRetry = () -> retryParallelAdmission(node, context);
        descriptor.setExecutionNode(node);

        synchronized (node) {
            admitParallelChildren(node, context);
        }

        if (node.attemptedChildren == 0 && node.childIndex >= node.children.size()) {
            completeParallel(node);
            return node.aggregator.status();
        }
        return Status.RUNNING;
    }

    private static void retryParallelAdmission(final ExecutionNode node, final ConcreteContext context) {
        synchronized (node) {
            if (node.descriptor.executionNode() != node) {
                return;
            }
            admitParallelChildren(node, context);
        }
    }

    /**
     * Makes every parallel child ready, stopping without advancing the cursor when an admission is
     * deferred for ready capacity.
     *
     * <p>A deferred admission is a backpressure signal from the scheduler's admission permits, not a
     * concurrency bound: the child at the cursor was not dispatched, so the cursor rolls back and a
     * capacity wakeup resumes from it via {@code node.admissionRetry}.
     */
    private static void admitParallelChildren(final ExecutionNode node, final ConcreteContext context) {
        var children = node.children;
        while (node.childIndex < children.size()) {
            var child = Arguments.requireInstanceOf(
                    children.get(node.childIndex), MutableDescriptor.class, "child must be a MutableDescriptor");
            node.childIndex++;
            if (scheduleChild(child, ExecutionMode.RUN, node, context, false) == Admission.DEFERRED) {
                node.childIndex--;
                return;
            }
        }
    }

    private static void continueParallel(final ExecutionNode node, final ConcreteContext context) {
        synchronized (node) {
            if (node.descriptor.executionNode() != node) {
                return;
            }
            // Re-evaluated on every child completion: readiness can grow (more children admitted)
            // and the completion has already released its leaf capacity.
            admitParallelChildren(node, context);

            if (node.pendingChildCount() == 0 && node.childIndex >= node.children.size()) {
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
        node.admissionRetry = () -> scheduleNextSequentialChild(node, context);
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
        if (scheduleChild(child, node.childMode, node, context, true) == Admission.DEFERRED) {
            // The child at the cursor was not dispatched; roll back so the capacity retry
            // re-dispatches it.
            node.childIndex--;
        }
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
        node.childMode = ExecutionMode.RUN;
        node.continuation = () -> continueLifecycle(node, context);
        node.admissionRetry = () -> retryLifecycleAdmission(node, context);
        descriptor.setExecutionNode(node);

        dispatchLifecyclePhase(node, context);
        return runningIfChildrenAttempted(node);
    }

    /**
     * Lifecycle node state machine. The node phase denotes the lifecycle phase currently being
     * dispatched or executed:
     *
     * <ul>
     *   <li>{@link ExecutionNode#PHASE_BEFORE}: the optional before child is (or is about to be)
     *       dispatched; its completion advances the node to the body phase.</li>
     *   <li>{@link ExecutionNode#PHASE_BODY}: body children are being admitted and executed;
     *       {@link ExecutionNode#bodyAdmissionComplete} records whether every body child has been
     *       dispatched, and the body phase is only left once every body child has both been
     *       dispatched and completed.</li>
     *   <li>{@link ExecutionNode#PHASE_AFTER}: the optional after child is (or is about to be)
     *       dispatched; its completion finishes the lifecycle.</li>
     *   <li>{@link ExecutionNode#PHASE_COMPLETE}: aggregation has been published.</li>
     * </ul>
     *
     * <p>All lifecycle transitions are serialized on the node monitor. Dispatch may be interrupted
     * when ready capacity is exhausted: {@link Scheduler#dispatchChild} leaves the child at the
     * cursor unscheduled and registers the node, and {@link #retryLifecycleAdmission} resumes the
     * interrupted dispatch from the cursors stored on the node.
     */
    private static void dispatchLifecyclePhase(final ExecutionNode node, final ConcreteContext context) {
        synchronized (node) {
            if (node.descriptor.executionNode() != node) {
                return;
            }
            switch (node.phase) {
                case ExecutionNode.PHASE_BEFORE:
                    scheduleLifecycleBeforeChild(node, context);
                    break;
                case ExecutionNode.PHASE_BODY:
                    dispatchLifecycleBodyChildren(node, context);
                    break;
                case ExecutionNode.PHASE_AFTER:
                    enterLifecycleAfterPhase(node, context);
                    break;
                case ExecutionNode.PHASE_COMPLETE:
                default:
                    break;
            }
        }
    }

    private static void retryLifecycleAdmission(final ExecutionNode node, final ConcreteContext context) {
        synchronized (node) {
            if (node.descriptor.executionNode() != node) {
                return;
            }
            // Body admission may also resume from a completion continuation once capacity returns;
            // guard so a wakeup that raced a completed phase transition is a no-op.
            switch (node.phase) {
                case ExecutionNode.PHASE_BEFORE:
                    scheduleLifecycleBeforeChild(node, context);
                    break;
                case ExecutionNode.PHASE_BODY:
                    if (!node.bodyAdmissionComplete) {
                        dispatchLifecycleBodyChildren(node, context);
                    }
                    break;
                case ExecutionNode.PHASE_AFTER:
                    enterLifecycleAfterPhase(node, context);
                    break;
                case ExecutionNode.PHASE_COMPLETE:
                default:
                    break;
            }
        }
    }

    private static void continueLifecycle(final ExecutionNode node, final ConcreteContext context) {
        if (node.descriptor.executionNode() != node) {
            return;
        }
        lifecycleChildrenCompleted(node, context);
    }

    /**
     * Handles a child-completion event (pending count reached zero) for the current lifecycle
     * phase and advances the phase when its children are fully dispatched and completed.
     */
    private static void lifecycleChildrenCompleted(final ExecutionNode node, final ConcreteContext context) {
        synchronized (node) {
            if (node.descriptor.executionNode() != node) {
                return;
            }
            switch (node.phase) {
                case ExecutionNode.PHASE_BEFORE:
                    enterLifecycleBodyPhase(node, context);
                    break;
                case ExecutionNode.PHASE_BODY:
                    if (node.bodyAdmissionComplete) {
                        enterLifecycleAfterPhase(node, context);
                    }
                    // Body children still awaiting admission: the capacity retry resumes the
                    // burst and the burst's own completions advance the phase.
                    break;
                case ExecutionNode.PHASE_AFTER:
                    if (node.pendingChildCount() == 0) {
                        finishLifecycle(node);
                    }
                    // Stray continuation from a previous phase while the after child is still in
                    // flight: nothing to do.
                    break;
                case ExecutionNode.PHASE_COMPLETE:
                default:
                    break;
            }
        }
    }

    private static void scheduleLifecycleBeforeChild(final ExecutionNode node, final ConcreteContext context) {
        var before = node.descriptor.before().orElse(null);
        if (before == null) {
            enterLifecycleBodyPhase(node, context);
            return;
        }
        var child = Arguments.requireInstanceOf(before, MutableDescriptor.class, "before descriptor is mutable");
        // A deferred admission leaves the child parked; the capacity retry re-enters this method.
        scheduleChild(child, ExecutionMode.RUN, node, context, true);
        // Before child is in flight; its completion continuation advances the phase.
    }

    /**
     * Transitions the lifecycle from the before phase to the body phase, aggregates the before
     * child, decides the body execution mode, and dispatches the body children.
     */
    private static void enterLifecycleBodyPhase(final ExecutionNode node, final ConcreteContext context) {
        var descriptor = node.descriptor;
        node.phase = ExecutionNode.PHASE_BODY;
        node.bodyChildIndex = 0;
        node.bodyAdmissionComplete = false;

        var before = descriptor.before().orElse(null);
        var runBody = true;
        if (before != null) {
            node.aggregator.include(before);
            if (!before.isPassed()) {
                runBody = false;
            }
        }

        if (descriptor.children().isEmpty()) {
            node.bodyAdmissionComplete = true;
            enterLifecycleAfterPhase(node, context);
            return;
        }
        node.childMode = runBody ? ExecutionMode.RUN : ExecutionMode.SKIP;
        dispatchLifecycleBodyChildren(node, context);
    }

    /**
     * Dispatches body children from {@link ExecutionNode#bodyChildIndex} onward, stopping without
     * advancing the cursor when an admission is deferred for ready capacity.
     */
    private static void dispatchLifecycleBodyChildren(final ExecutionNode node, final ConcreteContext context) {
        var bodyChildren = node.descriptor.children();
        while (node.bodyChildIndex < bodyChildren.size()) {
            var child = Arguments.requireInstanceOf(
                    bodyChildren.get(node.bodyChildIndex),
                    MutableDescriptor.class,
                    "body child must be a MutableDescriptor");
            node.bodyChildIndex++;
            if (scheduleChild(child, node.childMode, node, context, false) == Admission.DEFERRED) {
                // The child at the cursor was not dispatched; roll back so the capacity retry
                // resumes the burst here.
                node.bodyChildIndex--;
                return;
            }
        }
        node.bodyAdmissionComplete = true;
    }

    /**
     * Enters the after phase and dispatches the optional after child; the lifecycle finishes when
     * the after child (if any) completes.
     */
    private static void enterLifecycleAfterPhase(final ExecutionNode node, final ConcreteContext context) {
        node.phase = ExecutionNode.PHASE_AFTER;
        var after = node.descriptor.after().orElse(null);
        if (after == null) {
            finishLifecycle(node);
            return;
        }
        var child = Arguments.requireInstanceOf(after, MutableDescriptor.class, "after descriptor is mutable");
        // A deferred admission leaves the child parked; the capacity retry re-enters this method.
        scheduleChild(child, ExecutionMode.RUN, node, context, true);
        // After child is in flight; its completion continuation finishes the lifecycle.
    }

    private static void finishLifecycle(final ExecutionNode node) {
        node.phase = ExecutionNode.PHASE_COMPLETE;
        completeLifecycle(node);
    }

    // before is already included when the body phase began — do not double-tally.
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
        node.admissionRetry = () -> dispatchTimeoutChild(node, childDescriptor, context);
        descriptor.setExecutionNode(node);

        // The deadline starts counting when the timeout begins orchestrating, so it also covers
        // a child whose admission is deferred for ready capacity.
        context.scheduler()
                .executeAfterDelay(
                        () -> fireTimeout(node, timeout, childDescriptor, timedOut),
                        timeoutNanos(timeout.timeout()),
                        TimeUnit.NANOSECONDS);
        dispatchTimeoutChild(node, childDescriptor, context);
        return Status.RUNNING;
    }

    /**
     * Dispatches the timeout's single child; a deferred admission parks the child (registered
     * for a capacity wakeup) while the deadline timer keeps running.
     */
    private static void dispatchTimeoutChild(
            final ExecutionNode node, final MutableDescriptor childDescriptor, final ConcreteContext context) {
        // A deferred admission parks the child; the deadline timer and the capacity retry (which
        // re-enters this method) take over. Not inlined: the deadline must be armed before the
        // body runs. Scheduling failures are accounted for inside scheduleChild.
        scheduleChild(childDescriptor, ExecutionMode.RUN, node, context, false);
    }

    private static void fireTimeout(
            final ExecutionNode node,
            final Timeout timeout,
            final MutableDescriptor childDescriptor,
            final AtomicBoolean timedOut) {
        if (!timedOut.compareAndSet(false, true)) {
            return;
        }
        var cause =
                new TimeoutException("timeout exceeded: " + timeout.timeout().toMillis() + " ms");
        handleTimeout(timeout, childDescriptor, cause);
        node.drainPendingChildren();
        node.scheduleContinuation();
    }

    static long timeoutNanos(final Duration duration) {
        Objects.requireNonNull(duration, "duration is null");
        try {
            return duration.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
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

    // ── Async iteration (Loop / Repeat / Until) ────────────────────────

    /**
     * Evaluates whether a completed iteration satisfies the action's termination condition.
     *
     * <p>Implementations may throw: an exception is reported as a failed {@code until} predicate,
     * matching the behavior of the predicate itself.
     */
    @FunctionalInterface
    private interface TerminationCheck {

        boolean isSatisfied(Descriptor completedChild, ConcreteContext context) throws Throwable;
    }

    /**
     * Computes the terminal status once every iteration child has been admitted without the
     * termination condition being satisfied.
     */
    @FunctionalInterface
    private interface ExhaustedStatus {

        Status status(StatusAccumulator aggregator);
    }

    /**
     * Computes the delay before the next iteration.
     */
    @FunctionalInterface
    private interface IterationDelay {

        long nanosBeforeNext(int completedIndex);
    }

    /**
     * The four behaviors that distinguish {@link Loop}, {@link Repeat}, and {@link Until}.
     *
     * <p>Everything else about iterating an action's children — node setup, cursor advance,
     * capacity deferral, abort handling, remaining-child skipping, and terminal publication — is
     * shared and lives in {@link #startIteration}.
     *
     * @param stopsOnAbort whether an {@link Status#ABORTED ABORTED} child stops the iteration
     * @param termination the satisfaction test applied after each completed iteration
     * @param exhausted the terminal status when the cursor reaches the end
     * @param delay the inter-iteration delay, or a policy returning zero for none
     */
    private record IterationPolicy(
            boolean stopsOnAbort, TerminationCheck termination, ExhaustedStatus exhausted, IterationDelay delay) {

        /**
         * Returns the policy for {@link Loop}: aborts stop iteration, an optional predicate can
         * satisfy it, an exhausted tagged loop fails, and an optional delay policy applies
         * between iterations.
         *
         * @param loopAction the loop action; must not be {@code null}
         * @return the loop policy
         */
        static IterationPolicy forLoop(final Loop loopAction) {
            return new IterationPolicy(
                    true,
                    (completedChild, context) -> loopAction
                            .until()
                            .map(predicate -> predicate.test(context))
                            .orElse(false),
                    aggregator -> loopAction.until().isPresent() ? Status.FAILED : aggregator.status(),
                    completedIndex -> loopAction
                            .delay()
                            .map(policy -> delayNanosOrZero(policy.delayForIteration(completedIndex + 1)))
                            .orElse(0L));
        }

        /**
         * Returns the policy for {@link Repeat}: every child runs, nothing satisfies the
         * iteration, and the aggregate status is the result.
         *
         * @return the repeat policy
         */
        static IterationPolicy forRepeat() {
            return new IterationPolicy(
                    false, (completedChild, context) -> false, StatusAccumulator::status, completedIndex -> 0L);
        }

        /**
         * Returns the policy for {@link Until}: aborts stop iteration, the optional predicate (or
         * else the child's own outcome) satisfies it, and an exhausted until fails.
         *
         * @param untilAction the until action; must not be {@code null}
         * @return the until policy
         */
        static IterationPolicy forUntil(final Until untilAction) {
            return new IterationPolicy(
                    true,
                    (completedChild, context) -> untilAction
                            .until()
                            .map(predicate -> predicate.test(context))
                            .orElseGet(completedChild::isPassed),
                    aggregator -> Status.FAILED,
                    completedIndex -> 0L);
        }
    }

    private static final IterationPolicy REPEAT_POLICY = IterationPolicy.forRepeat();

    private static Status startLoop(final Loop loopAction, final ConcreteContext context) {
        return startIteration(IterationPolicy.forLoop(loopAction), context);
    }

    private static Status startRepeat(final Repeat ignored, final ConcreteContext context) {
        return startIteration(REPEAT_POLICY, context);
    }

    private static Status startUntil(final Until untilAction, final ConcreteContext context) {
        return startIteration(IterationPolicy.forUntil(untilAction), context);
    }

    private static Status startIteration(final IterationPolicy policy, final ConcreteContext context) {
        var descriptor = context.descriptor();
        var children = descriptor.children();
        if (children.isEmpty()) {
            return Status.PASSED;
        }

        var node = createNode(descriptor, context);
        node.children = new ArrayList<>(children);
        node.childIndex = 0;
        node.aggregator = new StatusAccumulator();
        node.continuation = () -> continueIteration(node, context, policy);
        node.admissionRetry = () -> scheduleNextIterationChild(node, context, policy);
        descriptor.setExecutionNode(node);

        scheduleNextIterationChild(node, context, policy);
        return runningIfChildrenAttempted(node);
    }

    private static void scheduleNextIterationChild(
            final ExecutionNode node, final ConcreteContext context, final IterationPolicy policy) {
        if (node.childIndex >= node.children.size()) {
            node.descriptor.setStatus(policy.exhausted().status(node.aggregator));
            node.descriptor.setExecutionNode(null);
            return;
        }

        var child = Arguments.requireInstanceOf(
                node.children.get(node.childIndex), MutableDescriptor.class, "child must be a MutableDescriptor");
        node.childIndex++;
        if (scheduleChild(child, ExecutionMode.RUN, node, context, true) == Admission.DEFERRED) {
            // The child at the cursor was not dispatched; roll back so the capacity retry
            // re-dispatches it.
            node.childIndex--;
        }
    }

    private static void continueIteration(
            final ExecutionNode node, final ConcreteContext context, final IterationPolicy policy) {
        if (node.descriptor.executionNode() != node) {
            return;
        }
        if (node.delayScheduled) {
            node.delayScheduled = false;
            scheduleNextIterationChild(node, context, policy);
            return;
        }
        var completedIndex = node.childIndex - 1;
        if (completedIndex >= 0 && completedIndex < node.children.size()) {
            var completedChild = node.children.get(completedIndex);
            node.aggregator.include(completedChild);

            if (policy.stopsOnAbort() && completedChild.isAborted()) {
                skipRemainingIterationChildren(node, context);
                node.descriptor.setStatus(Status.ABORTED);
                node.descriptor.setExecutionNode(null);
                return;
            }

            boolean satisfied;
            try {
                satisfied = policy.termination().isSatisfied(completedChild, context);
            } catch (Throwable t) {
                UnrecoverableErrors.rethrowIfUnrecoverable(t);
                skipRemainingIterationChildren(node, context);
                node.descriptor.setStatus(Status.failed("until predicate failed: " + t.getMessage(), t));
                node.descriptor.setExecutionNode(null);
                return;
            }
            if (satisfied) {
                skipRemainingIterationChildren(node, context);
                node.descriptor.setStatus(Status.PASSED);
                node.descriptor.setExecutionNode(null);
                return;
            }

            var delayNanos = policy.delay().nanosBeforeNext(completedIndex);
            if (delayNanos > 0 && node.childIndex < node.children.size()) {
                node.delayScheduled = true;
                context.scheduler().executeContinuationAfter(node, delayNanos, TimeUnit.NANOSECONDS);
                return;
            }
        }

        scheduleNextIterationChild(node, context, policy);
    }

    private static void skipRemainingIterationChildren(final ExecutionNode node, final ConcreteContext context) {
        for (var i = node.childIndex; i < node.children.size(); i++) {
            var child = node.children.get(i);
            node.aggregator.include(context.runChild(child, ExecutionMode.SKIP));
        }
        node.childIndex = node.children.size();
    }

    /**
     * Converts a loop inter-iteration delay to nanoseconds without truncating
     * sub-millisecond values. Non-positive durations yield zero (no delay).
     *
     * @param delay the policy-computed delay; must not be {@code null}
     * @return the delay in nanoseconds, or zero when no delay applies
     * @throws ArithmeticException if the duration overflows nanosecond precision,
     *     matching the previous millisecond-conversion behavior
     */
    static long delayNanosOrZero(final Duration delay) {
        var nanos = delay.toNanos();
        return Math.max(nanos, 0L);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Result of a child scheduling attempt for a coordination strategy.
     */
    private enum Admission {
        /** The child was dispatched (or terminalized by the scheduler) and a completion event
         * will follow; the strategy advances its cursor. */
        ADMITTED,
        /** Ready capacity was exhausted; the child at the cursor was not dispatched and the node
         * is registered for a capacity wakeup. The strategy keeps its cursor in place. */
        DEFERRED
    }

    private static ExecutionNode createNode(final MutableDescriptor descriptor, final ConcreteContext context) {
        return new ExecutionNode(descriptor, context.scheduler());
    }

    /**
     * Schedules a child descriptor for execution, keeping this node's completion accounting.
     *
     * <p>The pending count is incremented before scheduling so even immediate
     * child completion or scheduler rejection can safely notify this node. The
     * scheduler calls {@link Scheduler#childCompleted(Descriptor)} for rejected
     * submissions, so returned futures are not inspected here; a future can
     * complete exceptionally because the child genuinely ran and failed before
     * this method returns.
     *
     * <p>When ready capacity is exhausted the child is left unscheduled (never dispatched, never
     * terminalized), the pending and attempted counters are rolled back, and the parent node is
     * registered so its {@code admissionRetry} resumes the strategy's cursor-based admission when
     * capacity becomes available.
     *
     * <p>When scheduling throws, no completion event can ever arrive for the child. The failure is
     * therefore charged against the child: it is terminalized so status aggregation and skip
     * propagation see a real outcome, its pending increment is rolled back exactly once, and the
     * parent is advanced as a completion would advance it. Swallowing the throwable without that
     * accounting would leave the parent waiting on a completion that cannot happen.
     *
     * @param child the child descriptor to schedule; must not be {@code null}
     * @param mode the execution mode; must not be {@code null}
     * @param parentNode the coordination node whose strategy owns the cursor; must not be
     *     {@code null}
     * @param context the parent execution context; must not be {@code null}
     * @return {@link Admission#ADMITTED} when the child was dispatched or terminalized, or
     *     {@link Admission#DEFERRED} when ready capacity was exhausted
     */
    private static Admission scheduleChild(
            final Descriptor child,
            final ExecutionMode mode,
            final ExecutionNode parentNode,
            final ConcreteContext context,
            final boolean allowInline) {
        parentNode.incrementPendingChildren();
        parentNode.attemptedChildren++;
        var mutableChild = Arguments.requireInstanceOf(
                child, MutableDescriptor.class, "child descriptor must be a MutableDescriptor");
        try {
            if (allowInline
                    && mode == ExecutionMode.RUN
                    && mutableChild.isLeafAction()
                    && context.scheduler().canExecuteChildInline(mutableChild)
                    && context.scheduler().executeChildInline(mutableChild, mode, context)) {
                // No hand-off: the parent is advancing a one-at-a-time child, nothing queued
                // outranks it, and its leaf capacity was available. When capacity is unavailable the
                // child falls through to dispatch, where it waits in rank order for a slot.
                return Admission.ADMITTED;
            }
            if (context.scheduler().dispatchChild(mutableChild, mode, context, parentNode)
                    == Scheduler.ChildAdmissionResult.DEFERRED) {
                parentNode.decrementPendingChildren();
                parentNode.attemptedChildren--;
                return Admission.DEFERRED;
            }
            return Admission.ADMITTED;
        } catch (Throwable t) {
            var cause = Throwables.unwrap(t);
            UnrecoverableErrors.rethrowIfUnrecoverable(cause);
            terminalizeUnscheduledChild(mutableChild, cause);
            parentNode.attemptedChildren--;
            // Rolls back the pending increment and schedules the continuation that advances the
            // node, so the child is accounted for exactly once.
            signalChildSchedulingFailure(parentNode);
            return Admission.ADMITTED;
        }
    }

    /**
     * Marks a child that could not be scheduled terminal.
     *
     * <p>Mirrors the scheduler's handling of a rejected submission: the descriptor state machine
     * requires {@code PENDING -> RUNNING -> terminal}, so a pending child is moved through
     * {@code RUNNING} first. Leaving the child pending would aggregate as non-terminal and leave
     * the parent's own status unresolved. Idempotent: an already terminal child is left alone.
     *
     * @param child the child descriptor that was never dispatched; must not be {@code null}
     * @param cause the scheduling failure; must not be {@code null}
     */
    private static void terminalizeUnscheduledChild(final MutableDescriptor child, final Throwable cause) {
        if (child.isCompleted()) {
            return;
        }
        if (child.status().isPending()) {
            child.setStatus(Status.RUNNING);
        }
        var message = cause.getMessage() != null ? cause.getMessage() : "child could not be scheduled";
        child.setStatus(Status.failed(message, cause));
        child.completeFutureExceptionally(cause);
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
        if (node.descriptor.executionNode() != null) {
            return Status.RUNNING;
        }
        return node.descriptor.status();
    }
}

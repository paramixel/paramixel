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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.ExecutionNode;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Status;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;

@DisplayName("Scheduler continuation future parity")
class SchedulerContinuationFutureParityTest {

    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler(2);
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
    }

    @Test
    @DisplayName("deferred passing composite future completes normally")
    void deferredPassingCompositeFutureCompletesNormally() {
        var fixture = fixture(Sequential.builder("child")
                .child(Step.of("pass", context -> {}))
                .build());

        var result = fixture.context.scheduleAsync(fixture.child).join();

        assertThat(result.isPassed()).isTrue();
        assertThat(fixture.child.isPassed()).isTrue();
    }

    @Test
    @DisplayName("deferred failing composite future completes exceptionally")
    void deferredFailingCompositeFutureCompletesExceptionally() {
        var fixture = fixture(Sequential.builder("child")
                .child(Step.of("fail", context -> FailException.fail("boom")))
                .build());

        assertThatThrownBy(() -> fixture.context.scheduleAsync(fixture.child).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(FailException.class);
        assertThat(fixture.child.isFailed()).isTrue();
    }

    @Test
    @DisplayName("deferred aborted composite future completes exceptionally")
    void deferredAbortedCompositeFutureCompletesExceptionally() {
        var fixture = fixture(Sequential.builder("child")
                .child(Step.of("abort", context -> AbortedException.abort("abort")))
                .build());

        assertThatThrownBy(() -> fixture.context.scheduleAsync(fixture.child).join())
                .isInstanceOf(CompletionException.class);
        assertThat(fixture.child.isAborted()).isTrue();
    }

    @Test
    @DisplayName("completeExternally returns true on first call")
    void completeExternallyReturnsTrueOnFirstCall() {
        var fixture = fixture(Step.of("step", context -> {}));
        var node = new ExecutionNode(fixture.child, scheduler);

        assertThat(node.completeExternally()).isTrue();
        assertThat(node.nodeCompletion).isDone();
    }

    @Test
    @DisplayName("completeExternally returns false on second call")
    void completeExternallyReturnsFalseOnSecondCall() {
        var fixture = fixture(Step.of("step", context -> {}));
        var node = new ExecutionNode(fixture.child, scheduler);

        node.completeExternally();
        assertThat(node.completeExternally()).isFalse();
    }

    @Test
    @DisplayName("drainPendingChildren returns false when no children pending")
    void drainPendingChildrenReturnsFalseWhenNoChildrenPending() {
        var fixture = fixture(Step.of("step", context -> {}));
        var node = new ExecutionNode(fixture.child, scheduler);

        assertThat(node.drainPendingChildren()).isFalse();
    }

    @Test
    @DisplayName("drainPendingChildren returns true when children pending")
    void drainPendingChildrenReturnsTrueWhenChildrenPending() {
        var fixture = fixture(Step.of("step", context -> {}));
        var node = new ExecutionNode(fixture.child, scheduler);
        node.incrementPendingChildren();

        assertThat(node.drainPendingChildren()).isTrue();
        assertThat(node.pendingChildCount()).isZero();
    }

    @Test
    @DisplayName("queued descriptor that becomes terminal before execution notifies parent")
    void queuedDescriptorThatBecomesTerminalBeforeExecutionNotifiesParent() throws Exception {
        scheduler.close();
        scheduler = new Scheduler(1);
        var blockerStarted = new CountDownLatch(1);
        var releaseBlocker = new CountDownLatch(1);
        var blocker = new DescriptorBuilder().discover(Step.of("blocker", context -> {
            blockerStarted.countDown();
            try {
                releaseBlocker.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        var blockerContext = new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                blocker,
                scheduler,
                new InstanceHolder());
        scheduler.schedule(blocker, ExecutionMode.RUN, blockerContext);
        assertThat(blockerStarted.await(5, TimeUnit.SECONDS)).isTrue();

        var parent = new DescriptorBuilder()
                .discover(Sequential.builder("parent")
                        .child(Step.of("queued-child", context -> {}))
                        .build());
        parent.markScheduled();
        parent.setStatus(Status.RUNNING);
        var child = (MutableDescriptor) parent.children().get(0);
        var parentContext = new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                parent,
                scheduler,
                new InstanceHolder());
        var node = new ExecutionNode(parent, scheduler);
        node.continuation = () -> {
            parent.setStatus(Status.ABORTED);
            parent.setExecutionNode(null);
        };
        parent.setExecutionNode(node);
        node.incrementPendingChildren();

        try {
            var childFuture = scheduler.schedule(child, ExecutionMode.RUN, parentContext);
            child.abort(Status.aborted("aborted before execution"), new RuntimeException("aborted"));

            scheduler.managedJoin(node.nodeCompletion);

            assertThat(childFuture).isDone();
            assertThat(node.pendingChildCount()).isZero();
            assertThat(parent.isAborted()).isTrue();
        } finally {
            releaseBlocker.countDown();
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("terminal queued descriptor publishes future listener callback and parent exactly once")
    void terminalQueuedDescriptorPublishesFutureListenerCallbackAndParentExactlyOnce() throws Exception {
        scheduler.close();
        scheduler = new Scheduler(1);
        var blockerStarted = new CountDownLatch(1);
        var releaseBlocker = new CountDownLatch(1);
        var blocker = new DescriptorBuilder().discover(Step.of("blocker", context -> {
            blockerStarted.countDown();
            try {
                releaseBlocker.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        var blockerContext = new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                blocker,
                scheduler,
                new InstanceHolder());
        scheduler.schedule(blocker, ExecutionMode.RUN, blockerContext);
        assertThat(blockerStarted.await(5, TimeUnit.SECONDS)).isTrue();

        var parentCompletionCount = new AtomicInteger();
        var callbackCompleteCount = new AtomicInteger();
        var listenerCompleteCount = new AtomicInteger();

        var parent = new DescriptorBuilder()
                .discover(Sequential.builder("parent")
                        .child(Step.of("queued-child", context -> {}))
                        .build());
        parent.markScheduled();
        parent.setStatus(Status.RUNNING);
        var child = (MutableDescriptor) parent.children().get(0);
        var callback = new Scheduler.ExecutionCallback() {
            @Override
            public void onExecutionStart() {}

            @Override
            public void onExecutionComplete(final Throwable error) {
                callbackCompleteCount.incrementAndGet();
            }
        };
        var parentContext = new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                parent,
                scheduler,
                new InstanceHolder());
        var node = new ExecutionNode(parent, scheduler);
        node.continuation = () -> {
            parentCompletionCount.incrementAndGet();
            parent.setStatus(Status.ABORTED);
            parent.setExecutionNode(null);
        };
        parent.setExecutionNode(node);
        node.incrementPendingChildren();

        try {
            var childFuture = scheduler.schedule(child, ExecutionMode.RUN, parentContext, callback);
            childFuture.whenComplete((d, t) -> {
                listenerCompleteCount.incrementAndGet();
            });
            // Simulate queued descriptor becoming terminal before execution.
            child.abort(Status.aborted("aborted before execution"), new RuntimeException("aborted"));

            // Drain via managedJoin.
            scheduler.managedJoin(node.nodeCompletion);

            assertThat(childFuture).isDone();
            assertThat(node.pendingChildCount()).isZero();
            assertThat(parent.isAborted()).isTrue();
            assertThat(parentCompletionCount.get())
                    .as("parent notification must be exactly once")
                    .isEqualTo(1);
        } finally {
            releaseBlocker.countDown();
        }
    }

    private Fixture fixture(final org.paramixel.api.action.Action childAction) {
        var rootAction =
                Sequential.builder("root").independent().child(childAction).build();
        var root = new DescriptorBuilder().discover(rootAction);
        root.markScheduled();
        var child = (MutableDescriptor) root.children().get(0);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());
        return new Fixture(context, child);
    }

    private record Fixture(ConcreteContext context, MutableDescriptor child) {}
}

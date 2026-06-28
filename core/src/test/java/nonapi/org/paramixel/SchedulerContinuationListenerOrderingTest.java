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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler continuation listener ordering")
class SchedulerContinuationListenerOrderingTest {

    @Test
    @DisplayName("deferred Sequential closes listener bracket before future completion")
    void deferredSequentialClosesListenerBracketBeforeFutureCompletion() {
        assertDeferredRootListenerSemantics(Sequential.builder("sequential")
                .child(Step.of("leaf", context -> {}))
                .build());
    }

    @Test
    @DisplayName("deferred Parallel closes listener bracket before future completion")
    void deferredParallelClosesListenerBracketBeforeFutureCompletion() {
        assertDeferredRootListenerSemantics(Parallel.builder("parallel")
                .parallelism(2)
                .child(Step.of("one", context -> {}))
                .child(Step.of("two", context -> {}))
                .build());
    }

    @Test
    @DisplayName("deferred Scope closes listener bracket before future completion")
    void deferredScopeClosesListenerBracketBeforeFutureCompletion() {
        assertDeferredRootListenerSemantics(Scope.builder("scope")
                .before(Step.of("before", context -> {}))
                .body(Step.of("body", context -> {}))
                .after(Step.of("after", context -> {}))
                .build());
    }

    @Test
    @DisplayName("recoverable onAfter listener failure does not strand future")
    void recoverableAfterListenerFailureDoesNotStrandFuture() {
        var root = new DescriptorBuilder()
                .discover(Sequential.builder("sequential")
                        .child(Step.of("leaf", context -> {}))
                        .build());
        root.markScheduled();
        var listener = new Listener() {
            @Override
            public void onAfterExecution(final Descriptor descriptor) {
                if (descriptor.parent().isEmpty()) {
                    throw new IllegalStateException("after listener failed");
                }
            }
        };

        try (var scheduler = new Scheduler(2)) {
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(), listener, root, scheduler, new InstanceHolder());

            scheduler.executeDescriptor(root, context, ExecutionMode.RUN);

            assertThat(root.scheduledFuture()).isDone();
            assertThatThrownBy(() -> root.scheduledFuture().join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("recoverable onBefore listener failure does not run action body")
    void recoverableBeforeListenerFailureDoesNotRunActionBody() {
        var actionRan = new AtomicBoolean();
        var root = new DescriptorBuilder()
                .discover(Sequential.builder("sequential")
                        .child(Step.of("leaf", context -> actionRan.set(true)))
                        .build());
        root.markScheduled();
        var listener = new Listener() {
            @Override
            public void onBeforeExecution(final Descriptor descriptor) {
                if (descriptor.parent().isEmpty()) {
                    throw new IllegalStateException("before listener failed");
                }
            }
        };

        try (var scheduler = new Scheduler(2)) {
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(), listener, root, scheduler, new InstanceHolder());

            scheduler.executeDescriptor(root, context, ExecutionMode.RUN);

            assertThat(actionRan).isFalse();
            assertThat(root.isFailed()).isTrue();
            assertThat(root.scheduledFuture()).isDone();
            assertThatThrownBy(() -> root.scheduledFuture().join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    private static void assertDeferredRootListenerSemantics(final Action action) {
        var beforeThread = new AtomicReference<String>();
        var afterThread = new AtomicReference<String>();
        var afterTerminal = new AtomicBoolean();
        var futureDoneDuringAfter = new AtomicBoolean(true);
        var root = new DescriptorBuilder().discover(action);
        root.markScheduled();
        var listener = new Listener() {
            @Override
            public void onBeforeExecution(final Descriptor descriptor) {
                if (descriptor.parent().isEmpty()) {
                    beforeThread.set(Thread.currentThread().getName());
                }
            }

            @Override
            public void onAfterExecution(final Descriptor descriptor) {
                if (descriptor.parent().isEmpty()) {
                    afterThread.set(Thread.currentThread().getName());
                    afterTerminal.set(descriptor.isCompleted());
                    futureDoneDuringAfter.set(
                            ((MutableDescriptor) descriptor).scheduledFuture().isDone());
                }
            }
        };

        try (var scheduler = new Scheduler(2)) {
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(), listener, root, scheduler, new InstanceHolder());

            scheduler.executeDescriptor(root, context, ExecutionMode.RUN);

            assertThat(root.isPassed()).isTrue();
            assertThat(root.scheduledFuture()).isDone();
            assertThat(afterTerminal).isTrue();
            assertThat(futureDoneDuringAfter).isFalse();
            assertThat(afterThread.get()).isEqualTo(beforeThread.get());
        }
    }
}

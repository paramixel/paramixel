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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.FailException;

@DisplayName("Scheduler continuation callback parity")
class SchedulerContinuationCallbackParityTest {

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
    @DisplayName("deferred passing composite invokes callback completion once with no error")
    void deferredPassingCompositeInvokesCallbackCompletionOnceWithNoError() {
        var fixture = fixture(Sequential.builder("child")
                .child(Step.of("pass", context -> {}))
                .build());
        var callback = new CountingCallback();

        fixture.scheduler
                .schedule(fixture.child, ExecutionMode.RUN, fixture.context, callback)
                .join();

        assertThat(callback.admitted.get()).isEqualTo(1);
        assertThat(callback.started.get()).isEqualTo(1);
        assertThat(callback.completed.get()).isEqualTo(1);
        assertThat(callback.error.get()).isNull();
    }

    @Test
    @DisplayName("deferred failing composite invokes callback completion once with error")
    void deferredFailingCompositeInvokesCallbackCompletionOnceWithError() {
        var fixture = fixture(Sequential.builder("child")
                .child(Step.of("fail", context -> FailException.fail("boom")))
                .build());
        var callback = new CountingCallback();

        assertThatThrownBy(() -> fixture.scheduler
                        .schedule(fixture.child, ExecutionMode.RUN, fixture.context, callback)
                        .join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(FailException.class);

        assertThat(callback.admitted.get()).isEqualTo(1);
        assertThat(callback.started.get()).isEqualTo(1);
        assertThat(callback.completed.get()).isEqualTo(1);
        assertThat(callback.error.get()).isInstanceOf(FailException.class);
    }

    @Test
    @DisplayName("callback completion failure does not prevent future completion")
    void callbackCompletionFailureDoesNotPreventFutureCompletion() {
        var fixture = fixture(Sequential.builder("child")
                .child(Step.of("pass", context -> {}))
                .build());
        var callback = new ThrowingCompletionCallback();

        var result = fixture.scheduler
                .schedule(fixture.child, ExecutionMode.RUN, fixture.context, callback)
                .join();

        assertThat(result.isPassed()).isTrue();
        assertThat(fixture.child.scheduledFuture()).isDone();
        assertThat(callback.completed.get()).isEqualTo(1);
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
        return new Fixture(scheduler, context, child);
    }

    private record Fixture(Scheduler scheduler, ConcreteContext context, MutableDescriptor child) {}

    private static final class CountingCallback implements Scheduler.ExecutionCallback {
        private final AtomicInteger admitted = new AtomicInteger();
        private final AtomicInteger started = new AtomicInteger();
        private final AtomicInteger completed = new AtomicInteger();
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void onAdmitted() {
            admitted.incrementAndGet();
        }

        @Override
        public void onExecutionStart() {
            started.incrementAndGet();
        }

        @Override
        public void onExecutionComplete(final Throwable executionError) {
            completed.incrementAndGet();
            error.set(executionError);
        }
    }

    private static final class ThrowingCompletionCallback implements Scheduler.ExecutionCallback {
        private final AtomicInteger completed = new AtomicInteger();

        @Override
        public void onExecutionStart() {
            // Intentionally empty
        }

        @Override
        public void onExecutionComplete(final Throwable executionError) {
            completed.incrementAndGet();
            throw new IllegalStateException("callback failed");
        }
    }
}

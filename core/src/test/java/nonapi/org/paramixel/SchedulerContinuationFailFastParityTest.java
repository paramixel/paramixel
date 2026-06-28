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

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler continuation fail-fast parity")
class SchedulerContinuationFailFastParityTest {

    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler(2);
        scheduler.setFailFast(true);
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
    }

    @Test
    @DisplayName("deferred composite failure triggers fail-fast skip for later root sibling")
    void deferredCompositeFailureTriggersFailFastSkipForLaterRootSibling() {
        var secondRan = new AtomicBoolean();
        var first = Loop.builder("first")
                .body(Step.of("passing-child", context -> {}))
                .until(context -> false)
                .maxIterations(1)
                .build();
        var second = Step.of("second", context -> secondRan.set(true));
        var rootAction = Sequential.builder("root")
                .independent()
                .child(first)
                .child(second)
                .build();
        var root = new DescriptorBuilder().discover(rootAction);
        root.markScheduled();
        var firstDescriptor = (MutableDescriptor) root.children().get(0);
        var secondDescriptor = (MutableDescriptor) root.children().get(1);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());

        assertThatThrownBy(() -> scheduler
                        .schedule(firstDescriptor, ExecutionMode.RUN, context)
                        .join())
                .isInstanceOf(CompletionException.class);
        scheduler.schedule(secondDescriptor, ExecutionMode.RUN, context).join();

        assertThat(firstDescriptor.isFailed()).isTrue();
        assertThat(secondDescriptor.isSkipped()).isTrue();
        assertThat(secondRan).isFalse();
    }

    @Test
    @DisplayName("deferred composite records fail-fast after listener after callback")
    void deferredCompositeRecordsFailFastAfterListenerAfterCallback() {
        var events = Collections.synchronizedList(new ArrayList<String>());
        var listener = new Listener() {
            @Override
            public void onBeforeExecution(final org.paramixel.api.Descriptor descriptor) {
                events.add("before:" + descriptor.action().displayName());
            }

            @Override
            public void onAfterExecution(final org.paramixel.api.Descriptor descriptor) {
                events.add("after:" + descriptor.action().displayName() + ":" + statusName(descriptor));
            }
        };
        var first = Loop.builder("first")
                .body(Step.of("passing-child", context -> {}))
                .until(context -> false)
                .maxIterations(1)
                .build();
        var second = Step.of("second", context -> {});
        var rootAction = Sequential.builder("root")
                .independent()
                .child(first)
                .child(second)
                .build();
        var root = new DescriptorBuilder().discover(rootAction);
        root.markScheduled();
        var firstDescriptor = (MutableDescriptor) root.children().get(0);
        var secondDescriptor = (MutableDescriptor) root.children().get(1);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), listener, root, scheduler, new InstanceHolder());

        assertThatThrownBy(() -> scheduler
                        .schedule(firstDescriptor, ExecutionMode.RUN, context)
                        .join())
                .isInstanceOf(CompletionException.class);
        scheduler.schedule(secondDescriptor, ExecutionMode.RUN, context).join();

        assertThat(events).containsSubsequence("after:first:FAILED", "before:second", "after:second:SKIPPED");
    }

    private static String statusName(final org.paramixel.api.Descriptor descriptor) {
        if (descriptor.isFailed()) {
            return "FAILED";
        }
        if (descriptor.isAborted()) {
            return "ABORTED";
        }
        if (descriptor.isSkipped()) {
            return "SKIPPED";
        }
        if (descriptor.isPassed()) {
            return "PASSED";
        }
        return "NON_TERMINAL";
    }
}

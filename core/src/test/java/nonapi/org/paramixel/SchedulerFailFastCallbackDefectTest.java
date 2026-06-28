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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;

/**
 * Regression test for the callback-contract defect recorded in ISSUES.md (L3) against the
 * scheduler's fail-fast skip path.
 *
 * <p>L3 is now fixed: a fail-fast-skipped descriptor receives no {@link Scheduler.ExecutionCallback}
 * invocations at all, preserving the documented paired lifecycle
 * ({@code onAdmitted}/{@code onExecutionStart}/{@code onExecutionComplete}).
 */
@DisplayName("Scheduler fail-fast callback regression (ISSUES.md L3)")
class SchedulerFailFastCallbackDefectTest {

    @Test
    @Timeout(5)
    @DisplayName("L3: fail-fast skipped descriptor must not receive an unpaired onExecutionComplete")
    void failFastSkippedDescriptorMustNotReceiveUnpairedOnExecutionComplete() {
        var scheduler = new Scheduler(1);
        try {
            scheduler.setFailFast(true);
            var root = new ConcreteDescriptor(Step.of("root", context -> {}));
            var failing = newChildDescriptor(root, Step.of("failing", context -> {
                throw new IllegalStateException("boom");
            }));
            var skipped = newChildDescriptor(root, Step.of("skipped", context -> {}));
            var context = newContext(scheduler, root);

            // Trigger a failure under fail-fast so failureOccurred is set for the next sibling.
            assertThatThrownBy(() -> scheduler
                            .schedule(failing, ExecutionMode.RUN, context)
                            .join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);

            var admittedInvoked = new AtomicBoolean();
            var startInvoked = new AtomicBoolean();
            var completeInvoked = new AtomicBoolean();
            var callback = new Scheduler.ExecutionCallback() {
                @Override
                public void onAdmitted() {
                    admittedInvoked.set(true);
                }

                @Override
                public void onExecutionStart() {
                    startInvoked.set(true);
                }

                @Override
                public void onExecutionComplete(final Throwable error) {
                    completeInvoked.set(true);
                }
            };

            // Schedule the not-yet-started sibling; fail-fast must skip its execution.
            assertThatCode(() -> scheduler
                            .schedule(skipped, ExecutionMode.RUN, context, callback)
                            .join())
                    .doesNotThrowAnyException();

            assertThat(skipped.isSkipped()).isTrue();
            // Contract: onAdmitted is documented as "invoked exactly once per scheduled action,
            // before execution begins." A skipped action never begins execution, so admission and
            // execution-start are correctly omitted ...
            assertThat(admittedInvoked)
                    .as("onAdmitted must not fire for a descriptor whose execution never begins")
                    .isFalse();
            assertThat(startInvoked)
                    .as("onExecutionStart must not fire for a skipped descriptor")
                    .isFalse();
            // ... and therefore onExecutionComplete must NOT be delivered unpaired. (Bug L3: it is.)
            assertThat(completeInvoked)
                    .as("onExecutionComplete must be paired with onAdmitted/onExecutionStart (ISSUES.md L3)")
                    .isFalse();
        } finally {
            scheduler.close();
        }
    }

    private static ConcreteContext newContext(final Scheduler scheduler, final MutableDescriptor root) {
        return new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());
    }

    private static MutableDescriptor newChildDescriptor(final MutableDescriptor parent, final Action action) {
        var child = new ConcreteDescriptor(parent, action);
        parent.addChild(child);
        return child;
    }
}

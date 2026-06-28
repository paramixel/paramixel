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

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;

@DisplayName("Action execution strategy defect regressions")
class ActionExecutionStrategiesDefectTest {

    @Test
    @DisplayName("handleTimeout returns existing terminal status when child already completed")
    void handleTimeoutReturnsExistingTerminalStatusWhenChildAlreadyCompleted() throws Exception {
        var timeout = Timeout.builder("timeout")
                .body(Step.of("child", context -> {}))
                .timeout(Duration.ofMillis(1))
                .build();
        var child = new ConcreteDescriptor(Step.of("child", context -> {}));
        child.setStatus(Status.RUNNING);
        child.setStatus(Status.PASSED);

        var status = invokeHandleTimeout(timeout, child);

        assertThat(status).isEqualTo(Status.PASSED);
        assertThat(child.isPassed()).isTrue();
    }

    private static Status invokeHandleTimeout(final Timeout timeout, final MutableDescriptor child) throws Exception {
        Method method = ActionExecutionStrategies.class.getDeclaredMethod(
                "handleTimeout", Timeout.class, MutableDescriptor.class, TimeoutException.class);
        method.setAccessible(true);
        var cause =
                new TimeoutException("timeout exceeded: " + timeout.timeout().toMillis() + " ms");
        return (Status) method.invoke(null, timeout, child, cause);
    }

    @Test
    @DisplayName("handleTimeout produces non-null descendant abort messages")
    void handleTimeoutProducesNonNullDescendantAbortMessages() throws Exception {
        var timeout = Timeout.builder("timeout")
                .body(Step.of("child", context -> {}))
                .timeout(Duration.ofMillis(1))
                .build();
        var root = new ConcreteDescriptor(Step.of("root", context -> {}));
        var child = new ConcreteDescriptor(root, Step.of("child", context -> {}));
        var grandchild = new ConcreteDescriptor(child, Step.of("gc", context -> {}));
        root.addChild(child);
        child.addChild(grandchild);
        root.freeze();
        root.markScheduled();
        child.markScheduled();
        grandchild.markScheduled();
        child.setStatus(Status.RUNNING);

        invokeHandleTimeout(timeout, child);

        assertThat(grandchild.isAborted()).as("grandchild aborted").isTrue();
        assertThat(grandchild.status().message())
                .isPresent()
                .hasValueSatisfying(msg -> assertThat(msg)
                        .as("descendant message must not contain literal 'null'")
                        .startsWith("cancelled by ancestor: timeout exceeded:"));
    }

    @Test
    @DisplayName("abort marks a frozen subtree terminal and recuses into before/children/after")
    void abortMarksFrozenSubtreeTerminalAndRecuses() throws Exception {
        var cause = new CancellationException("test cancel");
        var root = new ConcreteDescriptor(Step.of("root", context -> {}));
        var before = new ConcreteDescriptor(root, Step.of("before", context -> {}));
        var child1 = new ConcreteDescriptor(root, Step.of("c1", context -> {}));
        var child2 = new ConcreteDescriptor(root, Step.of("c2", context -> {}));
        var after = new ConcreteDescriptor(root, Step.of("after", context -> {}));
        root.setBefore(before);
        root.addChild(child1);
        root.addChild(child2);
        root.setAfter(after);
        root.freeze();
        root.markScheduled();
        before.markScheduled();
        child1.markScheduled();
        child2.markScheduled();
        after.markScheduled();

        root.abort(Status.FAILED, cause);

        assertThat(root.isFailed()).as("root failed").isTrue();
        assertThat(root.status()).isEqualTo(Status.FAILED);
        // Descendants are ABORTED when recursively cancelled.
        assertThat(before.isAborted()).as("before aborted").isTrue();
        assertThat(child1.isAborted()).as("child1 aborted").isTrue();
        assertThat(child2.isAborted()).as("child2 aborted").isTrue();
        assertThat(after.isAborted()).as("after aborted").isTrue();
    }

    @Test
    @DisplayName("abort is idempotent — re-calling on a terminal subtree is a no-op")
    void abortIsIdempotentOnTerminalSubtree() {
        var root = new ConcreteDescriptor(Step.of("leaf", context -> {}));
        root.freeze();
        root.markScheduled();
        // Transition to a normal terminal status.
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);

        root.abort(Status.FAILED, new RuntimeException("late cancel"));

        // Already PASSED — abort skipped this node.
        assertThat(root.isPassed()).as("still passed").isTrue();
    }

    @Test
    @DisplayName("abort marks an already-RUNNING subtree terminal")
    void abortMarksAlreadyRunningSubtreeTerminal() {
        var cause = new RuntimeException("cancel");
        var root = new ConcreteDescriptor(Step.of("leaf", context -> {}));
        root.freeze();
        root.markScheduled();
        root.setStatus(Status.RUNNING); // mid-execution

        root.abort(Status.FAILED, cause);

        assertThat(root.isFailed()).as("transitioned from RUNNING to FAILED").isTrue();
    }
}

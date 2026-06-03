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

package nonapi.org.paramixel.listener;

import static org.assertj.core.api.Assertions.assertThat;

import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;

@DisplayName("Listeners")
class ListenersTest {

    @Test
    @DisplayName("formatAnsiStatus formats ABORTED with orange")
    void formatAnsiStatusAborted() {
        String result = Listeners.formatAnsiStatus(Status.ABORTED);
        assertThat(result).contains("ABORTED");
    }

    @Test
    @DisplayName("formatAnsiStatus formats RUNNING with bold blue")
    void formatAnsiStatusRunning() {
        String result = Listeners.formatAnsiStatus(Status.RUNNING);
        assertThat(result).contains("RUNNING");
    }

    @Test
    @DisplayName("formatAnsiStatus formats PASSED with green")
    void formatAnsiStatusPassed() {
        String result = Listeners.formatAnsiStatus(Status.PASSED);
        assertThat(result).contains("PASSED");
    }

    @Test
    @DisplayName("formatAnsiStatus formats FAILED with red")
    void formatAnsiStatusFailed() {
        String result = Listeners.formatAnsiStatus(Status.FAILED);
        assertThat(result).contains("FAILED");
    }

    @Test
    @DisplayName("formatAnsiStatus formats SKIPPED with bold yellow")
    void formatAnsiStatusSkipped() {
        String result = Listeners.formatAnsiStatus(Status.SKIPPED);
        assertThat(result).contains("SKIPPED");
    }

    @Test
    @DisplayName("formatAnsiStatus formats PENDING status as plain text")
    void formatAnsiStatusPending() {
        String result = Listeners.formatAnsiStatus(Status.PENDING);
        assertThat(result).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("formatException returns null for non-failed descriptor")
    void formatExceptionReturnsNullForNonFailedDescriptor() {
        var action = Step.of("test", context -> {});
        var descriptor = new ConcreteDescriptor(action);
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.PASSED);

        assertThat(Listeners.formatException(descriptor)).isNull();
    }

    @Test
    @DisplayName("formatException returns throwable class and message for failed descriptor")
    void formatExceptionReturnsThrowableClassAndMessage() {
        var action = Step.of("test", context -> {});
        var descriptor = new ConcreteDescriptor(action);
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.failed("something broke", new RuntimeException("something broke")));

        String result = Listeners.formatException(descriptor);
        assertThat(result).startsWith("java.lang.RuntimeException: something broke");
    }

    @Test
    @DisplayName("formatException returns throwable class name when message is null")
    void formatExceptionReturnsClassNameWhenMessageNull() {
        var action = Step.of("test", context -> {});
        var descriptor = new ConcreteDescriptor(action);
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.failed("failed", new RuntimeException()));

        String result = Listeners.formatException(descriptor);
        assertThat(result).isEqualTo("java.lang.RuntimeException");
    }

    @Test
    @DisplayName("formatException falls back to status message when no throwable present")
    void formatExceptionFallsBackToStatusMessage() {
        var action = Step.of("test", context -> {});
        var descriptor = new ConcreteDescriptor(action);
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.failed("status-only message"));

        String result = Listeners.formatException(descriptor);
        assertThat(result).isEqualTo("status-only message");
    }

    @Test
    @DisplayName("formatException returns null when failed with no message or throwable")
    void formatExceptionReturnsNullWhenNoDetails() {
        var action = Step.of("test", context -> {});
        var descriptor = new ConcreteDescriptor(action);
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.FAILED);

        assertThat(Listeners.formatException(descriptor)).isNull();
    }

    @Test
    @DisplayName("formatNamePath returns action name for single descriptor")
    void formatNamePathReturnsActionNameForSingleDescriptor() {
        var action = Step.of("my-action", context -> {});
        var descriptor = new ConcreteDescriptor(action);
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.PASSED);

        assertThat(Listeners.formatNamePath(descriptor)).isEqualTo("my-action");
    }

    @Test
    @DisplayName("formatNamePath returns path for parent and child")
    void formatNamePathReturnsPathForParentAndChild() {
        var parentAction = Step.of("parent-action", context -> {});
        var childAction = Step.of("child-action", context -> {});
        MutableDescriptor parent = new ConcreteDescriptor(parentAction);
        MutableDescriptor child = new ConcreteDescriptor(parent, childAction);
        parent.addChild(child);
        parent.setStatus(Status.RUNNING);
        parent.setStatus(Status.PASSED);
        child.setStatus(Status.RUNNING);
        child.setStatus(Status.PASSED);

        assertThat(Listeners.formatNamePath(child)).isEqualTo("parent-action / child-action");
    }

    @Test
    @DisplayName("formatNamePath returns path for three-level tree")
    void formatNamePathReturnsPathForThreeLevelTree() {
        var grandAction = Step.of("grand-action", context -> {});
        var parentAction = Step.of("parent-action", context -> {});
        var childAction = Step.of("child-action", context -> {});
        MutableDescriptor grand = new ConcreteDescriptor(grandAction);
        MutableDescriptor parent = new ConcreteDescriptor(grand, parentAction);
        MutableDescriptor child = new ConcreteDescriptor(parent, childAction);
        grand.addChild(parent);
        parent.addChild(child);
        grand.setStatus(Status.RUNNING);
        grand.setStatus(Status.PASSED);
        parent.setStatus(Status.RUNNING);
        parent.setStatus(Status.PASSED);
        child.setStatus(Status.RUNNING);
        child.setStatus(Status.PASSED);

        assertThat(Listeners.formatNamePath(child)).isEqualTo("grand-action / parent-action / child-action");
    }

    @Test
    @DisplayName("formatIdPath returns id for single descriptor")
    void formatIdPathReturnsIdForSingleDescriptor() {
        var action = Step.of("my-action", context -> {});
        var descriptor = new ConcreteDescriptor(action);
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.PASSED);

        assertThat(Listeners.formatIdPath(descriptor)).isEqualTo(descriptor.id());
    }

    @Test
    @DisplayName("formatIdPath returns path for parent and child")
    void formatIdPathReturnsPathForParentAndChild() {
        var parentAction = Step.of("parent-action", context -> {});
        var childAction = Step.of("child-action", context -> {});
        MutableDescriptor parent = new ConcreteDescriptor(parentAction);
        MutableDescriptor child = new ConcreteDescriptor(parent, childAction);
        parent.addChild(child);
        parent.setStatus(Status.RUNNING);
        parent.setStatus(Status.PASSED);
        child.setStatus(Status.RUNNING);
        child.setStatus(Status.PASSED);

        assertThat(Listeners.formatIdPath(child)).isEqualTo(parent.id() + "-" + child.id());
    }

    @Test
    @DisplayName("elapsedMillis returns zero when completedAt is not set")
    void elapsedMillisReturnsZeroWhenNotCompleted() {
        var action = Step.of("test", context -> {});
        var descriptor = new ConcreteDescriptor(action);
        descriptor.setStatus(Status.RUNNING);

        assertThat(Listeners.elapsedMillis(descriptor)).isZero();
    }

    @Test
    @DisplayName("elapsedMillis returns positive value when both timestamps are set")
    void elapsedMillisReturnsPositiveValueWhenBothTimestampsSet() {
        var action = Step.of("test", context -> {});
        var descriptor = new ConcreteDescriptor(action);
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.PASSED);

        assertThat(Listeners.elapsedMillis(descriptor)).isGreaterThanOrEqualTo(0);
    }
}

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

package org.paramixel.maven.plugin.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.maven.plugin.internal.summary.SummaryNode;
import org.paramixel.maven.plugin.internal.summary.SummaryStatus;

@DisplayName("SummaryNode tests")
class SummaryNodeTest {

    @Nested
    @DisplayName("Constructor tests")
    class ConstructorTests {

        @Test
        @DisplayName("should throw NullPointerException for null uniqueId")
        void shouldThrowForNullUniqueId() {
            assertThatThrownBy(() -> new SummaryNode(null, "displayName"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("uniqueId must not be null");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank uniqueId")
        void shouldThrowForBlankUniqueId() {
            assertThatThrownBy(() -> new SummaryNode("  ", "displayName"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("uniqueId must not be blank");
        }

        @Test
        @DisplayName("should throw NullPointerException for null displayName")
        void shouldThrowForNullDisplayName() {
            assertThatThrownBy(() -> new SummaryNode("id", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("displayName must not be null");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank displayName")
        void shouldThrowForBlankDisplayName() {
            assertThatThrownBy(() -> new SummaryNode("id", "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("displayName must not be blank");
        }

        @Test
        @DisplayName("should create node with valid arguments")
        void shouldCreateNodeWithValidArguments() {
            var node = new SummaryNode("id", "displayName");
            assertThat(node.getUniqueId()).isEqualTo("id");
            assertThat(node.getDisplayName()).isEqualTo("displayName");
        }
    }

    @Nested
    @DisplayName("addChild tests")
    class AddChildTests {

        @Test
        @DisplayName("should throw NullPointerException for null child")
        void shouldThrowForNullChild() {
            var node = new SummaryNode("id", "displayName");
            assertThatThrownBy(() -> node.addChild(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("child must not be null");
        }

        @Test
        @DisplayName("should add child and set parent reference")
        void shouldAddChildAndSetParent() {
            var parent = new SummaryNode("parent", "Parent");
            var child = new SummaryNode("child", "Child");
            parent.addChild(child);
            assertThat(parent.getChildren()).containsExactly(child);
            assertThat(child.getParent()).isSameAs(parent);
        }

        @Test
        @DisplayName("should add multiple children")
        void shouldAddMultipleChildren() {
            var parent = new SummaryNode("parent", "Parent");
            var child1 = new SummaryNode("child1", "Child1");
            var child2 = new SummaryNode("child2", "Child2");
            parent.addChild(child1);
            parent.addChild(child2);
            assertThat(parent.getChildren()).containsExactly(child1, child2);
        }
    }

    @Nested
    @DisplayName("setStatus tests")
    class SetStatusTests {

        @Test
        @DisplayName("should throw NullPointerException for null status")
        void shouldThrowForNullStatus() {
            var node = new SummaryNode("id", "displayName");
            assertThatThrownBy(() -> node.setStatus(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("status must not be null");
        }

        @Test
        @DisplayName("should set and get status")
        void shouldSetAndGetStatus() {
            var node = new SummaryNode("id", "displayName");
            node.setStatus(SummaryStatus.SUCCESSFUL);
            assertThat(node.getStatus()).isEqualTo(SummaryStatus.SUCCESSFUL);
        }
    }

    @Nested
    @DisplayName("throwable tests")
    class ThrowableTests {

        @Test
        @DisplayName("should return null throwable by default")
        void shouldReturnNullThrowableByDefault() {
            var node = new SummaryNode("id", "displayName");
            assertThat(node.getThrowable()).isNull();
        }

        @Test
        @DisplayName("should set and get throwable")
        void shouldSetAndGetThrowable() {
            var node = new SummaryNode("id", "displayName");
            var exception = new RuntimeException("test");
            node.setThrowable(exception);
            assertThat(node.getThrowable()).isSameAs(exception);
        }

        @Test
        @DisplayName("should clear throwable with null")
        void shouldClearThrowableWithNull() {
            var node = new SummaryNode("id", "displayName");
            node.setThrowable(new RuntimeException("test"));
            node.setThrowable(null);
            assertThat(node.getThrowable()).isNull();
        }
    }

    @Nested
    @DisplayName("skipReason tests")
    class SkipReasonTests {

        @Test
        @DisplayName("should return null skip reason by default")
        void shouldReturnNullSkipReasonByDefault() {
            var node = new SummaryNode("id", "displayName");
            assertThat(node.getSkipReason()).isNull();
        }

        @Test
        @DisplayName("should set and get skip reason")
        void shouldSetAndGetSkipReason() {
            var node = new SummaryNode("id", "displayName");
            node.setSkipReason("not implemented");
            assertThat(node.getSkipReason()).isEqualTo("not implemented");
        }

        @Test
        @DisplayName("should clear skip reason with null")
        void shouldClearSkipReasonWithNull() {
            var node = new SummaryNode("id", "displayName");
            node.setSkipReason("not implemented");
            node.setSkipReason(null);
            assertThat(node.getSkipReason()).isNull();
        }
    }

    @Nested
    @DisplayName("recordStart and recordEnd tests")
    class RecordTimingTests {

        @Test
        @DisplayName("should publish timing state through volatile fields")
        void shouldPublishTimingStateThroughVolatileFields() throws Exception {
            assertThat(Modifier.isVolatile(
                            SummaryNode.class.getDeclaredField("startTime").getModifiers()))
                    .isTrue();
            assertThat(Modifier.isVolatile(
                            SummaryNode.class.getDeclaredField("duration").getModifiers()))
                    .isTrue();
        }

        @Test
        @DisplayName("should return 0 start time before recordStart")
        void shouldReturnZeroStartTimeBeforeRecordStart() {
            var node = new SummaryNode("id", "displayName");
            assertThat(node.getStartTime()).isEqualTo(0L);
        }

        @Test
        @DisplayName("should return 0 duration before recordEnd")
        void shouldReturnZeroDurationBeforeRecordEnd() {
            var node = new SummaryNode("id", "displayName");
            assertThat(node.getDuration()).isEqualTo(0L);
        }

        @Test
        @DisplayName("should record start time")
        void shouldRecordStartTime() {
            var node = new SummaryNode("id", "displayName");
            node.recordStart();
            assertThat(node.getStartTime()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("should compute duration from recordStart and recordEnd")
        void shouldComputeDuration() {
            var node = new SummaryNode("id", "displayName");
            node.recordStart();
            node.recordEnd();
            assertThat(node.getDuration()).isGreaterThanOrEqualTo(0L);
        }

        @Test
        @DisplayName("should update duration when recordEnd called multiple times")
        void shouldUpdateDurationOnMultipleRecordEnd() {
            var node = new SummaryNode("id", "displayName");
            node.recordStart();
            node.recordEnd();
            var firstDuration = node.getDuration();
            node.recordEnd();
            assertThat(node.getDuration()).isGreaterThanOrEqualTo(firstDuration);
        }
    }

    @Nested
    @DisplayName("hasFailures tests")
    class HasFailuresTests {

        @Test
        @DisplayName("should return false when no failures exist")
        void shouldReturnFalseWhenNoFailures() {
            var node = new SummaryNode("id", "displayName");
            node.setStatus(SummaryStatus.SUCCESSFUL);
            assertThat(node.hasFailures()).isFalse();
        }

        @Test
        @DisplayName("should return true when node itself has failed")
        void shouldReturnTrueWhenNodeHasFailed() {
            var node = new SummaryNode("id", "displayName");
            node.setStatus(SummaryStatus.FAILED);
            assertThat(node.hasFailures()).isTrue();
        }

        @Test
        @DisplayName("should return true when child has failed")
        void shouldReturnTrueWhenChildHasFailed() {
            var parent = new SummaryNode("parent", "Parent");
            parent.setStatus(SummaryStatus.SUCCESSFUL);
            var child = new SummaryNode("child", "Child");
            child.setStatus(SummaryStatus.FAILED);
            parent.addChild(child);
            assertThat(parent.hasFailures()).isTrue();
        }

        @Test
        @DisplayName("should return true when deeply nested descendant has failed")
        void shouldReturnTrueWhenDeeplyNestedDescendantHasFailed() {
            var root = new SummaryNode("root", "Root");
            root.setStatus(SummaryStatus.SUCCESSFUL);
            var child = new SummaryNode("child", "Child");
            child.setStatus(SummaryStatus.SUCCESSFUL);
            root.addChild(child);
            var grandchild = new SummaryNode("grandchild", "Grandchild");
            grandchild.setStatus(SummaryStatus.FAILED);
            child.addChild(grandchild);
            assertThat(root.hasFailures()).isTrue();
        }

        @Test
        @DisplayName("should return false when status is null")
        void shouldReturnFalseWhenStatusIsNull() {
            var node = new SummaryNode("id", "displayName");
            assertThat(node.hasFailures()).isFalse();
        }
    }

    @Nested
    @DisplayName("toString tests")
    class ToStringTests {

        @Test
        @DisplayName("should include uniqueId, displayName, status, and duration in toString")
        void shouldIncludeFieldsInToString() {
            var node = new SummaryNode("test-id", "TestName");
            node.setStatus(SummaryStatus.SUCCESSFUL);
            node.recordStart();
            node.recordEnd();
            var result = node.toString();
            assertThat(result).contains("test-id");
            assertThat(result).contains("TestName");
            assertThat(result).contains("SUCCESSFUL");
            assertThat(result).contains("ms");
        }

        @Test
        @DisplayName("should include null status in toString")
        void shouldIncludeNullStatusInToString() {
            var node = new SummaryNode("id", "name");
            var result = node.toString();
            assertThat(result).contains("null");
        }
    }
}

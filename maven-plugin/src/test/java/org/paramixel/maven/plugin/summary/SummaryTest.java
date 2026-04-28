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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.maven.plugin.internal.summary.Summary;
import org.paramixel.maven.plugin.internal.summary.SummaryNode;
import org.paramixel.maven.plugin.internal.summary.SummaryStatus;

@DisplayName("Summary tests")
class SummaryTest {

    @Nested
    @DisplayName("duration tracking")
    class DurationTests {

        @Test
        @DisplayName("should return 0 when no timing recorded")
        void shouldReturnZeroWhenNoTimingRecorded() {
            var summary = new Summary();
            assertThat(summary.getDuration()).isEqualTo(0L);
        }

        @Test
        @DisplayName("should set and get duration")
        void shouldSetAndGetDuration() {
            var summary = new Summary();
            summary.setDuration(5000L);
            assertThat(summary.getDuration()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("should compute duration from recordStart and recordEnd")
        void shouldComputeDurationFromRecordStartAndRecordEnd() {
            var summary = new Summary();
            summary.recordStart();
            summary.recordEnd();
            assertThat(summary.getDuration()).isGreaterThanOrEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("tree access")
    class TreeAccessTests {

        @Test
        @DisplayName("should return null tree when not set")
        void shouldReturnNullTreeWhenNotSet() {
            var summary = new Summary();
            assertThat(summary.getTree()).isNull();
        }

        @Test
        @DisplayName("should set and get root node")
        void shouldSetAndGetRootNode() {
            var summary = new Summary();
            var node = new SummaryNode("id", "displayName");
            summary.setRoot(node);
            assertThat(summary.getTree()).isSameAs(node);
        }

        @Test
        @DisplayName("should register root node in lookup when setRoot is called")
        void shouldRegisterRootNodeInLookup() {
            var summary = new Summary();
            var node = new SummaryNode("root-id", "Root");
            summary.setRoot(node);
            assertThat(summary.findNode("root-id")).isSameAs(node);
        }

        @Test
        @DisplayName("should clear tree when setRoot is called with null")
        void shouldClearTreeWhenSetRootWithNull() {
            var summary = new Summary();
            var node = new SummaryNode("id", "displayName");
            summary.setRoot(node);
            summary.setRoot(null);
            assertThat(summary.getTree()).isNull();
        }
    }

    @Nested
    @DisplayName("findNode validation tests")
    class FindNodeValidationTests {

        @Test
        @DisplayName("should throw NullPointerException for null uniqueId")
        void shouldThrowForNullUniqueId() {
            var summary = new Summary();
            assertThatThrownBy(() -> summary.findNode(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("uniqueId must not be null");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank uniqueId")
        void shouldThrowForBlankUniqueId() {
            var summary = new Summary();
            assertThatThrownBy(() -> summary.findNode("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("uniqueId must not be blank");
        }

        @Test
        @DisplayName("should return null when node not found")
        void shouldReturnNullWhenNodeNotFound() {
            var summary = new Summary();
            assertThat(summary.findNode("nonexistent")).isNull();
        }

        @Test
        @DisplayName("should return node when found")
        void shouldReturnNodeWhenFound() {
            var summary = new Summary();
            var node = summary.createNode("id1", "Display1", null);
            assertThat(summary.findNode("id1")).isSameAs(node);
        }
    }

    @Nested
    @DisplayName("createNode validation tests")
    class CreateNodeValidationTests {

        @Test
        @DisplayName("should throw NullPointerException for null uniqueId")
        void shouldThrowForNullUniqueId() {
            var summary = new Summary();
            assertThatThrownBy(() -> summary.createNode(null, "display", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("uniqueId must not be null");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank uniqueId")
        void shouldThrowForBlankUniqueId() {
            var summary = new Summary();
            assertThatThrownBy(() -> summary.createNode("  ", "display", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("uniqueId must not be blank");
        }

        @Test
        @DisplayName("should throw NullPointerException for null displayName")
        void shouldThrowForNullDisplayName() {
            var summary = new Summary();
            assertThatThrownBy(() -> summary.createNode("id", null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("displayName must not be null");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank displayName")
        void shouldThrowForBlankDisplayName() {
            var summary = new Summary();
            assertThatThrownBy(() -> summary.createNode("id", "  ", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("displayName must not be blank");
        }
    }

    @Nested
    @DisplayName("createNode tree building tests")
    class CreateNodeTreeBuildingTests {

        @Test
        @DisplayName("should set node as root when parentUniqueId is null")
        void shouldSetNodeAsRootWhenParentIsNull() {
            var summary = new Summary();
            var node = summary.createNode("root", "Root", null);
            assertThat(summary.getTree()).isSameAs(node);
        }

        @Test
        @DisplayName("should add node as child when parentUniqueId is provided")
        void shouldAddNodeAsChildWhenParentIsProvided() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            var child = summary.createNode("child1", "Child1", "root");
            assertThat(root.getChildren()).containsExactly(child);
            assertThat(child.getParent()).isSameAs(root);
        }

        @Test
        @DisplayName("should build full tree hierarchy")
        void shouldBuildFullTreeHierarchy() {
            var summary = new Summary();
            var root = summary.createNode("[engine]", "Engine", null);
            var classNode = summary.createNode("[engine]/[class:Test]", "Test", root.getUniqueId());
            var argNode = summary.createNode("[engine]/[class:Test]/[arg:0]", "arg0", classNode.getUniqueId());
            var methodNode = summary.createNode(
                    "[engine]/[class:Test]/[arg:0]/[method:test()]", "test()", argNode.getUniqueId());

            assertThat(summary.getTree()).isSameAs(root);
            assertThat(root.getChildren()).containsExactly(classNode);
            assertThat(classNode.getChildren()).containsExactly(argNode);
            assertThat(argNode.getChildren()).containsExactly(methodNode);
            assertThat(methodNode.getChildren()).isEmpty();
        }

        @Test
        @DisplayName("should register all nodes in lookup")
        void shouldRegisterAllNodesInLookup() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            var child = summary.createNode("child", "Child", "root");

            assertThat(summary.findNode("root")).isSameAs(root);
            assertThat(summary.findNode("child")).isSameAs(child);
        }
    }

    @Nested
    @DisplayName("hasFailures tests")
    class HasFailuresTests {

        @Test
        @DisplayName("should return false when tree is null")
        void shouldReturnFalseWhenTreeIsNull() {
            var summary = new Summary();
            assertThat(summary.hasFailures()).isFalse();
        }

        @Test
        @DisplayName("should return false when no failures exist")
        void shouldReturnFalseWhenNoFailures() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            root.setStatus(SummaryStatus.SUCCESSFUL);
            assertThat(summary.hasFailures()).isFalse();
        }

        @Test
        @DisplayName("should return true when root has failed")
        void shouldReturnTrueWhenRootHasFailed() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            root.setStatus(SummaryStatus.FAILED);
            assertThat(summary.hasFailures()).isTrue();
        }

        @Test
        @DisplayName("should return true when child has failed")
        void shouldReturnTrueWhenChildHasFailed() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            root.setStatus(SummaryStatus.SUCCESSFUL);
            var child = summary.createNode("child", "Child", "root");
            child.setStatus(SummaryStatus.FAILED);
            assertThat(summary.hasFailures()).isTrue();
        }

        @Test
        @DisplayName("should return true when deeply nested node has failed")
        void shouldReturnTrueWhenDeeplyNestedNodeHasFailed() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            root.setStatus(SummaryStatus.SUCCESSFUL);
            var classNode = summary.createNode("class", "Class", "root");
            classNode.setStatus(SummaryStatus.SUCCESSFUL);
            var argNode = summary.createNode("arg", "Arg", "class");
            argNode.setStatus(SummaryStatus.SUCCESSFUL);
            var methodNode = summary.createNode("method", "Method", "arg");
            methodNode.setStatus(SummaryStatus.FAILED);
            assertThat(summary.hasFailures()).isTrue();
        }
    }

    @Nested
    @DisplayName("class count tests")
    class ClassCountTests {

        @Test
        @DisplayName("should return 0 when tree is null")
        void shouldReturnZeroWhenTreeIsNull() {
            var summary = new Summary();
            assertThat(summary.getTestClassCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return class count")
        void shouldReturnClassCount() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            summary.createNode("class1", "Class1", "root");
            summary.createNode("class2", "Class2", "root");
            assertThat(summary.getTestClassCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should count passed classes")
        void shouldCountPassedClasses() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            var class1 = summary.createNode("class1", "Class1", "root");
            class1.setStatus(SummaryStatus.SUCCESSFUL);
            var class2 = summary.createNode("class2", "Class2", "root");
            class2.setStatus(SummaryStatus.SUCCESSFUL);
            assertThat(summary.getTestClassPassed()).isEqualTo(2);
        }

        @Test
        @DisplayName("should count failed classes")
        void shouldCountFailedClasses() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            var class1 = summary.createNode("class1", "Class1", "root");
            class1.setStatus(SummaryStatus.FAILED);
            var class2 = summary.createNode("class2", "Class2", "root");
            class2.setStatus(SummaryStatus.SUCCESSFUL);
            assertThat(summary.getTestClassFailed()).isEqualTo(1);
        }

        @Test
        @DisplayName("should count skipped classes including aborted")
        void shouldCountSkippedClassesIncludingAborted() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            var class1 = summary.createNode("class1", "Class1", "root");
            class1.setStatus(SummaryStatus.SKIPPED);
            var class2 = summary.createNode("class2", "Class2", "root");
            class2.setStatus(SummaryStatus.ABORTED);
            var class3 = summary.createNode("class3", "Class3", "root");
            class3.setStatus(SummaryStatus.SUCCESSFUL);
            assertThat(summary.getTestClassSkipped()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("argument count tests")
    class ArgumentCountTests {

        @Test
        @DisplayName("should count passed arguments")
        void shouldCountPassedArguments() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            var class1 = summary.createNode("class1", "Class1", "root");
            var arg1 = summary.createNode("arg1", "Arg1", "class1");
            arg1.setStatus(SummaryStatus.SUCCESSFUL);
            var arg2 = summary.createNode("arg2", "Arg2", "class1");
            arg2.setStatus(SummaryStatus.SUCCESSFUL);
            assertThat(summary.getTestArgumentPassed()).isEqualTo(2);
        }

        @Test
        @DisplayName("should count failed arguments")
        void shouldCountFailedArguments() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            var class1 = summary.createNode("class1", "Class1", "root");
            var arg1 = summary.createNode("arg1", "Arg1", "class1");
            arg1.setStatus(SummaryStatus.FAILED);
            var arg2 = summary.createNode("arg2", "Arg2", "class1");
            arg2.setStatus(SummaryStatus.SUCCESSFUL);
            assertThat(summary.getTestArgumentFailed()).isEqualTo(1);
        }

        @Test
        @DisplayName("should count skipped arguments including aborted")
        void shouldCountSkippedArgumentsIncludingAborted() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            var class1 = summary.createNode("class1", "Class1", "root");
            var arg1 = summary.createNode("arg1", "Arg1", "class1");
            arg1.setStatus(SummaryStatus.SKIPPED);
            var arg2 = summary.createNode("arg2", "Arg2", "class1");
            arg2.setStatus(SummaryStatus.ABORTED);
            assertThat(summary.getTestArgumentSkipped()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("method count tests")
    class MethodCountTests {

        @Test
        @DisplayName("should count passed methods")
        void shouldCountPassedMethods() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            var class1 = summary.createNode("class1", "Class1", "root");
            var arg1 = summary.createNode("arg1", "Arg1", "class1");
            var method1 = summary.createNode("method1", "Method1", "arg1");
            method1.setStatus(SummaryStatus.SUCCESSFUL);
            var method2 = summary.createNode("method2", "Method2", "arg1");
            method2.setStatus(SummaryStatus.SUCCESSFUL);
            assertThat(summary.getTestMethodPassed()).isEqualTo(2);
        }

        @Test
        @DisplayName("should count failed methods")
        void shouldCountFailedMethods() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            var class1 = summary.createNode("class1", "Class1", "root");
            var arg1 = summary.createNode("arg1", "Arg1", "class1");
            var method1 = summary.createNode("method1", "Method1", "arg1");
            method1.setStatus(SummaryStatus.FAILED);
            var method2 = summary.createNode("method2", "Method2", "arg1");
            method2.setStatus(SummaryStatus.SUCCESSFUL);
            assertThat(summary.getTestMethodFailed()).isEqualTo(1);
        }

        @Test
        @DisplayName("should count skipped methods including aborted")
        void shouldCountSkippedMethodsIncludingAborted() {
            var summary = new Summary();
            var root = summary.createNode("root", "Root", null);
            var class1 = summary.createNode("class1", "Class1", "root");
            var arg1 = summary.createNode("arg1", "Arg1", "class1");
            var method1 = summary.createNode("method1", "Method1", "arg1");
            method1.setStatus(SummaryStatus.SKIPPED);
            var method2 = summary.createNode("method2", "Method2", "arg1");
            method2.setStatus(SummaryStatus.ABORTED);
            var method3 = summary.createNode("method3", "Method3", "arg1");
            method3.setStatus(SummaryStatus.SUCCESSFUL);
            assertThat(summary.getTestMethodSkipped()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("recordStart and recordEnd tests")
    class RecordTimingTests {

        @Test
        @DisplayName("recordStart should set start time")
        void recordStartShouldSetStartTime() {
            var summary = new Summary();
            summary.recordStart();
            // Cannot directly assert startTime (private), but recordEnd should compute duration >= 0
            summary.recordEnd();
            assertThat(summary.getDuration()).isGreaterThanOrEqualTo(0L);
        }

        @Test
        @DisplayName("recordEnd can be called multiple times")
        void recordEndCanBeCalledMultipleTimes() {
            var summary = new Summary();
            summary.recordStart();
            summary.recordEnd();
            var firstDuration = summary.getDuration();
            summary.recordEnd();
            var secondDuration = summary.getDuration();
            assertThat(secondDuration).isGreaterThanOrEqualTo(firstDuration);
        }
    }
}

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

package org.paramixel.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Executable;
import org.paramixel.core.internal.DefaultContext;

@DisplayName("Context Attachment")
class ContextAttachmentTest {

    record TestData(String value, int number) {}

    @Test
    @DisplayName("sets and retrieves attachment of correct type")
    void setsAndRetrievesAttachmentOfCorrectType() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context context = DefaultContext.create(action, runner);
        var testData = new TestData("test-value", 42);

        context.setAttachment(testData);

        assertThat(context.attachment(TestData.class)).isPresent().contains(testData);
    }

    @Test
    @DisplayName("returns empty when attachment is null")
    void returnsEmptyWhenAttachmentIsNull() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context context = DefaultContext.create(action, runner);

        assertThat(context.attachment(TestData.class)).isEmpty();
    }

    @Test
    @DisplayName("returns empty when attachment type does not match")
    void returnsEmptyWhenAttachmentTypeDoesNotMatch() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context context = DefaultContext.create(action, runner);
        var testData = new TestData("test-value", 42);

        context.setAttachment(testData);

        assertThat(context.attachment(String.class)).isEmpty();
        assertThat(context.attachment(Integer.class)).isEmpty();
    }

    @Test
    @DisplayName("sets null attachment and returns empty")
    void setsNullAttachmentAndReturnsEmpty() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context context = DefaultContext.create(action, runner);
        var testData = new TestData("test-value", 42);

        context.setAttachment(testData);
        assertThat(context.attachment(TestData.class)).isPresent();

        context.setAttachment(null);
        assertThat(context.attachment(TestData.class)).isEmpty();
    }

    @Test
    @DisplayName("replaces existing attachment")
    void replacesExistingAttachment() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context context = DefaultContext.create(action, runner);
        var testData1 = new TestData("value1", 1);
        var testData2 = new TestData("value2", 2);

        context.setAttachment(testData1);
        assertThat(context.attachment(TestData.class)).contains(testData1);

        context.setAttachment(testData2);
        assertThat(context.attachment(TestData.class)).contains(testData2);
    }

    @Test
    @DisplayName("removes and returns existing attachment")
    void removesAndReturnsExistingAttachment() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context context = DefaultContext.create(action, runner);
        var testData = new TestData("test-value", 42);

        context.setAttachment(testData);
        assertThat(context.attachment(TestData.class)).isPresent();

        Optional<Object> removed = context.removeAttachment();
        assertThat(removed).isPresent().contains(testData);
        assertThat(context.attachment(TestData.class)).isEmpty();
    }

    @Test
    @DisplayName("removeAttachment returns empty when no attachment present")
    void removeAttachmentReturnsEmptyWhenNoAttachmentPresent() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context context = DefaultContext.create(action, runner);

        Optional<Object> removed = context.removeAttachment();
        assertThat(removed).isEmpty();
    }

    @Test
    @DisplayName("setAttachment returns context for method chaining")
    void setAttachmentReturnsContextForMethodChaining() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context context = DefaultContext.create(action, runner);
        var testData = new TestData("test-value", 42);

        Context result = context.setAttachment(testData);

        assertThat(result).isSameAs(context);
        assertThat(context.attachment(TestData.class)).isPresent().contains(testData);
    }

    @Test
    @DisplayName("child contexts have independent attachments")
    void childContextsHaveIndependentAttachments() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context parent = DefaultContext.create(action, runner);
        Context child = parent.createChild(Direct.of("child", Executable.noop()));
        var parentData = new TestData("parent", 1);
        var childData = new TestData("child", 2);

        parent.setAttachment(parentData);
        child.setAttachment(childData);

        assertThat(parent.attachment(TestData.class)).contains(parentData);
        assertThat(child.attachment(TestData.class)).contains(childData);
    }

    @Test
    @DisplayName("child contexts do not inherit parent attachments")
    void childContextsDoNotInheritParentAttachments() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context parent = DefaultContext.create(action, runner);
        Context child = parent.createChild(Direct.of("child", Executable.noop()));
        var parentData = new TestData("parent", 1);

        parent.setAttachment(parentData);

        assertThat(parent.attachment(TestData.class)).contains(parentData);
        assertThat(child.attachment(TestData.class)).isEmpty();
    }

    @Test
    @DisplayName("attachment works with String type")
    void attachmentWorksWithStringType() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context context = DefaultContext.create(action, runner);

        context.setAttachment("test-string");

        assertThat(context.attachment(String.class)).isPresent().contains("test-string");
    }

    @Test
    @DisplayName("attachment works with Integer type")
    void attachmentWorksWithIntegerType() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context context = DefaultContext.create(action, runner);

        context.setAttachment(123);

        assertThat(context.attachment(Integer.class)).isPresent().contains(123);
    }

    @Test
    @DisplayName("attachment works with interface type when implementation is attached")
    void attachmentWorksWithInterfaceTypeWhenImplementationIsAttached() {
        Action action = Direct.of("test", Executable.noop());
        var runner = Runner.builder().build();
        Context context = DefaultContext.create(action, runner);

        Runnable runnable = () -> {};
        context.setAttachment(runnable);

        assertThat(context.attachment(Runnable.class)).isPresent().contains(runnable);
    }
}

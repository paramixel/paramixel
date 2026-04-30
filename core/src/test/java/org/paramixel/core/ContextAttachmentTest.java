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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.internal.DefaultContext;

@DisplayName("Context Attachment")
class ContextAttachmentTest {

    record TestData(String value, int number) {}

    private DefaultContext createContext() {
        return new DefaultContext(
                Configuration.defaultProperties(), Listener.defaultListener(), directExecutorService());
    }

    private static ExecutorService directExecutorService() {
        return new AbstractExecutorService() {
            private boolean shutdown;

            @Override
            public void shutdown() {
                shutdown = true;
            }

            @Override
            public List<Runnable> shutdownNow() {
                shutdown = true;
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return shutdown;
            }

            @Override
            public boolean isTerminated() {
                return shutdown;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return shutdown;
            }

            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }

    @Test
    @DisplayName("sets and retrieves attachment of correct type")
    void setsAndRetrievesGetAttachmentOfCorrectType() {
        Context context = createContext();
        var testData = new TestData("test-value", 42);

        context.setAttachment(testData);

        assertThat(context.getAttachment().flatMap(a -> a.to(TestData.class)))
                .isPresent()
                .contains(testData);
    }

    @Test
    @DisplayName("returns empty when attachment is null")
    void returnsEmptyWhenGetAttachmentIsNull() {
        Context context = createContext();

        assertThat(context.getAttachment().flatMap(a -> a.to(TestData.class))).isEmpty();
    }

    @Test
    @DisplayName("returns empty when attachment type does not match")
    void returnsEmptyWhenGetAttachmentTypeDoesNotMatch() {
        Context context = createContext();
        var testData = new TestData("test-value", 42);

        context.setAttachment(testData);

        assertThatThrownBy(() -> context.getAttachment().flatMap(a -> a.to(String.class)))
                .isInstanceOf(ClassCastException.class);
        assertThatThrownBy(() -> context.getAttachment().flatMap(a -> a.to(Integer.class)))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    @DisplayName("sets null attachment and returns empty")
    void setsNullGetAttachmentAndReturnsEmpty() {
        Context context = createContext();
        var testData = new TestData("test-value", 42);

        context.setAttachment(testData);
        assertThat(context.getAttachment().flatMap(a -> a.to(TestData.class))).isPresent();

        context.setAttachment(null);
        assertThat(context.getAttachment().flatMap(a -> a.to(TestData.class))).isEmpty();
    }

    @Test
    @DisplayName("replaces existing attachment")
    void replacesExistingGetAttachment() {
        Context context = createContext();
        var testData1 = new TestData("value1", 1);
        var testData2 = new TestData("value2", 2);

        context.setAttachment(testData1);
        assertThat(context.getAttachment().flatMap(a -> a.to(TestData.class))).contains(testData1);

        context.setAttachment(testData2);
        assertThat(context.getAttachment().flatMap(a -> a.to(TestData.class))).contains(testData2);
    }

    @Test
    @DisplayName("removes and returns existing attachment")
    void removesAndReturnsExistingGetAttachment() {
        Context context = createContext();
        var testData = new TestData("test-value", 42);

        context.setAttachment(testData);
        assertThat(context.getAttachment().flatMap(a -> a.to(TestData.class))).isPresent();

        Optional<Attachment> removed = context.removeAttachment();
        assertThat(removed).isPresent();
        assertThat(removed.flatMap(a -> a.to(TestData.class))).contains(testData);
        assertThat(context.getAttachment().flatMap(a -> a.to(TestData.class))).isEmpty();
    }

    @Test
    @DisplayName("removeAttachment returns empty when no attachment present")
    void removeAttachmentReturnsEmptyWhenNoGetAttachmentPresent() {
        Context context = createContext();

        Optional<Attachment> removed = context.removeAttachment();
        assertThat(removed).isEmpty();
    }

    @Test
    @DisplayName("setAttachment returns context for method chaining")
    void setGetAttachmentReturnsContextForMethodChaining() {
        Context context = createContext();
        var testData = new TestData("test-value", 42);

        Context result = context.setAttachment(testData);

        assertThat(result).isSameAs(context);
        assertThat(context.getAttachment().flatMap(a -> a.to(TestData.class)))
                .isPresent()
                .contains(testData);
    }

    @Test
    @DisplayName("child contexts have independent attachments")
    void childContextsHaveIndependentAttachments() {
        DefaultContext parent = createContext();
        DefaultContext child = new DefaultContext(parent);
        var parentData = new TestData("parent", 1);
        var childData = new TestData("child", 2);

        parent.setAttachment(parentData);
        child.setAttachment(childData);

        assertThat(parent.getAttachment().flatMap(a -> a.to(TestData.class))).contains(parentData);
        assertThat(child.getAttachment().flatMap(a -> a.to(TestData.class))).contains(childData);
    }

    @Test
    @DisplayName("child contexts do not inherit parent attachments")
    void childContextsDoNotInheritGetParentAttachments() {
        DefaultContext parent = createContext();
        DefaultContext child = new DefaultContext(parent);
        var parentData = new TestData("parent", 1);

        parent.setAttachment(parentData);

        assertThat(parent.getAttachment().flatMap(a -> a.to(TestData.class))).contains(parentData);
        assertThat(child.getAttachment().flatMap(a -> a.to(TestData.class))).isEmpty();
    }

    @Test
    @DisplayName("attachment works with String type")
    void getAttachmentWorksWithStringType() {
        Context context = createContext();

        context.setAttachment("test-string");

        assertThat(context.getAttachment().flatMap(a -> a.to(String.class)))
                .isPresent()
                .contains("test-string");
    }

    @Test
    @DisplayName("attachment works with Integer type")
    void getAttachmentWorksWithIntegerType() {
        Context context = createContext();

        context.setAttachment(123);

        assertThat(context.getAttachment().flatMap(a -> a.to(Integer.class)))
                .isPresent()
                .contains(123);
    }

    @Test
    @DisplayName("attachment works with interface type when implementation is attached")
    void getAttachmentWorksWithInterfaceTypeWhenImplementationIsAttached() {
        Context context = createContext();

        Runnable runnable = () -> {};
        context.setAttachment(runnable);

        assertThat(context.getAttachment().flatMap(a -> a.to(Runnable.class)))
                .isPresent()
                .contains(runnable);
    }

    @Test
    @DisplayName("child context can access parent attachment using parent() chain (current pattern)")
    void childContextCanAccessParentGetAttachmentUsingGetParentChain() {
        DefaultContext parent = createContext();
        DefaultContext child = new DefaultContext(parent);
        var parentData = new TestData("parent", 1);

        parent.setAttachment(parentData);

        assertThat(child.getAttachment().flatMap(a -> a.to(TestData.class))).isEmpty();

        Optional<TestData> fromParent =
                child.getParent().orElseThrow().getAttachment().flatMap(a -> a.to(TestData.class));

        assertThat(fromParent).isPresent().contains(parentData);
    }

    @Test
    @DisplayName("grandchild context can access grandparent attachment using parent() chain (current pattern)")
    void grandchildContextCanAccessGrandparentGetAttachmentUsingGetParentChain() {
        DefaultContext grandparent = createContext();
        DefaultContext parent = new DefaultContext(grandparent);
        DefaultContext grandchild = new DefaultContext(parent);
        var grandparentData = new TestData("grandparent", 1);

        grandparent.setAttachment(grandparentData);

        assertThat(grandchild.getAttachment().flatMap(a -> a.to(TestData.class)))
                .isEmpty();
        assertThat(grandchild.getParent().orElseThrow().getAttachment().flatMap(a -> a.to(TestData.class)))
                .isEmpty();

        Optional<TestData> fromGrandparent = grandchild
                .getParent()
                .flatMap(Context::getParent)
                .orElseThrow()
                .getAttachment()
                .flatMap(a -> a.to(TestData.class));

        assertThat(fromGrandparent).isPresent().contains(grandparentData);
    }

    @Test
    @DisplayName("findAttachment with level 0 returns self's attachment")
    void findAttachmentWithLevel0ReturnsSelfsGetAttachment() {
        Context context = createContext();
        var testData = new TestData("test-value", 42);

        context.setAttachment(testData);

        assertThat(context.findAttachment(0).flatMap(a -> a.to(TestData.class)))
                .isPresent()
                .contains(testData);
    }

    @Test
    @DisplayName("findAttachment with level 1 returns parent's attachment")
    void findAttachmentWithLevel1ReturnsParentsGetAttachment() {
        DefaultContext parent = createContext();
        DefaultContext child = new DefaultContext(parent);
        var parentData = new TestData("parent", 1);

        parent.setAttachment(parentData);

        assertThat(child.findAttachment(1).flatMap(a -> a.to(TestData.class)))
                .isPresent()
                .contains(parentData);
    }

    @Test
    @DisplayName("findAttachment with level 2 returns grandparent's attachment")
    void findAttachmentWithLevel2ReturnsGrandparentsGetAttachment() {
        DefaultContext grandparent = createContext();
        DefaultContext parent = new DefaultContext(grandparent);
        DefaultContext grandchild = new DefaultContext(parent);
        var grandparentData = new TestData("grandparent", 1);

        grandparent.setAttachment(grandparentData);

        assertThat(grandchild.findAttachment(2).flatMap(a -> a.to(TestData.class)))
                .isPresent()
                .contains(grandparentData);
    }

    @Test
    @DisplayName("findAttachment throws NoSuchElementException when ancestor at given level doesn't exist")
    void findAttachmentThrowsNoSuchElementExceptionWhenAncestorAtGivenLevelDoesntExist() {
        DefaultContext root = createContext();
        DefaultContext child = new DefaultContext(root);

        assertThatThrownBy(() -> child.findAttachment(2))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Context ancestor not found at level 2");

        assertThatThrownBy(() -> root.findAttachment(1))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Context ancestor not found at level 1");
    }

    @Test
    @DisplayName("findAttachment returns Attachment with correct type")
    void findAttachmentReturnsAttachmentWithCorrectType() {
        DefaultContext parent = createContext();
        DefaultContext child = new DefaultContext(parent);
        var parentData = new TestData("parent", 1);

        parent.setAttachment(parentData);

        assertThat(child.findAttachment(1)).isPresent().map(Attachment::getType).contains(TestData.class);
    }

    @Test
    @DisplayName("findAttachment throws IllegalArgumentException for negative level")
    void findGetAttachmentThrowsIllegalArgumentExceptionForNegativeLevel() {
        Context context = createContext();

        assertThatThrownBy(() -> context.findAttachment(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("level must not be negative");
    }

    @Test
    @DisplayName("findAttachment works with interface type when implementation is attached")
    void findGetAttachmentWorksWithInterfaceTypeWhenImplementationIsAttached() {
        DefaultContext parent = createContext();
        DefaultContext child = new DefaultContext(parent);

        Runnable runnable = () -> {};
        parent.setAttachment(runnable);

        assertThat(child.findAttachment(1).flatMap(a -> a.to(Runnable.class)))
                .isPresent()
                .contains(runnable);
    }

    @Test
    @DisplayName("findAttachment returns empty when ancestor context exists but has no attachment")
    void findAttachmentReturnsEmptyWhenAncestorContextExistsButHasNoAttachment() {
        DefaultContext parent = createContext();
        DefaultContext child = new DefaultContext(parent);

        assertThat(child.findAttachment(1)).isEmpty();
    }

    @Test
    @DisplayName("findContext with level 0 returns Optional containing self")
    void findContextWithLevel0ReturnsOptionalContainingSelf() {
        Context context = createContext();

        assertThat(context.findContext(0)).isPresent().contains(context);
    }

    @Test
    @DisplayName("findContext with level 1 returns Optional containing parent")
    void findContextWithLevel1ReturnsOptionalContainingParent() {
        DefaultContext parent = createContext();
        DefaultContext child = new DefaultContext(parent);

        assertThat(child.findContext(1)).isPresent().contains(parent);
    }

    @Test
    @DisplayName("findContext with level 2 returns Optional containing grandparent")
    void findContextWithLevel2ReturnsOptionalContainingGrandparent() {
        DefaultContext grandparent = createContext();
        DefaultContext parent = new DefaultContext(grandparent);
        DefaultContext grandchild = new DefaultContext(parent);

        assertThat(grandchild.findContext(2)).isPresent().contains(grandparent);
    }

    @Test
    @DisplayName("findContext throws NoSuchElementException when ancestor at given level doesn't exist")
    void findContextThrowsNoSuchElementExceptionWhenAncestorAtGivenLevelDoesntExist() {
        DefaultContext root = createContext();
        DefaultContext child = new DefaultContext(root);

        assertThatThrownBy(() -> child.findContext(2))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Context ancestor not found at level 2");

        assertThatThrownBy(() -> root.findContext(1))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Context ancestor not found at level 1");
    }

    @Test
    @DisplayName("findContext throws IllegalArgumentException for negative level")
    void findContextThrowsIllegalArgumentExceptionForNegativeLevel() {
        Context context = createContext();

        assertThatThrownBy(() -> context.findContext(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("level must not be negative");
    }

    @Test
    @DisplayName("Attachment.getType returns correct type for non-null attachment")
    void attachmentGetTypeReturnsCorrectTypeForNonNullAttachment() {
        Context context = createContext();
        var testData = new TestData("test-value", 42);

        context.setAttachment(testData);

        assertThat(context.getAttachment()).isPresent().map(Attachment::getType).contains(TestData.class);
    }

    @Test
    @DisplayName("Attachment is empty for null attachment")
    void attachmentIsEmptyForNullAttachment() {
        Context context = createContext();

        context.setAttachment(null);

        assertThat(context.getAttachment()).isEmpty();
    }

    @Test
    @DisplayName("removeAttachment returns Attachment with correct type")
    void removeAttachmentReturnsAttachmentWithCorrectType() {
        Context context = createContext();
        var testData = new TestData("test-value", 42);

        context.setAttachment(testData);

        Optional<Attachment> removed = context.removeAttachment();
        assertThat(removed).isPresent().map(Attachment::getType).contains(TestData.class);
    }

    @Test
    @DisplayName("removeAttachment with wrong type throws ClassCastException and removes attachment")
    void removeAttachmentWithWrongTypeThrowsClassCastExceptionAndRemovesAttachment() {
        Context context = createContext();
        var testData = new TestData("test-value", 42);

        context.setAttachment(testData);

        Optional<Attachment> removed = context.removeAttachment();
        assertThat(removed).isPresent();
        assertThatThrownBy(() -> removed.flatMap(a -> a.to(String.class))).isInstanceOf(ClassCastException.class);
        assertThat(context.getAttachment()).isEmpty();
    }
}

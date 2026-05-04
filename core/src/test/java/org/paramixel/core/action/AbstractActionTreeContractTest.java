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

package org.paramixel.core.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.spi.DefaultResult;
import org.paramixel.core.spi.DefaultStatus;

@DisplayName("AbstractAction tree contract")
class AbstractActionTreeContractTest {

    @Test
    @DisplayName("rejects null children")
    void rejectsNullChildren() {
        CompositeAction parent = CompositeAction.of("parent", List.of(TestLeafAction.of("child")));

        assertThatThrownBy(() -> parent.addChild(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects adding itself as a child")
    void rejectsAddingItselfAsAChild() {
        CompositeAction action = CompositeAction.of("self", List.of(Direct.of("child", context -> {})));

        assertThatThrownBy(() -> action.addChild(action)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects adding a child that already has a parent")
    void rejectsAddingChildThatAlreadyHasAParent() {
        TestLeafAction child = TestLeafAction.of("child");
        CompositeAction firstParent = CompositeAction.of("firstParent", List.of(child));
        CompositeAction secondParent = CompositeAction.of("secondParent", List.of(Direct.of("other", context -> {})));

        assertThat(firstParent.getChildren()).containsExactly(child);
        assertThatThrownBy(() -> secondParent.addChild(child)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("validateChildren returns an unmodifiable list and assigns parents")
    void validateChildrenReturnsAnUnmodifiableListAndAssignsParents() {
        TestLeafAction first = TestLeafAction.of("first");
        TestLeafAction second = TestLeafAction.of("second");
        CompositeAction parent = CompositeAction.of("parent", List.of(first, second));

        assertThat(parent.getChildren()).containsExactly(first, second);
        assertThat(first.getParent()).contains(parent);
        assertThat(second.getParent()).contains(parent);
        assertThatThrownBy(() -> parent.getChildren().add(TestLeafAction.of("third")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("accepts direct Action implementations as children")
    void acceptsDirectActionImplementationsAsChildren() {
        DirectLeafAction child = DirectLeafAction.of("child-id", "child");
        CompositeAction parent = CompositeAction.of("parent", List.of(child));

        assertThat(parent.getChildren()).containsExactly(child);
        assertThat(child.getParent()).contains(parent);
    }

    @Test
    @DisplayName("rejects null parents")
    void rejectsNullParents() {
        TestLeafAction child = TestLeafAction.of("child");

        assertThatThrownBy(() -> child.setParent(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects setting itself as its own parent")
    void rejectsSettingItselfAsItsOwnParent() {
        TestLeafAction action = TestLeafAction.of("self");

        assertThatThrownBy(() -> action.setParent(action)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects setting a second parent")
    void rejectsSettingASecondParent() {
        TestLeafAction child = TestLeafAction.of("child");
        TestLeafAction firstParent = TestLeafAction.of("firstParent");
        TestLeafAction secondParent = TestLeafAction.of("secondParent");

        child.setParent(firstParent);

        assertThatThrownBy(() -> child.setParent(secondParent)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("concurrent setParent assigns exactly one parent")
    void concurrentSetParentAssignsExactlyOneParent() throws Exception {
        TestLeafAction child = TestLeafAction.of("child");
        int threadCount = 8;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<Action> winner = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            TestLeafAction parentCandidate = TestLeafAction.of("parent-" + i);
            new Thread(() -> {
                        try {
                            startLatch.await();
                            child.setParent(parentCandidate);
                            successCount.incrementAndGet();
                            winner.set(parentCandidate);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (IllegalStateException e) {
                            failureCount.incrementAndGet();
                        } finally {
                            doneLatch.countDown();
                        }
                    })
                    .start();
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);
        assertThat(child.getParent()).contains(winner.get());
    }

    private static final class DirectLeafAction implements Action {

        private final String id;
        private final String name;
        private final AtomicReference<Action> parent = new AtomicReference<>();

        private DirectLeafAction(String id, String name) {
            this.id = id;
            this.name = name;
        }

        static DirectLeafAction of(String id, String name) {
            return new DirectLeafAction(id, name);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Optional<Action> getParent() {
            return Optional.ofNullable(parent.get());
        }

        @Override
        public void setParent(Action parent) {
            Objects.requireNonNull(parent, "parent must not be null");
            if (parent == this) {
                throw new IllegalArgumentException("action must not be its own parent");
            }
            if (!this.parent.compareAndSet(null, parent)) {
                throw new IllegalStateException("child already has a parent");
            }
        }

        @Override
        public List<Action> getChildren() {
            return List.of();
        }

        @Override
        public void addChild(Action child) {
            throw new UnsupportedOperationException("leaf action");
        }

        @Override
        public Result execute(Context context) {
            DefaultResult result = new DefaultResult(this);
            result.setStatus(DefaultStatus.PASS);
            return result;
        }

        @Override
        public Result skip(Context context) {
            DefaultResult result = new DefaultResult(this);
            result.setStatus(DefaultStatus.SKIP);
            result.setElapsedTime(Duration.ZERO);
            context.getListener().skipAction(result);
            return result;
        }
    }

    private static final class CompositeAction extends BranchAction {

        private CompositeAction(String name, List<Action> children) {
            super(children);
            this.name = validateName(name);
        }

        static CompositeAction of(String name, List<Action> children) {
            CompositeAction instance = new CompositeAction(name, children);
            instance.initialize();
            return instance;
        }

        @Override
        public Result execute(Context context) {
            DefaultResult result = new DefaultResult(this);
            context.getListener().beforeAction(result);
            result.setStatus(DefaultStatus.PASS);
            result.setElapsedTime(Duration.ZERO);
            context.getListener().afterAction(result);
            return result;
        }
    }

    private static final class TestLeafAction extends org.paramixel.core.action.LeafAction {

        private TestLeafAction(String name) {
            this.name = validateName(name);
        }

        private static TestLeafAction of(String name) {
            TestLeafAction instance = new TestLeafAction(name);
            instance.initialize();
            return instance;
        }

        @Override
        public Result execute(Context context) {
            DefaultResult result = new DefaultResult(this);
            context.getListener().beforeAction(result);
            result.setStatus(DefaultStatus.PASS);
            result.setElapsedTime(Duration.ZERO);
            context.getListener().afterAction(result);
            return result;
        }
    }
}

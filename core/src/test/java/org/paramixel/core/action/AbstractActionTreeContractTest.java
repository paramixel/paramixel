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

@DisplayName("AbstractAction tree contract")
class AbstractActionTreeContractTest {

    @Test
    @DisplayName("rejects null children")
    void rejectsNullChildren() {
        CompositeAction parent = CompositeAction.of("parent", List.of(LeafAction.of("child")));

        assertThatThrownBy(() -> parent.addChild(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects adding itself as a child")
    void rejectsAddingItselfAsAChild() {
        LeafAction action = LeafAction.of("self");

        assertThatThrownBy(() -> action.addChild(action)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects adding a child that already has a parent")
    void rejectsAddingChildThatAlreadyHasAParent() {
        LeafAction child = LeafAction.of("child");
        CompositeAction firstParent = CompositeAction.of("firstParent", List.of(child));
        LeafAction secondParent = LeafAction.of("secondParent");

        assertThat(firstParent.getChildren()).containsExactly(child);
        assertThatThrownBy(() -> secondParent.addChild(child)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("validateChildren returns an unmodifiable list and assigns parents")
    void validateChildrenReturnsAnUnmodifiableListAndAssignsParents() {
        LeafAction first = LeafAction.of("first");
        LeafAction second = LeafAction.of("second");
        CompositeAction parent = CompositeAction.of("parent", List.of(first, second));

        assertThat(parent.getChildren()).containsExactly(first, second);
        assertThat(first.getParent()).contains(parent);
        assertThat(second.getParent()).contains(parent);
        assertThatThrownBy(() -> parent.getChildren().add(LeafAction.of("third")))
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
        LeafAction child = LeafAction.of("child");

        assertThatThrownBy(() -> child.setParent(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects setting itself as its own parent")
    void rejectsSettingItselfAsItsOwnParent() {
        LeafAction action = LeafAction.of("self");

        assertThatThrownBy(() -> action.setParent(action)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects setting a second parent")
    void rejectsSettingASecondParent() {
        LeafAction child = LeafAction.of("child");
        LeafAction firstParent = LeafAction.of("firstParent");
        LeafAction secondParent = LeafAction.of("secondParent");

        child.setParent(firstParent);

        assertThatThrownBy(() -> child.setParent(secondParent)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("concurrent setParent assigns exactly one parent")
    void concurrentSetParentAssignsExactlyOneParent() throws Exception {
        LeafAction child = LeafAction.of("child");
        int threadCount = 8;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<Action> winner = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            LeafAction parentCandidate = LeafAction.of("parent-" + i);
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
        public org.paramixel.core.Result getResult() {
            return org.paramixel.core.Result.staged();
        }

        @Override
        public void execute(Context context) {}

        @Override
        public void skip(Context context) {}

        @Override
        public void setResult(org.paramixel.core.Result result) {}
    }

    private static final class CompositeAction extends AbstractAction {

        private final List<Action> children;

        private CompositeAction(String name, List<Action> children) {
            super();
            this.name = validateName(name);
            this.children = validateChildren(children);
        }

        static CompositeAction of(String name, List<Action> children) {
            CompositeAction instance = new CompositeAction(name, children);
            instance.initialize();
            return instance;
        }

        @Override
        public List<Action> getChildren() {
            return children;
        }

        @Override
        public void execute(Context context) {}
    }

    private static final class LeafAction extends AbstractAction {

        private LeafAction(String name) {
            super();
            this.name = validateName(name);
        }

        private static LeafAction of(String name) {
            LeafAction instance = new LeafAction(name);
            instance.initialize();
            return instance;
        }

        @Override
        public void execute(Context context) {}
    }
}

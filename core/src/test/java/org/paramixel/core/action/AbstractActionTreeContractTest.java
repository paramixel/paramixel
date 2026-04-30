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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Context;

@DisplayName("AbstractAction tree contract")
class AbstractActionTreeContractTest {

    @Test
    @DisplayName("rejects null children")
    void rejectsNullChildren() {
        CompositeAction parent = new CompositeAction("parent", List.of(LeafAction.of("child")));

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
        CompositeAction firstParent = new CompositeAction("firstParent", List.of(child));
        LeafAction secondParent = LeafAction.of("secondParent");

        assertThat(firstParent.getChildren()).containsExactly(child);
        assertThatThrownBy(() -> secondParent.addChild(child)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("validateChildren returns an unmodifiable list and assigns parents")
    void validateChildrenReturnsAnUnmodifiableListAndAssignsParents() {
        LeafAction first = LeafAction.of("first");
        LeafAction second = LeafAction.of("second");
        CompositeAction parent = new CompositeAction("parent", List.of(first, second));

        assertThat(parent.getChildren()).containsExactly(first, second);
        assertThat(first.getParent()).contains(parent);
        assertThat(second.getParent()).contains(parent);
        assertThatThrownBy(() -> parent.getChildren().add(LeafAction.of("third")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static final class CompositeAction extends AbstractAction {

        private final List<Action> children;

        private CompositeAction(String name, List<Action> children) {
            super(name);
            this.children = validateChildren(children);
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
            super(name);
        }

        private static LeafAction of(String name) {
            return new LeafAction(name);
        }

        @Override
        public void execute(Context context) {}
    }
}

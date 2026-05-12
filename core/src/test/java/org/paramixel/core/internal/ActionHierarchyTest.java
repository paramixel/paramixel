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

package org.paramixel.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.CompositeAction;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;

@DisplayName("ActionHierarchy")
class ActionHierarchyTest {

    @Test
    @DisplayName("install rejects null root")
    void installRejectsNullRoot() {
        assertThatThrownBy(() -> ActionHierarchy.install(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("root must not be null");
    }

    @Test
    @DisplayName("pathOf rejects null action")
    void pathOfRejectsNullAction() {
        assertThatThrownBy(() -> ActionHierarchy.pathOf(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action must not be null");
    }

    @Test
    @DisplayName("install with leaf action returns single-element path")
    void installWithLeafActionReturnsSingleElementPath() {
        Action leaf = Noop.of("leaf");

        try (ActionHierarchy.Scope ignored = ActionHierarchy.install(leaf)) {
            Optional<List<Action>> path = ActionHierarchy.pathOf(leaf);
            assertThat(path).isPresent();
            assertThat(path.get()).containsExactly(leaf);
        }
    }

    @Test
    @DisplayName("install with composite action returns root path")
    void installWithCompositeActionReturnsRootPath() {
        Action child = Noop.of("child");
        Action root = Container.builder("root").child(child).build();

        try (ActionHierarchy.Scope ignored = ActionHierarchy.install(root)) {
            Optional<List<Action>> path = ActionHierarchy.pathOf(root);
            assertThat(path).isPresent();
            assertThat(path.get()).containsExactly(root);
        }
    }

    @Test
    @DisplayName("install with composite action returns child paths")
    void installWithCompositeActionReturnsChildPaths() {
        Action child1 = Noop.of("child1");
        Action child2 = Noop.of("child2");
        Action root = Container.builder("root").child(child1).child(child2).build();

        try (ActionHierarchy.Scope ignored = ActionHierarchy.install(root)) {
            Optional<List<Action>> rootPath = ActionHierarchy.pathOf(root);
            assertThat(rootPath).isPresent();
            assertThat(rootPath.get()).containsExactly(root);

            Optional<List<Action>> child1Path = ActionHierarchy.pathOf(child1);
            assertThat(child1Path).isPresent();
            assertThat(child1Path.get()).containsExactly(root, child1);

            Optional<List<Action>> child2Path = ActionHierarchy.pathOf(child2);
            assertThat(child2Path).isPresent();
            assertThat(child2Path.get()).containsExactly(root, child2);
        }
    }

    @Test
    @DisplayName("install with nested composites returns deep paths")
    void installWithNestedCompositesReturnsDeepPaths() {
        Action leaf = Noop.of("leaf");
        Action inner = Container.builder("inner").child(leaf).build();
        Action outer = Container.builder("outer").child(inner).build();

        try (ActionHierarchy.Scope ignored = ActionHierarchy.install(outer)) {
            Optional<List<Action>> leafPath = ActionHierarchy.pathOf(leaf);
            assertThat(leafPath).isPresent();
            assertThat(leafPath.get()).containsExactly(outer, inner, leaf);
        }
    }

    @Test
    @DisplayName("pathOf returns empty for unknown action")
    void pathOfReturnsEmptyForUnknownAction() {
        Action root = Noop.of("root");
        Action unknown = Noop.of("unknown");

        try (ActionHierarchy.Scope ignored = ActionHierarchy.install(root)) {
            assertThat(ActionHierarchy.pathOf(unknown)).isEmpty();
        }
    }

    @Test
    @DisplayName("Scope close clears hierarchy")
    void scopeCloseClearsHierarchy() {
        Action action = Noop.of("action");

        try (ActionHierarchy.Scope ignored = ActionHierarchy.install(action)) {
            assertThat(ActionHierarchy.pathOf(action)).isPresent();
        }

        assertThat(ActionHierarchy.pathOf(action)).isEmpty();
    }

    @Test
    @DisplayName("install overwrites previous index")
    void installOverwritesPreviousIndex() {
        Action first = Noop.of("first");
        Action second = Noop.of("second");

        try (ActionHierarchy.Scope ignored1 = ActionHierarchy.install(first)) {
            assertThat(ActionHierarchy.pathOf(first)).isPresent();
            assertThat(ActionHierarchy.pathOf(second)).isEmpty();
        }

        try (ActionHierarchy.Scope ignored2 = ActionHierarchy.install(second)) {
            assertThat(ActionHierarchy.pathOf(first)).isEmpty();
            assertThat(ActionHierarchy.pathOf(second)).isPresent();
        }
    }

    @Test
    @DisplayName("composite action with null child throws NPE")
    void compositeActionWithNullChildThrowsNPE() {
        Action root = new CompositeAction() {
            @Override
            public List<Action> getChildren() {
                return Arrays.asList((Action) null);
            }

            @Override
            public String getId() {
                return "null-child-composite";
            }

            @Override
            public String getName() {
                return "nullChildComposite";
            }

            @Override
            public Action.ContextMode getContextMode() {
                return Action.ContextMode.ISOLATED;
            }

            @Override
            public Result execute(Context context) {
                return Result.pass(this);
            }

            @Override
            public Result skip(Context context) {
                return Result.skip(this);
            }
        };

        assertThatThrownBy(() -> ActionHierarchy.install(root))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("null child");
    }

    @Test
    @DisplayName("putIfAbsent semantics: first path wins for same action")
    void putIfAbsentSemanticsFirstPathWins() {
        Action shared = Direct.builder("shared").execute(ctx -> {}).build();
        Action root = Container.builder("root").child(shared).child(shared).build();

        try (ActionHierarchy.Scope ignored = ActionHierarchy.install(root)) {
            Optional<List<Action>> sharedPath = ActionHierarchy.pathOf(shared);
            assertThat(sharedPath).isPresent();
            assertThat(sharedPath.get()).hasSize(2);
            assertThat(sharedPath.get().get(0)).isSameAs(root);
            assertThat(sharedPath.get().get(1)).isSameAs(shared);
        }
    }
}

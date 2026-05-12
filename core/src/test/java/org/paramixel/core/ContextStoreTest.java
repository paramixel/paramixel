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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.internal.DefaultContext;

@DisplayName("Context Store")
class ContextStoreTest {

    private static final Listener NOOP_LISTENER = new Listener() {};

    private static final AsyncScheduler SCHEDULER =
            (action, context) -> CompletableFuture.completedFuture(action.execute(context));

    private static DefaultContext context() {
        return new DefaultContext(Configuration.defaultProperties(), NOOP_LISTENER, SCHEDULER);
    }

    @Test
    @DisplayName("getParent returns direct parent")
    void getParentReturnsDirectParent() {
        var parent = context();
        Context child = parent.createChild();

        assertThat(child.getParent()).contains(parent);
    }

    @Test
    @DisplayName("getParent on root returns empty")
    void getParentOnRootReturnsEmpty() {
        var root = context();

        assertThat(root.getParent()).isEmpty();
    }

    @Test
    @DisplayName("findAncestor returns requested context")
    void findAncestorReturnsRequestedContext() {
        var root = context();
        Context child = root.createChild();
        Context grandchild = child.createChild();

        assertThat(grandchild.findAncestor(0)).contains(grandchild);
        assertThat(grandchild.findAncestor(1)).contains(child);
        assertThat(grandchild.findAncestor(2)).contains(root);
    }

    @Test
    @DisplayName("findAncestor rejects negative levels")
    void findAncestorRejectsNegativeLevels() {
        var root = context();

        assertThatThrownBy(() -> root.findAncestor(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("levelUp must be non-negative");
    }

    @Test
    @DisplayName("findAncestor returns empty when level does not exist")
    void findAncestorReturnsEmptyWhenLevelDoesNotExist() {
        var root = context();
        Context child = root.createChild();

        assertThat(child.findAncestor(2)).isEmpty();
    }

    @Test
    @DisplayName("createChild creates isolated local store")
    void createChildCreatesIsolatedLocalStore() {
        var parent = context();
        Context child = parent.createChild();
        parent.getStore().put("shared", Value.of("parent"));
        child.getStore().put("shared", Value.of("child"));

        assertThat(parent.getStore().get("shared").orElseThrow().cast(String.class))
                .isEqualTo("parent");
        assertThat(child.getStore().get("shared").orElseThrow().cast(String.class))
                .isEqualTo("child");
        assertThat(child.findAncestor(1)
                        .orElseThrow()
                        .getStore()
                        .get("shared")
                        .orElseThrow()
                        .cast(String.class))
                .isEqualTo("parent");
    }

    @Test
    @DisplayName("createChild preserves runner services")
    void createChildPreservesRunnerServices() {
        var parent = context();
        Context child = parent.createChild();

        assertThat(child.getConfiguration()).isEqualTo(parent.getConfiguration());
        assertThat(child.getListener()).isSameAs(parent.getListener());
    }

    @Test
    @DisplayName("runAsync rejects null action")
    void runAsyncRejectsNullAction() {
        var ctx = context();

        assertThatThrownBy(() -> ctx.runAsync(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action must not be null");
    }

    @Test
    void defaultContextRejectsNullListener() {
        assertThatThrownBy(() -> new DefaultContext(Configuration.defaultProperties(), null, SCHEDULER))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listener must not be null");
    }

    @Test
    @DisplayName("DefaultContext rejects null scheduler")
    void defaultContextRejectsNullScheduler() {
        assertThatThrownBy(() -> new DefaultContext(Configuration.defaultProperties(), NOOP_LISTENER, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("scheduler must not be null");
    }

    @Test
    @DisplayName("DefaultContext Map constructor rejects null listener")
    void defaultContextMapConstructorRejectsNullListener() {
        assertThatThrownBy(() -> new DefaultContext((Map<String, String>) null, null, SCHEDULER))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listener must not be null");
    }

    @Test
    @DisplayName("DefaultContext Map constructor rejects null scheduler")
    void defaultContextMapConstructorRejectsNullScheduler() {
        assertThatThrownBy(() -> new DefaultContext((Map<String, String>) null, NOOP_LISTENER, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("scheduler must not be null");
    }

    @Test
    void defaultContextWithNullConfigurationFallsBack() {
        var context = new DefaultContext((Map<String, String>) null, NOOP_LISTENER, SCHEDULER);

        assertThat(context.getConfiguration()).containsKey(Configuration.RUNNER_PARALLELISM);
    }

    @Test
    @DisplayName("DefaultContext toString returns root for root context")
    void toStringReturnsRootForRootContext() {
        var root = context();

        assertThat(root.toString()).isEqualTo("Context[root]");
    }

    @Test
    @DisplayName("DefaultContext toString returns child for child context")
    void toStringReturnsChildForChildContext() {
        var root = context();
        Context child = root.createChild();

        assertThat(child.toString()).isEqualTo("Context[child]");
    }
}

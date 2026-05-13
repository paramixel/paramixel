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
import org.paramixel.core.exception.AncestorNotFoundException;
import org.paramixel.core.internal.DefaultContext;

@DisplayName("Context Store")
class ContextStoreTest {

    private static final Listener NOOP_LISTENER = new Listener() {};

    private static final AsyncScheduler SCHEDULER =
            (action, context) -> CompletableFuture.completedFuture(action.run(context));

    private static DefaultContext context() {
        return new DefaultContext(Configuration.defaultProperties(), NOOP_LISTENER, SCHEDULER);
    }

    @Test
    @DisplayName("getParent returns direct parent")
    void getParentReturnsDirectParent() {
        var parent = context();
        Context child = parent.createChild();

        assertThat(child.getParent()).isSameAs(parent);
    }

    @Test
    @DisplayName("getParent on root throws AncestorNotFoundException")
    void getParentOnRootThrowsAncestorNotFoundException() {
        var root = context();

        assertThatThrownBy(root::getParent)
                .isInstanceOf(AncestorNotFoundException.class)
                .hasMessage("parent does not exist: this context is the root");
    }

    @Test
    @DisplayName("getAncestor with '../' returns parent")
    void getAncestorReturnsParent() {
        var root = context();
        Context child = root.createChild();

        assertThat(child.getAncestor("../")).isSameAs(root);
    }

    @Test
    @DisplayName("getAncestor with '../../' returns grandparent")
    void getAncestorReturnsGrandparent() {
        var root = context();
        Context child = root.createChild();
        Context grandchild = child.createChild();

        assertThat(grandchild.getAncestor("../../")).isSameAs(root);
    }

    @Test
    @DisplayName("getAncestor with '/' returns root")
    void getAncestorSlashReturnsRoot() {
        var root = context();
        Context child = root.createChild();
        Context grandchild = child.createChild();

        assertThat(grandchild.getAncestor("/")).isSameAs(root);
        assertThat(child.getAncestor("/")).isSameAs(root);
        assertThat(root.getAncestor("/")).isSameAs(root);
    }

    @Test
    @DisplayName("getAncestor throws AncestorNotFoundException when path traverses beyond root")
    void getAncestorThrowsWhenPathTraversesBeyondRoot() {
        var root = context();
        Context child = root.createChild();

        assertThatThrownBy(() -> child.getAncestor("../../../")).isInstanceOf(AncestorNotFoundException.class);
    }

    @Test
    @DisplayName("getAncestor rejects named segments")
    void getAncestorRejectsNamedSegments() {
        var root = context();

        assertThatThrownBy(() -> root.getAncestor("../foo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> root.getAncestor("/bar")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> root.getAncestor("baz")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getAncestor rejects '.' segments")
    void getAncestorRejectsDotSegment() {
        var root = context();

        assertThatThrownBy(() -> root.getAncestor(".")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> root.getAncestor("./")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> root.getAncestor(".././")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getAncestor rejects null path")
    void getAncestorRejectsNullPath() {
        var root = context();

        assertThatThrownBy(() -> root.getAncestor(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("path must not be null");
    }

    @Test
    @DisplayName("getAncestor rejects empty path")
    void getAncestorRejectsEmptyPath() {
        var root = context();

        assertThatThrownBy(() -> root.getAncestor(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("path must not be empty");
    }

    @Test
    @DisplayName("findParent returns direct parent")
    void findParentReturnsDirectParent() {
        var parent = context();
        Context child = parent.createChild();

        assertThat(child.findParent()).contains(parent);
    }

    @Test
    @DisplayName("findParent on root returns empty")
    void findParentOnRootReturnsEmpty() {
        var root = context();

        assertThat(root.findParent()).isEmpty();
    }

    @Test
    @DisplayName("findAncestor with '..' returns parent")
    void findAncestorDoubleDotReturnsParent() {
        var root = context();
        Context child = root.createChild();

        assertThat(child.findAncestor("..")).contains(root);
    }

    @Test
    @DisplayName("findAncestor with '../' returns parent")
    void findAncestorDoubleDotSlashReturnsParent() {
        var root = context();
        Context child = root.createChild();

        assertThat(child.findAncestor("../")).contains(root);
    }

    @Test
    @DisplayName("findAncestor with '../..' returns grandparent")
    void findAncestorTwoLevelsUpWithoutSlash() {
        var root = context();
        Context child = root.createChild();
        Context grandchild = child.createChild();

        assertThat(grandchild.findAncestor("../..")).contains(root);
    }

    @Test
    @DisplayName("findAncestor with '../../' returns grandparent")
    void findAncestorTwoLevelsUpWithSlash() {
        var root = context();
        Context child = root.createChild();
        Context grandchild = child.createChild();

        assertThat(grandchild.findAncestor("../../")).contains(root);
    }

    @Test
    @DisplayName("findAncestor trailing and non-trailing slash return same context")
    void findAncestorMixedSlashPattern() {
        var root = context();
        Context child = root.createChild();
        Context grandchild = child.createChild();

        assertThat(grandchild.findAncestor("../..")).contains(root);
        assertThat(grandchild.findAncestor("../../")).contains(root);
        assertThat(grandchild.findAncestor("../..")).isEqualTo(grandchild.findAncestor("../../"));
    }

    @Test
    @DisplayName("findAncestor with '/' returns root")
    void findAncestorSlashReturnsRoot() {
        var root = context();
        Context child = root.createChild();
        Context grandchild = child.createChild();

        assertThat(grandchild.findAncestor("/")).contains(root);
        assertThat(child.findAncestor("/")).contains(root);
        assertThat(root.findAncestor("/")).contains(root);
    }

    @Test
    @DisplayName("findAncestor returns empty when path traverses beyond root")
    void findAncestorReturnsEmptyWhenPathTraversesBeyondRoot() {
        var root = context();
        Context child = root.createChild();

        assertThat(child.findAncestor("../../../")).isEmpty();
    }

    @Test
    @DisplayName("findAncestor rejects named segments")
    void findAncestorRejectsNamedSegments() {
        var root = context();

        assertThatThrownBy(() -> root.findAncestor("../foo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> root.findAncestor("/bar")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> root.findAncestor("baz")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("findAncestor rejects '.' segments")
    void findAncestorRejectsDotSegment() {
        var root = context();

        assertThatThrownBy(() -> root.findAncestor(".")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> root.findAncestor("./")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> root.findAncestor(".././")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("findAncestor rejects null path")
    void findAncestorRejectsNullPath() {
        var root = context();

        assertThatThrownBy(() -> root.findAncestor((String) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("path must not be null");
    }

    @Test
    @DisplayName("findAncestor rejects empty path")
    void findAncestorRejectsEmptyPath() {
        var root = context();

        assertThatThrownBy(() -> root.findAncestor(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("path must not be empty");
    }

    @Test
    @DisplayName("createChild creates isolated local store")
    void createChildCreatesIsolatedLocalStore() {
        var parent = context();
        Context child = parent.createChild();
        parent.getStore().put("shared", "parent");
        child.getStore().put("shared", "child");

        assertThat(parent.getStore().get("shared", String.class).orElseThrow()).isEqualTo("parent");
        assertThat(child.getStore().get("shared", String.class).orElseThrow()).isEqualTo("child");
        assertThat(child.getAncestor("../")
                        .getStore()
                        .get("shared", String.class)
                        .orElseThrow())
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

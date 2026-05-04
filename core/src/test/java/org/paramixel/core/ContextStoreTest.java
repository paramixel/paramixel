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
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.spi.DefaultContext;

@DisplayName("Context Store")
class ContextStoreTest {

    private static final Listener NOOP_LISTENER = new Listener() {};

    private static ExecutorService executorService() {
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
            public boolean awaitTermination(final long timeout, final TimeUnit unit) {
                return shutdown;
            }

            @Override
            public void execute(final Runnable command) {
                command.run();
            }
        };
    }

    @Test
    @DisplayName("getParent returns direct parent")
    void getParentReturnsDirectParent() {
        Context parent = new DefaultContext(Configuration.defaultProperties(), NOOP_LISTENER, executorService());
        Context child = parent.createChild();

        assertThat(child.getParent()).contains(parent);
    }

    @Test
    @DisplayName("getParent on root returns empty")
    void getParentOnRootReturnsEmpty() {
        Context root = new DefaultContext(Configuration.defaultProperties(), NOOP_LISTENER, executorService());

        assertThat(root.getParent()).isEmpty();
    }

    @Test
    @DisplayName("findAncestor returns requested context")
    void findAncestorReturnsRequestedContext() {
        Context root = new DefaultContext(Configuration.defaultProperties(), NOOP_LISTENER, executorService());
        Context child = root.createChild();
        Context grandchild = child.createChild();

        assertThat(grandchild.findAncestor(0)).contains(grandchild);
        assertThat(grandchild.findAncestor(1)).contains(child);
        assertThat(grandchild.findAncestor(2)).contains(root);
    }

    @Test
    @DisplayName("findAncestor rejects negative levels")
    void findAncestorRejectsNegativeLevels() {
        Context root = new DefaultContext(Configuration.defaultProperties(), NOOP_LISTENER, executorService());

        assertThatThrownBy(() -> root.findAncestor(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("levelUp must be non-negative");
    }

    @Test
    @DisplayName("findAncestor returns empty when level does not exist")
    void findAncestorReturnsEmptyWhenLevelDoesNotExist() {
        Context root = new DefaultContext(Configuration.defaultProperties(), NOOP_LISTENER, executorService());
        Context child = root.createChild();

        assertThat(child.findAncestor(2)).isEmpty();
    }

    @Test
    @DisplayName("createChild creates isolated local store")
    void createChildCreatesIsolatedLocalStore() {
        Context parent = new DefaultContext(Configuration.defaultProperties(), NOOP_LISTENER, executorService());
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
        Context parent = new DefaultContext(Configuration.defaultProperties(), NOOP_LISTENER, executorService());
        Context child = parent.createChild();

        assertThat(child.getConfiguration()).isEqualTo(parent.getConfiguration());
        assertThat(child.getListener()).isSameAs(parent.getListener());
        assertThat(child.getExecutorService()).isSameAs(parent.getExecutorService());
    }
}

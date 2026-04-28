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

package org.paramixel.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Runner;
import org.paramixel.core.SkipException;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Sequential;
import org.paramixel.gui.internal.ExecutionNode;

class GuiExecutionListenerTest {

    @Test
    @DisplayName("should materialize the full tree before execution starts")
    void shouldMaterializeTheFullTreeBeforeExecutionStarts() {
        Action leaf = Direct.of("leaf", context -> {});
        Action branch = Sequential.of("branch", List.of(leaf));
        GuiExecutionListener listener = new GuiExecutionListener(branch, false);

        ExecutionNode rootNode = listener.rootNode();

        assertThat(rootNode.children()).hasSize(1);
        ExecutionNode branchNode = rootNode.children().get(0);
        assertThat(branchNode.displayName()).isEqualTo("branch");
        assertThat(branchNode.children()).hasSize(1);
        assertThat(branchNode.children().get(0).displayName()).isEqualTo("leaf");
        assertThat(branchNode.status()).isEqualTo(ExecutionNode.Status.WAITING);
    }

    @Test
    @DisplayName("should update statuses from the current core listener api")
    void shouldUpdateStatusesFromCurrentCoreApi() {
        Action passingLeaf = Direct.of("passing", context -> {});
        Action skippedLeaf = Direct.of("skipped", context -> {
            throw new SkipException("skip");
        });
        Action rootAction = Sequential.of("root", List.of(passingLeaf, skippedLeaf));

        GuiExecutionListener listener = new GuiExecutionListener(rootAction, false);

        Runner.builder().listener(listener).build().run(rootAction);

        ExecutionNode rootNode = listener.rootNode();
        ExecutionNode rootActionNode = rootNode.children().get(0);
        assertThat(rootActionNode.status()).isEqualTo(ExecutionNode.Status.SKIPPED);
        assertThat(rootActionNode.children())
                .extracting(ExecutionNode::status)
                .containsExactly(ExecutionNode.Status.SUCCESSFUL, ExecutionNode.Status.SKIPPED);
    }
}

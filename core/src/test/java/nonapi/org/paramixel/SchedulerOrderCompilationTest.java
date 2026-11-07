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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.DescriptorOrder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Step;

/**
 * Verifies the compilation boundary: the first scheduling or execution of any descriptor compiles
 * and freezes the whole tree it belongs to, so compiled ranks are tree-global and the structure can
 * no longer change underneath them.
 */
@DisplayName("Scheduler order compilation")
@Timeout(30)
class SchedulerOrderCompilationTest {

    @Test
    @DisplayName("scheduling a mid-tree descriptor compiles and freezes the whole tree")
    void schedulingMidTreeDescriptorCompilesAndFreezesWholeTree() {
        var scheduler = new Scheduler(1, 8);
        try {
            var root = new ConcreteDescriptor(Step.of("root", context -> {}));
            var first = new ConcreteDescriptor(root, Step.of("first", context -> {}));
            var second = new ConcreteDescriptor(root, Step.of("second", context -> {}));
            root.addChild(first);
            root.addChild(second);
            var context = newContext(root, scheduler);

            assertThat(root.orderRank()).isEqualTo(DescriptorOrder.UNCOMPILED);

            scheduler.schedule(second, ExecutionMode.RUN, context).join();

            assertThat(root.isFrozen()).isTrue();
            assertThat(first.isFrozen()).isTrue();
            assertThat(root.orderRank()).isEqualTo(2L);
            assertThat(first.orderRank()).isEqualTo(0L);
            assertThat(second.orderRank()).isEqualTo(1L);
            assertThat(first.orderTreeId()).isEqualTo(root.orderTreeId());
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("executing a descriptor that was never scheduled compiles its tree")
    void executingUnscheduledDescriptorCompilesTree() {
        var scheduler = new Scheduler(1, 8);
        try {
            var root = new ConcreteDescriptor(Step.of("root", context -> {}));
            var child = new ConcreteDescriptor(root, Step.of("child", context -> {}));
            root.addChild(child);
            var context = newContext(root, scheduler);

            scheduler.executeDescriptor(root, context, ExecutionMode.RUN);

            assertThat(root.isFrozen()).isTrue();
            assertThat(child.orderRank()).isEqualTo(0L);
            assertThat(root.orderRank()).isEqualTo(1L);
            assertThat(child.orderTreeId()).isEqualTo(root.orderTreeId());
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("structural mutation after the compilation boundary is rejected")
    void structuralMutationAfterCompilationBoundaryIsRejected() {
        var scheduler = new Scheduler(1, 8);
        try {
            var root = new ConcreteDescriptor(Step.of("root", context -> {}));
            var child = new ConcreteDescriptor(root, Step.of("child", context -> {}));
            root.addChild(child);
            var context = newContext(root, scheduler);

            scheduler.schedule(child, ExecutionMode.RUN, context).join();

            assertThatThrownBy(() -> root.addChild(new ConcreteDescriptor(Step.of("late", c -> {}))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Descriptor is frozen:");
            assertThatThrownBy(() -> root.setAfter(new ConcreteDescriptor(Step.of("late-after", c -> {}))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Descriptor is frozen:");
        } finally {
            scheduler.close();
        }
    }

    private static ConcreteContext newContext(final MutableDescriptor descriptor, final Scheduler scheduler) {
        var configuration = Configuration.of(Map.of(Configuration.ANSI, "false"));
        return new ConcreteContext(configuration, new Listener() {}, descriptor, scheduler, new InstanceHolder());
    }
}

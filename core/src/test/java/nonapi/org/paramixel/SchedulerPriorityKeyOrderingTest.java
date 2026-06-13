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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler priority key ordering")
@SuppressWarnings("removal")
class SchedulerPriorityKeyOrderingTest {

    @Test
    @DisplayName("precomputed keys preserve legacy scheduler ordering")
    void precomputedKeysPreserveLegacySchedulerOrdering() {
        var branch = Sequence.builder("branch")
                .child(Step.of("branch-step-0", context -> {}))
                .child(Parallel.builder("branch-parallel")
                        .parallelism(2)
                        .child(Step.of("parallel-left", context -> {}))
                        .child(Step.of("parallel-right", context -> {}))
                        .build())
                .child(Step.of("branch-step-2", context -> {}))
                .build();

        var rootAction = Scope.builder("root")
                .before(Step.of("before", context -> {}))
                .body(branch)
                .after(Step.of("after", context -> {}))
                .build();

        var descriptorTree = new DescriptorBuilder().discover(rootAction);

        var descriptors = collectDescriptors(descriptorTree);
        var expected = descriptors.stream()
                .sorted((left, right) -> compareLegacyPriority(priorityOfLegacy(left), priorityOfLegacy(right)))
                .toList();
        var actual = descriptors.stream()
                .sorted((left, right) -> left.schedulerPriorityKey().compareTo(right.schedulerPriorityKey()))
                .toList();

        assertThat(actual).containsExactlyElementsOf(expected);
    }

    private static List<MutableDescriptor> collectDescriptors(final MutableDescriptor root) {
        var descriptors = new ArrayList<MutableDescriptor>();
        var queue = new ArrayDeque<MutableDescriptor>();
        queue.add(root);
        while (!queue.isEmpty()) {
            var descriptor = queue.removeFirst();
            descriptors.add(descriptor);
            for (var child : descriptor.children()) {
                queue.addLast((MutableDescriptor) child);
            }
        }
        return descriptors;
    }

    private static List<Integer> priorityOfLegacy(final MutableDescriptor descriptor) {
        var path = new ArrayList<Integer>();
        var current = descriptor;
        while (current.parent().isPresent()) {
            if (current.parent().orElseThrow() instanceof MutableDescriptor parent) {
                path.add(indexInParent(parent, current));
                current = parent;
            } else {
                break;
            }
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    private static int indexInParent(final MutableDescriptor parent, final MutableDescriptor child) {
        var children = parent.children();
        for (var i = 0; i < children.size(); i++) {
            if (children.get(i) == child) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static int compareLegacyPriority(final List<Integer> left, final List<Integer> right) {
        var size = Math.min(left.size(), right.size());
        for (var i = 0; i < size; i++) {
            var comparison = Integer.compare(left.get(i), right.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(right.size(), left.size());
    }
}

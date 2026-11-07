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

package nonapi.org.paramixel.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Descriptor;
import org.paramixel.api.action.Step;

@DisplayName("DescriptorOrder")
class DescriptorOrderTest {

    private static final String STEP = "step";

    @Test
    @DisplayName("compiled rank order matches the structural path order")
    void compiledRankOrderMatchesStructuralPathOrder() {
        var root = new ConcreteDescriptor(Step.of(STEP, context -> {}));
        var before = child(root, STEP);
        root.setBefore(before);
        var body = child(root, STEP);
        root.addChild(body);
        var after = child(root, STEP);
        root.setAfter(after);

        var beforeChild = child(before, STEP);
        before.addChild(beforeChild);
        var beforeGrandChild = child(beforeChild, STEP);
        beforeChild.addChild(beforeGrandChild);

        var bodyFirst = child(body, STEP);
        body.addChild(bodyFirst);
        var bodySecond = child(body, STEP);
        body.addChild(bodySecond);
        var bodySecondChild = child(bodySecond, STEP);
        bodySecond.addChild(bodySecondChild);

        var afterChild = child(after, STEP);
        after.addChild(afterChild);

        DescriptorOrder.compile(root);
        var descriptors = descendantsOrSelf(root);

        var byPath = new ArrayList<>(descriptors);
        byPath.sort(pathOrder(descriptors));
        var byRank = new ArrayList<>(descriptors);
        byRank.sort(Comparator.comparingLong(MutableDescriptor::orderRank));

        assertThat(byRank).containsExactlyElementsOf(byPath);
        assertThat(byRank.get(byRank.size() - 1)).isSameAs(root);
        assertThat(byRank.get(0)).isSameAs(beforeGrandChild);
    }

    @Test
    @DisplayName("every rank in a tree is unique")
    void everyRankInATreeIsUnique() {
        var root = new ConcreteDescriptor(Step.of(STEP, context -> {}));
        var repeated = Step.of(STEP, context -> {});
        for (var i = 0; i < 4; i++) {
            var branch = new ConcreteDescriptor(root, repeated);
            root.addChild(branch);
            for (var j = 0; j < 3; j++) {
                branch.addChild(new ConcreteDescriptor(branch, repeated));
            }
        }

        DescriptorOrder.compile(root);

        var ranks = descendantsOrSelf(root).stream()
                .map(MutableDescriptor::orderRank)
                .toList();
        assertThat(ranks).doesNotContain(DescriptorOrder.UNCOMPILED).hasSize(17).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("subtree start is the lowest rank in the subtree")
    void subtreeStartIsLowestRankInSubtree() {
        var root = new ConcreteDescriptor(Step.of(STEP, context -> {}));
        var branch = child(root, STEP);
        root.addChild(branch);
        var nested = child(branch, STEP);
        branch.addChild(nested);
        var sibling = child(root, STEP);
        root.addChild(sibling);

        DescriptorOrder.compile(root);

        for (var descriptor : descendantsOrSelf(root)) {
            var expected = descendantsOrSelf(descriptor).stream()
                    .mapToLong(MutableDescriptor::orderRank)
                    .min()
                    .orElseThrow();
            assertThat(descriptor.orderSubtreeStart()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("ancestry agrees with the parent walk within and across compiled trees")
    void ancestryAgreesWithParentWalkWithinAndAcrossCompiledTrees() {
        var firstRoot = new ConcreteDescriptor(Step.of(STEP, context -> {}));
        var firstBranch = child(firstRoot, STEP);
        firstRoot.addChild(firstBranch);
        firstBranch.addChild(child(firstBranch, STEP));
        firstRoot.addChild(child(firstRoot, STEP));
        DescriptorOrder.compile(firstRoot);

        var secondRoot = new ConcreteDescriptor(Step.of(STEP, context -> {}));
        var secondBranch = child(secondRoot, STEP);
        secondRoot.addChild(secondBranch);
        secondBranch.addChild(child(secondBranch, STEP));
        DescriptorOrder.compile(secondRoot);

        assertThat(firstRoot.orderTreeId()).isNotEqualTo(secondRoot.orderTreeId());

        var descriptors = new ArrayList<>(descendantsOrSelf(firstRoot));
        descriptors.addAll(descendantsOrSelf(secondRoot));
        for (var descriptor : descriptors) {
            for (var ancestor : descriptors) {
                assertThat(DescriptorOrder.isSameOrDescendant(descriptor, ancestor))
                        .as("%s vs %s", descriptor.id(), ancestor.id())
                        .isEqualTo(walkIsSameOrDescendant(descriptor, ancestor));
            }
        }
    }

    @Test
    @DisplayName("an uncompiled descriptor is answered by the parent walk")
    void uncompiledDescriptorIsAnsweredByParentWalk() {
        var root = new ConcreteDescriptor(Step.of(STEP, context -> {}));
        var branch = child(root, STEP);
        root.addChild(branch);
        var leaf = child(branch, STEP);
        branch.addChild(leaf);

        assertThat(root.orderRank()).isEqualTo(DescriptorOrder.UNCOMPILED);
        assertThat(DescriptorOrder.isSameOrDescendant(leaf, root)).isTrue();
        assertThat(DescriptorOrder.isSameOrDescendant(leaf, branch)).isTrue();
        assertThat(DescriptorOrder.isSameOrDescendant(leaf, leaf)).isTrue();
        assertThat(DescriptorOrder.isSameOrDescendant(root, leaf)).isFalse();
        assertThat(DescriptorOrder.isSameOrDescendant(root, branch)).isFalse();
        assertThat(DescriptorOrder.isSameOrDescendant(null, root)).isFalse();
        assertThat(DescriptorOrder.isSameOrDescendant(leaf, null)).isFalse();
    }

    @Test
    @DisplayName("compiling twice is idempotent and keeps the original ranks")
    void compilingTwiceIsIdempotentAndKeepsRanks() {
        var root = new ConcreteDescriptor(Step.of(STEP, context -> {}));
        var branch = child(root, STEP);
        root.addChild(branch);

        DescriptorOrder.compile(branch);
        var treeId = root.orderTreeId();
        var rank = branch.orderRank();

        DescriptorOrder.compile(root);
        DescriptorOrder.compile(branch);

        assertThat(root.orderTreeId()).isEqualTo(treeId);
        assertThat(branch.orderRank()).isEqualTo(rank);
    }

    @Test
    @DisplayName("a deep chain compiles without adding recursion depth")
    void deepChainCompilesWithoutAddingRecursionDepth() {
        // Compilation itself is iterative. The depth bound here is inherited from the recursive
        // Descriptor#freeze that precedes it, not from the ranking walk.
        var depth = 2_000;
        var root = new ConcreteDescriptor(Step.of(STEP, context -> {}));
        var current = root;
        for (var i = 0; i < depth; i++) {
            var next = child(current, STEP);
            current.addChild(next);
            current = next;
        }

        DescriptorOrder.compile(root);

        assertThat(root.orderRank()).isEqualTo(depth);
        assertThat(current.orderRank()).isZero();
        assertThat(root.orderSubtreeStart()).isZero();
        assertThat(current.orderSubtreeStart()).isZero();
    }

    @Test
    @DisplayName("compile rejects a null descriptor")
    void compileRejectsNullDescriptor() {
        assertThatThrownBy(() -> DescriptorOrder.compile(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("descriptor is null");
        assertThatThrownBy(() -> DescriptorOrder.rootOf(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("descriptor is null");
    }

    private static ConcreteDescriptor child(final MutableDescriptor parent, final String name) {
        var descriptor = new ConcreteDescriptor(parent, Step.of(name, context -> {}));
        return descriptor;
    }

    private static List<MutableDescriptor> descendantsOrSelf(final MutableDescriptor root) {
        var result = new ArrayList<MutableDescriptor>();
        var pending = new ArrayList<MutableDescriptor>();
        pending.add(root);
        while (!pending.isEmpty()) {
            var descriptor = pending.remove(pending.size() - 1);
            result.add(descriptor);
            var after = descriptor.after().orElse(null);
            if (after != null) {
                pending.add(mutable(after));
            }
            var children = descriptor.children();
            for (var i = children.size() - 1; i >= 0; i--) {
                pending.add(mutable(children.get(i)));
            }
            var before = descriptor.before().orElse(null);
            if (before != null) {
                pending.add(mutable(before));
            }
        }
        return result;
    }

    /**
     * Builds the comparator the pre-compilation scheduler used: descriptor paths compared
     * lexicographically by structural slot index, with the deeper path first when one path is a
     * prefix of the other. Re-implemented here because the production type is gone.
     */
    private static Comparator<MutableDescriptor> pathOrder(final List<MutableDescriptor> descriptors) {
        var paths = new IdentityHashMap<MutableDescriptor, List<Integer>>();
        for (var descriptor : descriptors) {
            paths.put(descriptor, structuralPath(descriptor));
        }
        return (left, right) -> comparePaths(paths.get(left), paths.get(right));
    }

    private static List<Integer> structuralPath(final MutableDescriptor descriptor) {
        var path = new ArrayList<Integer>();
        MutableDescriptor current = descriptor;
        while (true) {
            var parent = current.parent().orElse(null);
            if (parent == null) {
                break;
            }
            path.add(slotIndexOf(mutable(parent), current));
            current = mutable(parent);
        }
        var reversed = new ArrayList<Integer>(path.size());
        for (var i = path.size() - 1; i >= 0; i--) {
            reversed.add(path.get(i));
        }
        return reversed;
    }

    private static int slotIndexOf(final MutableDescriptor parent, final MutableDescriptor child) {
        var index = 0;
        if (parent.before().orElse(null) == child) {
            return 0;
        }
        if (parent.before().isPresent()) {
            index = 1;
        }
        var children = parent.children();
        for (var i = 0; i < children.size(); i++) {
            if (children.get(i) == child) {
                return index + i;
            }
        }
        if (parent.after().orElse(null) == child) {
            return index + children.size();
        }
        throw new IllegalArgumentException("descriptor is not a child of the given parent");
    }

    private static int comparePaths(final List<Integer> left, final List<Integer> right) {
        var size = Math.min(left.size(), right.size());
        for (var i = 0; i < size; i++) {
            var comparison = Integer.compare(left.get(i), right.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(right.size(), left.size());
    }

    private static boolean walkIsSameOrDescendant(
            final MutableDescriptor descriptor, final MutableDescriptor ancestor) {
        MutableDescriptor current = descriptor;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.parent().map(DescriptorOrderTest::mutable).orElse(null);
        }
        return false;
    }

    private static MutableDescriptor mutable(final Descriptor descriptor) {
        return (MutableDescriptor) descriptor;
    }
}

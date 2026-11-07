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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Descriptor;

/**
 * Compiles the structural order of a finished descriptor tree into scalar scheduling metadata.
 *
 * <p>Each descriptor of a compiled tree carries its own <em>rank</em>, the lowest rank in its
 * subtree ({@code subtreeStart}), and the identity of the compilation ({@code treeId}). Ranks are
 * assigned by an ordered postorder walk over the tree's structural slot order — the {@code before}
 * subtree, the body children in declaration order, the {@code after} subtree, then the node
 * itself — so a node's descendants always precede it and an earlier sibling's whole subtree
 * precedes a later sibling's.
 *
 * <p>That order is exactly the order produced by comparing descriptor paths lexicographically with
 * descendants before their ancestor, which is what the scheduler needs: a node never competes with
 * its own children, and earlier branches are served first while both are admitted. Comparing two
 * ranks is constant time.
 *
 * <p>Because a node's subtree occupies the inclusive interval
 * {@code [subtreeStart, rank]}, ancestry within one compilation is also constant time. Ranks are
 * tree-local: {@code treeId} equality must hold before an interval may be interpreted, otherwise
 * two separately compiled trees would appear to contain each other's nodes.
 *
 * <p>Compilation is idempotent per tree, freezes the resolved tree root so the compiled structure
 * cannot change afterwards, and runs under the root's monitor so concurrent scheduling of two
 * descriptors in the same tree compiles once. It is iterative, so it adds no recursion limit of
 * its own.
 */
public final class DescriptorOrder {

    /**
     * Marker for a descriptor that has not been compiled yet.
     *
     * <p>Every compiled rank is non-negative, so reading {@code rank == UNCOMPILED} is a single
     * volatile read that answers "is this descriptor's order known".
     */
    public static final long UNCOMPILED = -1L;

    private static final AtomicLong NEXT_TREE_ID = new AtomicLong(0L);

    private DescriptorOrder() {
        // Intentionally empty
    }

    /**
     * Compiles the structural order of the tree containing {@code descriptor}.
     *
     * <p>Returns immediately when the descriptor is already compiled. Otherwise the tree root is
     * resolved, frozen if it is not already frozen, and compiled once. Resolving the root — rather
     * than compiling just the given descriptor's subtree — is required because ranks are
     * tree-global: ranks from a partial compilation would not be comparable with siblings
     * admitted elsewhere in the same tree.
     *
     * @param descriptor a descriptor belonging to the tree to compile; must not be {@code null}
     * @throws NullPointerException if {@code descriptor} is {@code null}
     * @throws IllegalStateException if the tree is too large to rank, or a descriptor is compiled
     *     twice
     */
    public static void compile(final MutableDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor is null");
        if (descriptor.orderRank() != UNCOMPILED) {
            return;
        }
        var root = rootOf(descriptor);
        if (root.orderRank() != UNCOMPILED) {
            return;
        }
        synchronized (root) {
            if (root.orderRank() != UNCOMPILED) {
                return;
            }
            if (!root.isFrozen()) {
                root.freeze();
            }
            compileFrozenTree(root, NEXT_TREE_ID.getAndIncrement());
        }
    }

    /**
     * Returns the root of the tree containing {@code descriptor}.
     *
     * @param descriptor a descriptor; must not be {@code null}
     * @return the root descriptor; never {@code null}
     */
    public static MutableDescriptor rootOf(final MutableDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor is null");
        var current = descriptor;
        var parent = current.parent().orElse(null);
        while (parent != null) {
            current =
                    Arguments.requireInstanceOf(parent, MutableDescriptor.class, "parent must be a MutableDescriptor");
            parent = current.parent().orElse(null);
        }
        return current;
    }

    /**
     * Returns whether {@code descriptor} is {@code ancestor} or a descendant of it.
     *
     * <p>When both descriptors belong to the same compilation the answer is the constant-time
     * inclusive-interval test {@code ancestor.subtreeStart <= descriptor.rank <= ancestor.rank}.
     * Otherwise — an uncompiled descriptor, or descriptors from different trees — the answer comes
     * from an exact walk of the parent chain, so no defaulted rank is ever treated as real.
     *
     * @param descriptor the candidate descriptor; may be {@code null}
     * @param ancestor the candidate ancestor; may be {@code null}
     * @return {@code true} when {@code descriptor} is {@code ancestor} or below it
     */
    public static boolean isSameOrDescendant(final Descriptor descriptor, final Descriptor ancestor) {
        if (descriptor instanceof MutableDescriptor candidate && ancestor instanceof MutableDescriptor bound) {
            var rank = candidate.orderRank();
            if (rank != UNCOMPILED && candidate.orderTreeId() == bound.orderTreeId()) {
                return bound.orderSubtreeStart() <= rank && rank <= bound.orderRank();
            }
        }
        for (var current = descriptor;
                current != null;
                current = current.parent().orElse(null)) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static void compileFrozenTree(final MutableDescriptor root, final long treeId) {
        var stack = new ArrayDeque<CompileFrame>();
        stack.push(new CompileFrame(root, 0L));
        var next = 0L;
        while (!stack.isEmpty()) {
            var frame = stack.peek();
            if (frame.nextSlot < frame.slots.size()) {
                var slot = frame.slots.get(frame.nextSlot++);
                var child =
                        Arguments.requireInstanceOf(slot, MutableDescriptor.class, "slot must be a MutableDescriptor");
                if (child.orderRank() != UNCOMPILED) {
                    throw new IllegalStateException("Descriptor order already compiled: " + child.id());
                }
                stack.push(new CompileFrame(child, next));
                continue;
            }
            stack.pop();
            if (next == Long.MAX_VALUE) {
                // Reject overflow rather than letting ranks wrap and reorder the tree silently.
                throw new IllegalStateException("Descriptor tree is too large to compile its order");
            }
            frame.descriptor.setOrder(treeId, frame.subtreeStart, next);
            next++;
        }
    }

    /**
     * One node of the iteration stack: the node, its structural slots in priority order, and the
     * rank counter value observed when the node was entered.
     */
    private static final class CompileFrame {

        private final MutableDescriptor descriptor;
        private final List<Descriptor> slots;
        private final long subtreeStart;
        private int nextSlot;

        private CompileFrame(final MutableDescriptor descriptor, final long subtreeStart) {
            this.descriptor = descriptor;
            this.subtreeStart = subtreeStart;
            var children = descriptor.children();
            var before = descriptor.before().orElse(null);
            var after = descriptor.after().orElse(null);
            var ordered = new ArrayList<Descriptor>(children.size() + 2);
            if (before != null) {
                ordered.add(before);
            }
            ordered.addAll(children);
            if (after != null) {
                ordered.add(after);
            }
            this.slots = List.copyOf(ordered);
        }
    }
}

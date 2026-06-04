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

package nonapi.org.paramixel.listener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import nonapi.org.paramixel.listener.support.Constants;
import org.paramixel.api.Descriptor;

/**
 * Renders a descriptor tree as a tree-style summary with connectors, timing, and failure information.
 *
 * <p>Each line includes the action status, name, timing, and failure or skip detail when available.
 *
 * <p>The renderer does <em>not</em> add the {@code [PARAMIXEL]} line prefix; that responsibility belongs to
 * {@link SummaryListener}.
 */
public final class TreeSummaryRenderer implements SummaryRenderer {

    private final boolean ansiEnabled;

    /**
     * Creates an ANSI-enabled renderer.
     */
    public TreeSummaryRenderer() {
        this(true);
    }

    /**
     * Creates a renderer.
     *
     * @param ansiEnabled whether ANSI status text should be used
     */
    public TreeSummaryRenderer(final boolean ansiEnabled) {
        this.ansiEnabled = ansiEnabled;
    }

    @Override
    public String render(final Descriptor root) {
        if (root == null) {
            return Constants.PARAMIXEL_PLAIN + "No Paramixel tests found" + System.lineSeparator();
        }
        var sb = new StringBuilder();
        Deque<RenderFrame> stack = new ArrayDeque<>();
        stack.push(new RenderFrame(root, "", true, RenderMode.SUBTREE));
        while (!stack.isEmpty()) {
            var frame = stack.pop();
            renderNodeLine(frame.descriptor(), frame.prefix(), frame.isLast(), sb);
            if (frame.mode() == RenderMode.LINE_ONLY) {
                continue;
            }

            var children = frame.descriptor().children();
            var hasBefore = frame.descriptor().before().isPresent();
            var hasAfter = frame.descriptor().after().isPresent();
            if (children.isEmpty() && !hasBefore && !hasAfter) {
                continue;
            }

            String continuation = ConnectorStyle.STANDARD.continuation(frame.isLast());
            String childPrefix = frame.prefix() + continuation;
            var childFrames = buildChildFrames(frame.descriptor(), children, childPrefix);
            pushInReverse(stack, childFrames);
        }
        return sb.toString();
    }

    private List<RenderFrame> buildChildFrames(
            final Descriptor descriptor, final List<Descriptor> children, final String childPrefix) {
        boolean hasBefore = descriptor.before().isPresent();
        boolean hasAfter = descriptor.after().isPresent();
        if (hasBefore || hasAfter) {
            return buildStructuredChildFrames(descriptor, children, childPrefix);
        }
        return buildFlatChildFrames(children, childPrefix);
    }

    private List<RenderFrame> buildFlatChildFrames(final List<Descriptor> children, final String childPrefix) {
        var frames = new ArrayList<RenderFrame>(children.size());
        for (var i = 0; i < children.size(); i++) {
            frames.add(new RenderFrame(children.get(i), childPrefix, i == children.size() - 1, RenderMode.SUBTREE));
        }
        return frames;
    }

    private List<RenderFrame> buildStructuredChildFrames(
            final Descriptor descriptor, final List<Descriptor> bodyChildren, final String childPrefix) {
        var beforeChild = descriptor.before().orElse(null);
        var afterChild = descriptor.after().orElse(null);

        var frames = new ArrayList<RenderFrame>();
        if (beforeChild != null) {
            boolean beforeIsLast = (afterChild == null) && bodyChildren.isEmpty();
            frames.add(new RenderFrame(beforeChild, childPrefix, beforeIsLast, RenderMode.LINE_ONLY));

            var beforeChildren = beforeChild.children();
            if (!beforeIsLast || !beforeChildren.isEmpty()) {
                String beforeContinuation = ConnectorStyle.STANDARD.continuation(beforeIsLast);
                String nestedPrefix = childPrefix + beforeContinuation;

                var nestedChildren = new ArrayList<Descriptor>(beforeChildren.size() + bodyChildren.size());
                nestedChildren.addAll(beforeChildren);
                nestedChildren.addAll(bodyChildren);

                for (var i = 0; i < nestedChildren.size(); i++) {
                    frames.add(new RenderFrame(
                            nestedChildren.get(i), nestedPrefix, i == nestedChildren.size() - 1, RenderMode.SUBTREE));
                }
            }
        } else {
            boolean hasAfter = afterChild != null;
            for (var i = 0; i < bodyChildren.size(); i++) {
                boolean isLast = !hasAfter && (i == bodyChildren.size() - 1);
                frames.add(new RenderFrame(bodyChildren.get(i), childPrefix, isLast, RenderMode.SUBTREE));
            }
        }

        if (afterChild != null) {
            frames.add(new RenderFrame(afterChild, childPrefix, true, RenderMode.SUBTREE));
        }
        return frames;
    }

    private static void pushInReverse(final Deque<RenderFrame> stack, final List<RenderFrame> frames) {
        for (var i = frames.size() - 1; i >= 0; i--) {
            stack.push(frames.get(i));
        }
    }

    private void renderNodeLine(
            final Descriptor descriptor, final String prefix, final boolean isLast, final StringBuilder sb) {
        String status = formatStatus(descriptor);
        String actionName = descriptor.action().displayName();
        String timing = formatTiming(Listeners.elapsedMillis(descriptor));
        String failureInfo = formatFailureInfo(descriptor);

        String connector = ConnectorStyle.STANDARD.connector(isLast);
        String line = prefix + connector + status + " " + actionName + " " + timing + failureInfo;
        sb.append(line.stripTrailing()).append(System.lineSeparator());
    }

    private String formatStatus(final Descriptor descriptor) {
        return ansiEnabled ? Listeners.formatAnsiStatus(descriptor) : Listeners.formatStatus(descriptor);
    }

    private String formatFailureInfo(final Descriptor descriptor) {
        if (descriptor.isFailed()) {
            return descriptor
                    .throwable()
                    .map(f -> " \u2192 " + f.getClass().getName() + ": " + Listeners.sanitizeMessage(f.getMessage()))
                    .or(() -> descriptor.message().map(m -> " \u2192 " + Listeners.sanitizeMessage(m)))
                    .orElse("");
        } else if (descriptor.isAborted()) {
            return descriptor
                    .message()
                    .map(reason -> " \u2192 " + Listeners.sanitizeMessage(reason))
                    .orElse("");
        } else if (descriptor.isSkipped()) {
            return descriptor
                    .message()
                    .map(reason -> " \u2192 " + Listeners.sanitizeMessage(reason))
                    .orElse("");
        }
        return "";
    }

    private static String formatTiming(final long elapsedMillis) {
        return elapsedMillis + " ms";
    }

    private record RenderFrame(Descriptor descriptor, String prefix, boolean isLast, RenderMode mode) {}

    private enum RenderMode {
        SUBTREE,
        LINE_ONLY
    }

    private enum ConnectorStyle {
        STANDARD("\u251C\u2500 ", "\u2514\u2500 ", "\u2502  ", "   ");

        private final String branch;
        private final String lastBranch;
        private final String continuation;
        private final String lastContinuation;

        ConnectorStyle(
                final String branch,
                final String lastBranch,
                final String continuation,
                final String lastContinuation) {
            this.branch = branch;
            this.lastBranch = lastBranch;
            this.continuation = continuation;
            this.lastContinuation = lastContinuation;
        }

        private String connector(final boolean isLast) {
            return isLast ? lastBranch : branch;
        }

        private String continuation(final boolean isLast) {
            return isLast ? lastContinuation : continuation;
        }
    }
}

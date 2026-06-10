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
import org.paramixel.api.Descriptor;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Conditional;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Repeat;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Static;
import org.paramixel.api.action.Timeout;
import org.paramixel.api.action.Until;

/**
 * Renders a descriptor tree as a tree-style summary with connectors, timing, and failure information.
 *
 * <p>Each line includes the action status, name, timing, and failure or skip detail when available.
 *
 * <p>The renderer does <em>not</em> add the {@code [PARAMIXEL]} line prefix; that responsibility belongs to
 * {@link SummaryListener}.
 */
@SuppressWarnings("removal")
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
        var stringBuilder = new StringBuilder();
        Deque<RenderFrame> stack = new ArrayDeque<>();
        if (isSyntheticRoot(root)) {
            renderRunLine(root, stringBuilder);
            pushInReverse(stack, buildChildFrames(root, ""));
        } else {
            stack.push(new RenderFrame(root, "", true, null));
        }
        while (!stack.isEmpty()) {
            var frame = stack.pop();
            renderNodeLine(frame, stringBuilder);

            var childFrames = buildChildFrames(
                    frame.descriptor(), frame.prefix() + ConnectorStyle.STANDARD.continuation(frame.isLast()));
            pushInReverse(stack, childFrames);
        }
        return stringBuilder.toString();
    }

    private static boolean isSyntheticRoot(final Descriptor descriptor) {
        return Constants.ROOT_NAME.equals(descriptor.action().displayName());
    }

    private List<RenderFrame> buildChildFrames(final Descriptor descriptor, final String childPrefix) {
        var children = new ArrayList<ChildFrame>();
        descriptor.before().ifPresent(child -> children.add(new ChildFrame(child, "before")));
        String bodySlot = supportsBody(descriptor.action()) ? "body" : null;
        descriptor.children().forEach(child -> children.add(new ChildFrame(child, bodySlot)));
        descriptor.after().ifPresent(child -> children.add(new ChildFrame(child, "after")));
        return buildFlatChildFrames(children, childPrefix);
    }

    private List<RenderFrame> buildFlatChildFrames(final List<ChildFrame> children, final String childPrefix) {
        var frames = new ArrayList<RenderFrame>(children.size());
        for (var i = 0; i < children.size(); i++) {
            var child = children.get(i);
            frames.add(new RenderFrame(child.descriptor(), childPrefix, i == children.size() - 1, child.slot()));
        }
        return frames;
    }

    private static boolean supportsBody(final Action action) {
        return action instanceof Conditional
                || action instanceof Instance
                || action instanceof Isolated
                || action instanceof Repeat
                || action instanceof Scope
                || action instanceof Static
                || action instanceof Timeout
                || action instanceof Until;
    }

    private static void pushInReverse(final Deque<RenderFrame> stack, final List<RenderFrame> frames) {
        for (var i = frames.size() - 1; i >= 0; i--) {
            stack.push(frames.get(i));
        }
    }

    private void renderRunLine(final Descriptor descriptor, final StringBuilder stringBuilder) {
        String status = formatStatus(descriptor);
        String timing = formatTiming(Listeners.elapsedMillis(descriptor));
        String failureInfo = formatFailureInfo(descriptor);
        String line = status + " Run " + timing + failureInfo;
        stringBuilder.append(line.stripTrailing()).append(System.lineSeparator());
    }

    private void renderNodeLine(final RenderFrame frame, final StringBuilder stringBuilder) {
        var descriptor = frame.descriptor();
        String status = formatStatus(descriptor);
        String actionName = descriptor.action().displayName();
        String timing = formatTiming(Listeners.elapsedMillis(descriptor));
        String failureInfo = formatFailureInfo(descriptor);

        String connector = ConnectorStyle.STANDARD.connector(frame.isLast());
        String line = frame.prefix() + connector + formatLabel(frame.slot(), descriptor.action()) + status + " "
                + actionName + " " + timing + failureInfo;
        stringBuilder.append(line.stripTrailing()).append(System.lineSeparator());
    }

    private static String formatLabel(final String slot, final Action action) {
        String actionType = actionType(action);
        if (slot == null) {
            return actionType + ": ";
        }
        return slot + "[" + actionType + "]: ";
    }

    private static String actionType(final Action action) {
        String simpleName = action.getClass().getSimpleName();
        if (simpleName.isBlank()) {
            return "action";
        }
        return simpleName.toLowerCase(java.util.Locale.ROOT);
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

    private record ChildFrame(Descriptor descriptor, String slot) {}

    private record RenderFrame(Descriptor descriptor, String prefix, boolean isLast, String slot) {}

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

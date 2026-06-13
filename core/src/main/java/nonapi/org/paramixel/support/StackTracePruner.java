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

package nonapi.org.paramixel.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Prunes Paramixel framework frames from exception stack traces so user-code
 * origins are visible without internal implementation noise.
 *
 * <p>Frames whose class name starts with {@code nonapi.org.paramixel.} or
 * {@code org.paramixel.} are removed. All other frames — including user-code,
 * JDK, and third-party library frames — are preserved in their original order.
 *
 * <p>If the resulting stack trace is empty, the original stack trace is kept
 * unchanged to avoid confusing empty output.
 */
public final class StackTracePruner {

    private static final List<String> FRAMEWORK_PREFIXES = List.of("nonapi.org.paramixel.", "org.paramixel.");

    private StackTracePruner() {
        // Intentionally empty
    }

    /**
     * Removes Paramixel framework frames from the stack trace of the given
     * throwable.
     *
     * <p>Null input and empty stack traces are silently accepted and do nothing.
     *
     * @param throwable the throwable whose stack trace to prune; may be
     *     {@code null}
     */
    public static void prune(final Throwable throwable) {
        if (throwable == null) {
            return;
        }
        StackTraceElement[] frames = throwable.getStackTrace();
        if (frames.length == 0) {
            return;
        }
        var pruned = new ArrayList<StackTraceElement>();
        for (StackTraceElement frame : frames) {
            if (!isFramework(frame.getClassName())) {
                pruned.add(frame);
            }
        }
        if (pruned.isEmpty()) {
            return;
        }
        throwable.setStackTrace(pruned.toArray(new StackTraceElement[0]));
    }

    private static boolean isFramework(final String className) {
        for (String prefix : FRAMEWORK_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}

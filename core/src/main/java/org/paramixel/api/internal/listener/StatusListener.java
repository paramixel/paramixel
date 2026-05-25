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

package org.paramixel.api.internal.listener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Objects;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Descriptor;
import org.paramixel.api.internal.listener.support.Constants;

/**
 * Console listener that prints descriptor start and completion status lines.
 */
public class StatusListener implements Listener {

    private static final Object OUTPUT_LOCK = new Object();
    private final boolean ansiEnabled;

    /**
     * Creates a status listener with ANSI formatting enabled.
     */
    public StatusListener() {
        this(true);
    }

    /**
     * Creates a status listener.
     *
     * @param ansiEnabled whether ANSI formatting is enabled
     */
    public StatusListener(final boolean ansiEnabled) {
        this.ansiEnabled = ansiEnabled;
    }

    @Override
    public void onBeforeExecution(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        var output = prefix() + Listeners.formatIdPath(descriptor) + " | " + displayName(descriptor);
        synchronized (OUTPUT_LOCK) {
            System.out.println(output);
        }
    }

    @Override
    public void onAfterExecution(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        var statusText = ansiEnabled
                ? Listeners.formatAnsiStatus(descriptor.metadata().status())
                : Listeners.formatStatus(descriptor.metadata().status());
        var output = prefix()
                + statusText
                + " | "
                + Listeners.formatIdPath(descriptor)
                + " | "
                + displayName(descriptor)
                + " "
                + formatTiming(descriptor.metadata().runDuration());
        synchronized (OUTPUT_LOCK) {
            System.out.println(output);
        }
        descriptor.metadata().throwable().ifPresent(throwable -> {
            var sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            synchronized (OUTPUT_LOCK) {
                System.err.println(prefix() + "EXCEPTION | " + Listeners.formatIdPath(descriptor) + " | "
                        + displayName(descriptor));
                System.err.print(sw);
            }
        });
    }

    private String prefix() {
        return ansiEnabled ? Constants.PARAMIXEL_ANSI : Constants.PARAMIXEL_PLAIN;
    }

    private static String displayName(final Descriptor descriptor) {
        return Listeners.formatNamePath(descriptor) + " (" + Listeners.formatKind(descriptor) + ")";
    }

    private static String formatTiming(final Duration timing) {
        Objects.requireNonNull(timing, "timing must not be null");
        return timing.toMillis() + " ms";
    }
}

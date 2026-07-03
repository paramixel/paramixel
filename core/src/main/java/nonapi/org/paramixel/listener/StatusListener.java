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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import nonapi.org.paramixel.support.AnsiDetector;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;

/**
 * Console listener that prints descriptor start and completion status lines.
 */
public class StatusListener implements Listener {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private volatile boolean ansiEnabled;
    private volatile EnumSet<ExcludeTarget> excludes = EnumSet.noneOf(ExcludeTarget.class);

    /**
     * Creates a status listener with ANSI formatting enabled by default.
     */
    public StatusListener() {}

    @Override
    public void initialize(final Configuration configuration) {
        var value = configuration.getString(Configuration.ANSI).orElse(null);
        if (value == null || value.isBlank()) {
            this.ansiEnabled = AnsiDetector.isAnsiAvailable();
        } else {
            this.ansiEnabled = "true".equals(value.strip().toLowerCase(Locale.ROOT));
        }
        this.excludes = Listeners.parseExcludes(
                configuration.getString(Configuration.LISTENER_EXCLUDE).orElse(null));
    }

    @Override
    public void onBeforeExecution(final Descriptor descriptor) {
        if (excludes.contains(ExcludeTarget.STATUS_HEADER)) {
            return;
        }
        Objects.requireNonNull(descriptor, "descriptor is null");
        var stringBuilder = new StringBuilder();
        stringBuilder
                .append(prefix())
                .append(Thread.currentThread().getName())
                .append(" | ")
                .append(displayName(descriptor))
                .append(LINE_SEPARATOR);
        System.out.print(stringBuilder);
        System.out.flush();
    }

    @Override
    public void onAfterExecution(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor is null");

        if (!excludes.contains(ExcludeTarget.STATUS_FOOTER)) {
            var stringBuilder = new StringBuilder();
            var statusText = ansiEnabled ? Listeners.formatAnsiStatus(descriptor) : Listeners.formatStatus(descriptor);
            stringBuilder
                    .append(prefix())
                    .append(statusText)
                    .append(" | ")
                    .append(Thread.currentThread().getName())
                    .append(" | ")
                    .append(displayName(descriptor))
                    .append(' ')
                    .append(formatTiming(Listeners.elapsedMillis(descriptor)));
            var throwable = descriptor.throwable().orElse(null);
            if (throwable != null) {
                var msg = Listeners.sanitizeMessage(throwable.getMessage());
                stringBuilder.append(" | ").append(throwable.getClass().getName());
                if (msg != null && !msg.isBlank()) {
                    stringBuilder.append(": ").append(msg);
                }
                stringBuilder.append(LINE_SEPARATOR);
                var stringWriter = new StringWriter();
                throwable.printStackTrace(new PrintWriter(stringWriter));
                try (var br = new BufferedReader(new StringReader(stringWriter.toString()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        stringBuilder
                                .append(prefix())
                                .append(Listeners.stripUnsafe(line))
                                .append(LINE_SEPARATOR);
                    }
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            } else {
                descriptor
                        .message()
                        .ifPresent(message -> stringBuilder.append(" | ").append(Listeners.sanitizeMessage(message)));
                stringBuilder.append(LINE_SEPARATOR);
            }
            System.out.print(stringBuilder);
            System.out.flush();
        } else {
            descriptor
                    .throwable()
                    .ifPresentOrElse(
                            throwable -> {
                                var stringBuilder = new StringBuilder();
                                var sw = new StringWriter();
                                throwable.printStackTrace(new PrintWriter(sw));
                                try (var br = new BufferedReader(new StringReader(sw.toString()))) {
                                    String line;
                                    while ((line = br.readLine()) != null) {
                                        stringBuilder
                                                .append(prefix())
                                                .append(Listeners.stripUnsafe(line))
                                                .append(LINE_SEPARATOR);
                                    }
                                } catch (IOException e) {
                                    throw new AssertionError(e);
                                }
                                System.out.print(stringBuilder);
                                System.out.flush();
                            },
                            () -> descriptor.message().ifPresent(message -> {
                                var stringBuilder = new StringBuilder();
                                stringBuilder
                                        .append(prefix())
                                        .append(Listeners.sanitizeMessage(message))
                                        .append(LINE_SEPARATOR);
                                System.out.print(stringBuilder);
                                System.out.flush();
                            }));
        }
    }

    private String prefix() {
        return ansiEnabled ? Constants.PARAMIXEL_ANSI : Constants.PARAMIXEL_PLAIN;
    }

    private static String displayName(final Descriptor descriptor) {
        return Listeners.formatNamePath(descriptor);
    }

    private static String formatTiming(final long elapsedMillis) {
        return elapsedMillis + " ms";
    }
}

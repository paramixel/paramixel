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
import nonapi.org.paramixel.listener.support.Constants;
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
        if (excludes.contains(ExcludeTarget.STATUS_HEADER)) return;
        Objects.requireNonNull(descriptor, "descriptor is null");
        var sb = new StringBuilder();
        sb.append(prefix())
                .append(Listeners.formatIdPath(descriptor))
                .append(" | ")
                .append(displayName(descriptor))
                .append(LINE_SEPARATOR);
        System.out.print(sb.toString());
    }

    @Override
    public void onAfterExecution(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor is null");

        if (!excludes.contains(ExcludeTarget.STATUS_FOOTER)) {
            var sb = new StringBuilder();
            var statusText = ansiEnabled ? Listeners.formatAnsiStatus(descriptor) : Listeners.formatStatus(descriptor);
            sb.append(prefix())
                    .append(statusText)
                    .append(" | ")
                    .append(Listeners.formatIdPath(descriptor))
                    .append(" | ")
                    .append(displayName(descriptor))
                    .append(' ')
                    .append(formatTiming(Listeners.elapsedMillis(descriptor)));
            var throwable = descriptor.throwable().orElse(null);
            if (throwable != null) {
                sb.append(" | ")
                        .append(throwable.getClass().getName())
                        .append(": ")
                        .append(throwable.getMessage());
                var sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                try (var br = new BufferedReader(new StringReader(sw.toString()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(prefix()).append(line).append(LINE_SEPARATOR);
                    }
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            } else {
                sb.append(LINE_SEPARATOR);
            }
            System.out.print(sb.toString());
        } else {
            descriptor.throwable().ifPresent(throwable -> {
                var sb = new StringBuilder();
                var sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                try (var br = new BufferedReader(new StringReader(sw.toString()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(prefix()).append(line).append(LINE_SEPARATOR);
                    }
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
                System.out.print(sb.toString());
            });
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

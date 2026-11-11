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

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import nonapi.org.paramixel.support.AnsiDetector;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;

/**
 * Builds the standard listener chain.
 *
 * <p>This used to live inside the public {@link Listener} interface; it is internal because it constructs internal
 * listeners. {@link Listener#defaultListener(Configuration)} delegates here.
 */
public final class DefaultListeners {

    private DefaultListeners() {
        // Intentionally empty
    }

    /**
     * Creates the standard listener chain for the supplied configuration.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @return the listener chain; never {@code null}
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public static Listener create(final Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration is null");

        final var ansiEnabled = resolveAnsiEnabled(configuration);
        final var statusListener = new StatusListener();
        final var summaryListener = new SummaryListener();

        final var reportFile =
                configuration.getString(Configuration.REPORT_FILE).orElse(null);
        if (isBlank(reportFile)) {
            return new SafeListener(
                    new ConfiguredCompositeListener(
                            configuration, List.of(statusListener, summaryListener), ansiEnabled),
                    ansiEnabled);
        }

        final var reportListener = createReportListener(resolveReportFormat(reportFile));
        return new SafeListener(
                new ConfiguredCompositeListener(
                        configuration, List.of(statusListener, summaryListener, reportListener), ansiEnabled),
                ansiEnabled);
    }

    private static boolean resolveAnsiEnabled(final Configuration configuration) {
        final var value = configuration.getString(Configuration.ANSI).orElse(null);
        if (value == null || value.isBlank()) {
            return AnsiDetector.isAnsiAvailable();
        }
        final var trimmed = value.strip().toLowerCase(Locale.ROOT);
        return switch (trimmed) {
            case "true" -> true;
            case "false" -> false;
            default -> AnsiDetector.isAnsiAvailable();
        };
    }

    private static String resolveReportFormat(final String reportFile) {
        final var lowerReportFile = reportFile.toLowerCase(Locale.ROOT);
        if (lowerReportFile.endsWith(".json")) {
            return "json";
        } else if (lowerReportFile.endsWith(".xml")) {
            return "xml";
        } else if (lowerReportFile.endsWith(".html") || lowerReportFile.endsWith(".htm")) {
            return "html";
        }
        return "text";
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private static Listener createReportListener(final String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> new JsonReportListener();
            case "xml" -> new XmlReportListener();
            case "html" -> new HtmlReportListener();
            default -> new ReportListener();
        };
    }
}

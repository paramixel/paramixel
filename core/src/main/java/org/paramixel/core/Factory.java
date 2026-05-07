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

package org.paramixel.core;

import java.util.Map;
import java.util.Objects;
import org.paramixel.core.spi.DefaultRunner;
import org.paramixel.core.spi.listener.CompositeListener;
import org.paramixel.core.spi.listener.HtmlReportListener;
import org.paramixel.core.spi.listener.JsonReportListener;
import org.paramixel.core.spi.listener.ReportListener;
import org.paramixel.core.spi.listener.SafeListener;
import org.paramixel.core.spi.listener.StatusListener;
import org.paramixel.core.spi.listener.SummaryListener;
import org.paramixel.core.spi.listener.TreeSummaryRenderer;
import org.paramixel.core.spi.listener.XmlReportListener;

/**
 * Provides convenience factories for commonly used Paramixel defaults.
 *
 * <p>This class supplies preconfigured runner and listener instances suitable for standard command-line execution.
 */
public class Factory {

    private Factory() {}

    /**
     * Creates a runner using the framework's default configuration and listener chain.
     *
     * <p>The returned runner uses {@link Configuration#defaultProperties()}, {@link #defaultListener()}, and an
     * internally managed executor strategy.
     *
     * @return a default runner instance
     */
    public static Runner defaultRunner() {
        return new DefaultRunner(Configuration.defaultProperties(), defaultListener(), null);
    }

    /**
     * Creates the default listener chain used by Paramixel.
     *
     * <p>The returned listener wraps a {@link CompositeListener} combining a {@link StatusListener} and a
     * {@link SummaryListener} with a {@link TreeSummaryRenderer}, and then protects that chain with
     * {@link SafeListener}.
     *
     * @return the default listener
     */
    public static Listener defaultListener() {
        return new SafeListener(
                new CompositeListener(new StatusListener(), new SummaryListener(new TreeSummaryRenderer())));
    }

    /**
     * Creates the default listener chain used by Paramixel for the supplied configuration.
     *
     * <p>When {@link Configuration#REPORT_FILE} is configured, the returned chain includes a report listener in addition
     * to the standard console listeners. The report listener type is determined by the
     * {@link Configuration#REPORT_FORMAT} configuration key when present, or inferred from the report file extension.
     *
     * @param configuration the effective Paramixel configuration
     * @return the default listener
     */
    public static Listener defaultListener(final Map<String, String> configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");

        String reportFile = configuration.get(Configuration.REPORT_FILE);
        if (isBlank(reportFile)) {
            return defaultListener();
        }

        String reportFormat = resolveReportFormat(configuration, reportFile);

        Listener reportListener = createReportListener(reportFormat, reportFile);
        return new SafeListener(new CompositeListener(
                new StatusListener(), new SummaryListener(new TreeSummaryRenderer()), reportListener));
    }

    private static String resolveReportFormat(final Map<String, String> configuration, final String reportFile) {
        String explicitFormat = configuration.get(Configuration.REPORT_FORMAT);
        if (!isBlank(explicitFormat)) {
            return explicitFormat;
        }

        String lowerReportFile = reportFile.toLowerCase();
        if (lowerReportFile.endsWith(".json")) {
            return "json";
        } else if (lowerReportFile.endsWith(".xml")) {
            return "xml";
        } else if (lowerReportFile.endsWith(".html")) {
            return "html";
        } else if (lowerReportFile.endsWith(".log") || lowerReportFile.endsWith(".txt")) {
            return Configuration.REPORT_FORMAT_TEXT;
        }
        return Configuration.REPORT_FORMAT_TEXT;
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Listener createReportListener(String reportFormat, String reportFile) {
        return switch (reportFormat.toLowerCase()) {
            case Configuration.REPORT_FORMAT_TEXT -> new ReportListener(reportFile);
            case "json" -> new JsonReportListener(reportFile);
            case "xml" -> new XmlReportListener(reportFile);
            case "html" -> new HtmlReportListener(reportFile);
            default -> new ReportListener(reportFile);
        };
    }
}

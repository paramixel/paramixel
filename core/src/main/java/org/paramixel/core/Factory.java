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

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.paramixel.core.internal.DefaultRunner;
import org.paramixel.core.internal.listener.CompositeListener;
import org.paramixel.core.internal.listener.HtmlReportListener;
import org.paramixel.core.internal.listener.JsonReportListener;
import org.paramixel.core.internal.listener.ReportListener;
import org.paramixel.core.internal.listener.SafeListener;
import org.paramixel.core.internal.listener.StatusListener;
import org.paramixel.core.internal.listener.SummaryListener;
import org.paramixel.core.internal.listener.TreeSummaryRenderer;
import org.paramixel.core.internal.listener.XmlReportListener;

/**
 * Creates preconfigured runner and listener instances for standard Paramixel runs.
 *
 * <p>This class supplies preconfigured runner and listener instances suitable for standard command-line runs.
 */
public class Factory {

    private static final String TEXT_REPORT_FORMAT = "text";

    private Factory() {
        // Intentionally empty
    }

    /**
     * Creates a runner using the framework's default configuration and listener chain.
     *
     * <p>The returned runner uses {@link Configuration#defaultProperties()}, {@link #defaultListener()}, and an
     * internally managed executor strategy.
     *
     * @return a default runner instance
     */
    public static Runner defaultRunner() {
        return new DefaultRunner(Configuration.defaultProperties(), defaultListener());
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
     * to the standard console listeners. The report listener type is inferred from the report file extension.
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

        String format = resolveReportFormat(reportFile);

        Listener reportListener = createReportListener(format, reportFile);
        return new SafeListener(new CompositeListener(
                new StatusListener(), new SummaryListener(new TreeSummaryRenderer()), reportListener));
    }

    private static String resolveReportFormat(final String reportFile) {
        String lowerReportFile = reportFile.toLowerCase(Locale.ROOT);
        if (lowerReportFile.endsWith(".json")) {
            return "json";
        } else if (lowerReportFile.endsWith(".xml")) {
            return "xml";
        } else if (lowerReportFile.endsWith(".html")) {
            return "html";
        }
        return TEXT_REPORT_FORMAT;
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Listener createReportListener(String format, String reportFile) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case TEXT_REPORT_FORMAT -> new ReportListener(reportFile);
            case "json" -> new JsonReportListener(reportFile);
            case "xml" -> new XmlReportListener(reportFile);
            case "html" -> new HtmlReportListener(reportFile);
            default -> new ReportListener(reportFile);
        };
    }
}

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

package org.paramixel.api;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import nonapi.org.paramixel.listener.ConfiguredCompositeListener;
import nonapi.org.paramixel.listener.HtmlReportListener;
import nonapi.org.paramixel.listener.JsonReportListener;
import nonapi.org.paramixel.listener.ReportListener;
import nonapi.org.paramixel.listener.SafeListener;
import nonapi.org.paramixel.listener.StatusListener;
import nonapi.org.paramixel.listener.SummaryListener;
import nonapi.org.paramixel.listener.XmlReportListener;
import nonapi.org.paramixel.support.AnsiDetector;
import org.paramixel.api.action.Action;

/**
 * Receives run, discovery, and descriptor execution callbacks.
 *
 * <p>Actions own descriptor execution callbacks and invoke {@link #onBeforeExecution(Descriptor)}
 * and {@link #onAfterExecution(Descriptor)} around their own execution boundary. The runner invokes
 * run-level and discovery callbacks.
 */
public interface Listener {

    /**
     * Returns the standard listener chain using the default configuration.
     *
     * @return the default listener chain
     */
    static Listener defaultListener() {
        return defaultListener(Configuration.defaultConfiguration());
    }

    /**
     * Returns the standard listener chain using the supplied configuration.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @return the default listener chain
     */
    static Listener defaultListener(final Configuration configuration) {
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

        final var format = resolveReportFormat(reportFile);
        final var reportListener = createReportListener(format);
        return new SafeListener(
                new ConfiguredCompositeListener(
                        configuration, List.of(statusListener, summaryListener, reportListener), ansiEnabled),
                ansiEnabled);
    }

    /**
     * Invoked once before the run begins, allowing listeners to configure themselves based on the
     * supplied configuration.
     *
     * <p>This method is called exactly once per {@link Runner#run(Action)} invocation, before any other
     * listener callbacks. It is called from a synchronized context ({@code Runner.run()}).
     *
     * <p>Implementations are responsible for thread-safety if they maintain mutable state.
     *
     * @param configuration the effective configuration; never {@code null}
     * @see SafeListener
     */
    default void initialize(final Configuration configuration) {
        // Intentionally empty
    }

    /**
     * Invoked once before run discovery begins.
     *
     * <p>This method is called exactly once per {@link Runner#run(Action)} invocation, after
     * {@link #initialize(Configuration)} and {@link #onRunStarted()}, but before the discovery phase.
     *
     * <p>Implementations are responsible for thread-safety if they maintain mutable state.
     *
     * @see SafeListener
     */
    default void onDiscoveryStarted() {
        // Intentionally empty
    }

    /**
     * Invoked after discovery creates the descriptor tree.
     *
     * @param root the discovered root descriptor; never {@code null}
     */
    default void onDiscoveryCompleted(final Descriptor root) {
        // Intentionally empty
    }

    /**
     * Invoked once before the run begins, before discovery and execution.
     */
    default void onRunStarted() {
        // Intentionally empty
    }

    /**
     * Invoked by an action immediately before its descriptor execution logic runs.
     *
     * @param descriptor the descriptor about to execute; never {@code null}
     */
    default void onBeforeExecution(final Descriptor descriptor) {
        // Intentionally empty
    }

    /**
     * Invoked by an action after its descriptor reaches a terminal status.
     *
     * @param descriptor the completed descriptor; never {@code null}
     */
    default void onAfterExecution(final Descriptor descriptor) {
        // Intentionally empty
    }

    /**
     * Invoked once after the run completes.
     *
     * @param result the run result containing the root descriptor and effective aggregate status; never {@code null}
     */
    default void onRunCompleted(final Result result) {
        // Intentionally empty
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

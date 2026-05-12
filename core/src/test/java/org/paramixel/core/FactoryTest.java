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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.internal.DefaultRunner;
import org.paramixel.core.internal.listener.CompositeListener;
import org.paramixel.core.internal.listener.HtmlReportListener;
import org.paramixel.core.internal.listener.JsonReportListener;
import org.paramixel.core.internal.listener.ReportListener;
import org.paramixel.core.internal.listener.SafeListener;
import org.paramixel.core.internal.listener.XmlReportListener;

@DisplayName("Factory")
class FactoryTest {

    @Test
    @DisplayName("defaultRunner returns DefaultRunner")
    void defaultRunnerReturnsDefaultRunner() {
        Runner runner = Factory.defaultRunner();
        assertThat(runner).isInstanceOf(DefaultRunner.class);
    }

    @Test
    @DisplayName("defaultListener returns SafeListener")
    void defaultListenerReturnsSafeListener() {
        Listener listener = Factory.defaultListener();
        assertThat(listener).isInstanceOf(SafeListener.class);
    }

    @Test
    @DisplayName("defaultListener(Map) rejects null configuration")
    void defaultListenerRejectsNullConfiguration() {
        assertThatThrownBy(() -> Factory.defaultListener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("configuration must not be null");
    }

    @Test
    void defaultListenerWithReportFileAbsentOmitsReportListener() throws Exception {
        Listener listener = Factory.defaultListener(Map.of());

        assertThat(listener).isInstanceOf(SafeListener.class);
        assertThat(extractListeners(listener)).hasSize(2);
        assertThat(extractListeners(listener)).noneMatch(ReportListener.class::isInstance);
    }

    @Test
    @DisplayName("defaultListener(configuration) omits report listener when report file is blank")
    void defaultListenerWithBlankReportFileOmitsReportListener() throws Exception {
        Listener listener = Factory.defaultListener(Map.of(Configuration.REPORT_FILE, " "));

        assertThat(listener).isInstanceOf(SafeListener.class);
        assertThat(extractListeners(listener)).hasSize(2);
        assertThat(extractListeners(listener)).noneMatch(ReportListener.class::isInstance);
    }

    @Test
    @DisplayName("defaultListener(configuration) defaults to text format when format not specified")
    void defaultListenerDefaultsToTextFormat() throws Exception {
        Listener listener =
                Factory.defaultListener(Map.of(Configuration.REPORT_FILE, "target/custom-report/report.out"));

        assertThat(listener).isInstanceOf(SafeListener.class);
        assertThat(extractListeners(listener)).hasSize(3);
        assertThat(extractListeners(listener)).anyMatch(ReportListener.class::isInstance);
    }

    @Test
    @DisplayName("defaultListener(configuration) infers report format from file extension")
    void defaultListenerInfersReportFormatFromFileExtension() throws Exception {
        Listener json = Factory.defaultListener(Map.of(Configuration.REPORT_FILE, "target/report.json"));
        Listener xml = Factory.defaultListener(Map.of(Configuration.REPORT_FILE, "target/report.xml"));
        Listener html = Factory.defaultListener(Map.of(Configuration.REPORT_FILE, "target/report.html"));
        Listener log = Factory.defaultListener(Map.of(Configuration.REPORT_FILE, "target/report.log"));
        Listener txt = Factory.defaultListener(Map.of(Configuration.REPORT_FILE, "target/report.txt"));
        Listener upper = Factory.defaultListener(Map.of(Configuration.REPORT_FILE, "target/REPORT.JSON"));

        assertThat(extractListeners(json)).anyMatch(JsonReportListener.class::isInstance);
        assertThat(extractListeners(xml)).anyMatch(XmlReportListener.class::isInstance);
        assertThat(extractListeners(html)).anyMatch(HtmlReportListener.class::isInstance);
        assertThat(extractListeners(log)).anyMatch(ReportListener.class::isInstance);
        assertThat(extractListeners(txt)).anyMatch(ReportListener.class::isInstance);
        assertThat(extractListeners(upper)).anyMatch(JsonReportListener.class::isInstance);
    }

    @Test
    @DisplayName("defaultListener(configuration) defaults inferred format to text for unknown extensions")
    void defaultListenerDefaultsInferredFormatToTextForUnknownExtensions() throws Exception {
        Listener unknown = Factory.defaultListener(Map.of(Configuration.REPORT_FILE, "target/report.yaml"));
        Listener missing = Factory.defaultListener(Map.of(Configuration.REPORT_FILE, "target/report"));

        assertThat(extractListeners(unknown)).anyMatch(ReportListener.class::isInstance);
        assertThat(extractListeners(missing)).anyMatch(ReportListener.class::isInstance);
    }

    @Test
    @DisplayName("defaultListener(configuration) honors deprecated explicit format")
    @SuppressWarnings("removal")
    void defaultListenerHonorsDeprecatedExplicitFormat() throws Exception {
        Listener listener = Factory.defaultListener(
                Map.of(Configuration.REPORT_FILE, "target/report.out", Configuration.REPORT_FORMAT, "json"));

        assertThat(extractListeners(listener)).anyMatch(JsonReportListener.class::isInstance);
    }

    @Test
    @DisplayName("defaultListener(configuration) infers format when deprecated explicit format is blank")
    @SuppressWarnings("removal")
    void defaultListenerInfersFormatWhenDeprecatedExplicitFormatIsBlank() throws Exception {
        Listener listener = Factory.defaultListener(
                Map.of(Configuration.REPORT_FILE, "target/report.xml", Configuration.REPORT_FORMAT, " "));

        assertThat(extractListeners(listener)).anyMatch(XmlReportListener.class::isInstance);
    }

    @SuppressWarnings("unchecked")
    private static List<Listener> extractListeners(Listener listener) throws Exception {
        Field delegateField = SafeListener.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        Object delegate = delegateField.get(listener);

        Field listenersField = CompositeListener.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);
        return (List<Listener>) listenersField.get(delegate);
    }
}

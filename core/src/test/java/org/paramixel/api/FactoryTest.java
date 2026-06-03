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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import nonapi.org.paramixel.ConcreteRunner;
import nonapi.org.paramixel.listener.CompositeListener;
import nonapi.org.paramixel.listener.HtmlReportListener;
import nonapi.org.paramixel.listener.JsonReportListener;
import nonapi.org.paramixel.listener.ReportListener;
import nonapi.org.paramixel.listener.SafeListener;
import nonapi.org.paramixel.listener.StatusListener;
import nonapi.org.paramixel.listener.XmlReportListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.api.action.Step;

@DisplayName("Listener.defaultListener")
class FactoryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("defaultRunner returns ConcreteRunner")
    void defaultRunnerReturnsConcreteRunner() {
        Runner runner = Runner.defaultRunner();
        assertThat(runner).isInstanceOf(ConcreteRunner.class);
    }

    @Test
    @DisplayName("defaultListener returns SafeListener")
    void defaultListenerReturnsSafeListener() {
        Listener listener = Listener.defaultListener();
        assertThat(listener).isInstanceOf(SafeListener.class);
    }

    @Test
    void defaultListenerWithReportFileAbsentOmitsReportListener() throws Exception {
        Listener listener = Listener.defaultListener(Configuration.of(Map.of()));

        assertThat(listener).isInstanceOf(SafeListener.class);
        assertThat(extractListeners(listener)).hasSize(2);
        assertThat(extractListeners(listener)).noneMatch(ReportListener.class::isInstance);
    }

    @Test
    @DisplayName("defaultListener(configuration) defaults to text format when format not specified")
    void defaultListenerDefaultsToTextFormat() throws Exception {
        Listener listener = Listener.defaultListener(
                Configuration.of(Map.of(Configuration.REPORT_FILE, "target/custom-report/report.out")));

        assertThat(listener).isInstanceOf(SafeListener.class);
        assertThat(extractListeners(listener)).hasSize(3);
        assertThat(extractListeners(listener)).anyMatch(ReportListener.class::isInstance);
    }

    @Test
    @DisplayName("defaultListener(configuration) infers report format from file extension")
    void defaultListenerInfersReportFormatFromFileExtension() throws Exception {
        Listener json =
                Listener.defaultListener(Configuration.of(Map.of(Configuration.REPORT_FILE, "target/report.json")));
        Listener xml =
                Listener.defaultListener(Configuration.of(Map.of(Configuration.REPORT_FILE, "target/report.xml")));
        Listener html =
                Listener.defaultListener(Configuration.of(Map.of(Configuration.REPORT_FILE, "target/report.html")));
        Listener log =
                Listener.defaultListener(Configuration.of(Map.of(Configuration.REPORT_FILE, "target/report.log")));
        Listener txt =
                Listener.defaultListener(Configuration.of(Map.of(Configuration.REPORT_FILE, "target/report.txt")));
        Listener upper =
                Listener.defaultListener(Configuration.of(Map.of(Configuration.REPORT_FILE, "target/REPORT.JSON")));

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
        Listener unknown =
                Listener.defaultListener(Configuration.of(Map.of(Configuration.REPORT_FILE, "target/report.yaml")));
        Listener missing =
                Listener.defaultListener(Configuration.of(Map.of(Configuration.REPORT_FILE, "target/report")));

        assertThat(extractListeners(unknown)).anyMatch(ReportListener.class::isInstance);
        assertThat(extractListeners(missing)).anyMatch(ReportListener.class::isInstance);
    }

    @Test
    @DisplayName("defaultListener(configuration) writes text to supplied filename for unknown extension")
    void defaultListenerWritesTextToSuppliedFilenameForUnknownExtension() throws Exception {
        Path reportFile = tempDir.resolve("report.yaml");
        Runner runner = Runner.builder()
                .listener(Listener.defaultListener(
                        Configuration.of(Map.of(Configuration.REPORT_FILE, reportFile.toString()))))
                .build();

        runner.run(Step.of("unknown-extension-action", context -> {}));

        assertThat(reportFile).exists();
        String report = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(report).contains("unknown-extension-action");
        assertThat(report).contains("PASSED");
        assertThat(report).doesNotStartWith("{");
    }

    @SuppressWarnings("unchecked")
    private static List<Listener> extractListeners(final Listener listener) throws Exception {
        Field delegateField = SafeListener.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        Object delegate = delegateField.get(listener);

        Field listenersField = CompositeListener.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);
        return (List<Listener>) listenersField.get(delegate);
    }

    @Test
    @DisplayName("defaultListener with paramixel.ansi=true forces ANSI on")
    void defaultListenerWithAnsiTrueForcesAnsi() throws Exception {
        Listener listener = Listener.defaultListener(Configuration.of(Map.of(Configuration.ANSI, "true")));

        Field ansiField = SafeListener.class.getDeclaredField("ansiEnabled");
        ansiField.setAccessible(true);
        boolean ansiEnabled = ansiField.getBoolean(listener);
        assertThat(ansiEnabled).isTrue();
    }

    @Test
    @DisplayName("defaultListener with paramixel.ansi=false forces ANSI off")
    void defaultListenerWithAnsiFalseForcesNoAnsi() throws Exception {
        Listener listener = Listener.defaultListener(Configuration.of(Map.of(Configuration.ANSI, "false")));

        Field ansiField = SafeListener.class.getDeclaredField("ansiEnabled");
        ansiField.setAccessible(true);
        boolean ansiEnabled = ansiField.getBoolean(listener);
        assertThat(ansiEnabled).isFalse();
    }

    @Test
    @DisplayName("defaultListener with paramixel.ansi=true enables ANSI in StatusListener")
    void defaultListenerWithAnsiTrueEnablesStatusListenerAnsi() throws Exception {
        Listener listener = Listener.defaultListener(Configuration.of(Map.of(Configuration.ANSI, "true")));
        listener.initialize(Configuration.of(Map.of()));

        List<Listener> inner = extractListeners(listener);
        Field ansiField = StatusListener.class.getDeclaredField("ansiEnabled");
        ansiField.setAccessible(true);
        boolean ansiEnabled = ansiField.getBoolean(inner.get(0));
        assertThat(ansiEnabled).isTrue();
    }

    @Test
    @DisplayName("defaultListener with paramixel.ansi=false disables ANSI in StatusListener")
    void defaultListenerWithAnsiFalseDisablesStatusListenerAnsi() throws Exception {
        Listener listener = Listener.defaultListener(Configuration.of(Map.of(Configuration.ANSI, "false")));
        listener.initialize(Configuration.of(Map.of()));

        List<Listener> inner = extractListeners(listener);
        Field ansiField = StatusListener.class.getDeclaredField("ansiEnabled");
        ansiField.setAccessible(true);
        boolean ansiEnabled = ansiField.getBoolean(inner.get(0));
        assertThat(ansiEnabled).isFalse();
    }

    @Test
    @DisplayName("paramixel.ansi=auto auto-detects ANSI")
    void ansiAutoAutoDetects() throws Exception {
        Listener listener = Listener.defaultListener(Configuration.of(Map.of(Configuration.ANSI, "auto")));

        Field ansiField = SafeListener.class.getDeclaredField("ansiEnabled");
        ansiField.setAccessible(true);
        boolean ansiEnabled = ansiField.getBoolean(listener);
        assertThat(ansiEnabled).isEqualTo(System.console() != null);
    }

    @Test
    @DisplayName("paramixel.ansi empty string auto-detects ANSI")
    void ansiEmptyStringAutoDetects() throws Exception {
        Listener listener = Listener.defaultListener(Configuration.of(Map.of(Configuration.ANSI, "")));

        Field ansiField = SafeListener.class.getDeclaredField("ansiEnabled");
        ansiField.setAccessible(true);
        boolean ansiEnabled = ansiField.getBoolean(listener);
        assertThat(ansiEnabled).isEqualTo(System.console() != null);
    }

    @Test
    @DisplayName("paramixel.ansi absent auto-detects ANSI")
    void ansiAbsentAutoDetects() throws Exception {
        Listener listener = Listener.defaultListener(Configuration.of(Map.of()));

        Field ansiField = SafeListener.class.getDeclaredField("ansiEnabled");
        ansiField.setAccessible(true);
        boolean ansiEnabled = ansiField.getBoolean(listener);
        assertThat(ansiEnabled).isEqualTo(System.console() != null);
    }
}

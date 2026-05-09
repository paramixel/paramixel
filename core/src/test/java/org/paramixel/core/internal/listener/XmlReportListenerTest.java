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

package org.paramixel.core.internal.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Parallel;

@DisplayName("XmlReportListener")
class XmlReportListenerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("constructor rejects null and blank reportFile")
    void constructorRejectsNullAndBlankReportFile() {
        assertThatThrownBy(() -> new XmlReportListener((String) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("reportFile must not be null");
        assertThatThrownBy(() -> new XmlReportListener(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reportFile must not be blank");
        assertThatThrownBy(() -> new XmlReportListener("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reportFile must not be blank");
    }

    @Test
    @DisplayName("runCompleted writes XML report file")
    void runCompletedWritesXmlReportFile() throws Exception {
        Path reportFile = tempDir.resolve("paramixel.xml");
        Runner runner = Runner.builder()
                .listener(new XmlReportListener(reportFile.toString()))
                .build();

        runner.run(Noop.of("xml-action"));

        String report = Files.readString(reportFile, StandardCharsets.UTF_8);

        assertThat(reportFile).exists();
        assertThat(report).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(report).contains("<paramixel version=");
        assertThat(report).contains("name=\"xml-action\"");
        assertThat(report).contains("status=\"PASS\"");
        assertThat(report).contains("kind=\"Noop\"");
        assertThat(report).contains("runDuration=\"");
        assertThat(report).contains("</result>");
        assertThat(report).contains("</paramixel>");
    }

    @Test
    @DisplayName("runCompleted writes XML report with Parallel root from resolver")
    void runCompletedWritesXmlReportWithParallelRoot() throws Exception {
        Path reportFile = tempDir.resolve("paramixel.xml");
        Parallel parallel = Parallel.builder(Constants.ROOT_NAME)
                .parallelism(1)
                .child(Noop.of("child-action"))
                .build();
        Runner runner = Runner.builder()
                .listener(new XmlReportListener(reportFile.toString()))
                .build();

        runner.run(parallel);

        String report = Files.readString(reportFile, StandardCharsets.UTF_8);

        assertThat(report).contains("name=\"Paramixel v");
        assertThat(report).contains("kind=\"Parallel\"");
        assertThat(report).contains("name=\"child-action\"");
        assertThat(report).contains("<children>");
    }

    @Test
    @DisplayName("runCompleted throws UncheckedIOException when file cannot be written")
    void runCompletedThrowsUncheckedIOExceptionWhenFileCannotBeWritten() {
        Path readOnlyDir = tempDir.resolve("readonly-unwrapped");
        readOnlyDir.toFile().mkdirs();
        readOnlyDir.toFile().setReadOnly();
        Path reportFile = readOnlyDir.resolve("nested/paramixel.xml");

        XmlReportListener listener = new XmlReportListener(reportFile.toString());
        Runner runner = Runner.builder().build();
        Result result = runner.run(Noop.of("xml-unwrap-action"));

        assertThatThrownBy(() -> listener.runCompleted(runner, result))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Unable to write XML report file")
                .hasCauseInstanceOf(java.io.IOException.class);
    }
}

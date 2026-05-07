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

package org.paramixel.core.spi.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Noop;

@DisplayName("ReportListener")
class ReportListenerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("constructor rejects null and blank reportFile")
    void constructorRejectsNullAndBlankReportFile() {
        assertThatThrownBy(() -> new ReportListener((String) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("reportFile must not be null");
        assertThatThrownBy(() -> new ReportListener(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reportFile must not be blank");
        assertThatThrownBy(() -> new ReportListener("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reportFile must not be blank");
    }

    @Test
    @DisplayName("runCompleted writes plain text summary report file without prefix")
    void runCompletedWritesPlainTextSummaryReportFile() throws Exception {
        Path reportFile = tempDir.resolve("paramixel.log");
        Runner runner = Runner.builder()
                .listener(new ReportListener(reportFile.toString()))
                .build();

        runner.run(Noop.of("report-action"));

        String report = Files.readString(reportFile, StandardCharsets.UTF_8);

        assertThat(reportFile).exists();
        assertThat(report).doesNotContain("[PARAMIXEL]");
        assertThat(report).doesNotContain("\u001B[");
        assertThat(report).contains("Paramixel v");
        assertThat(report).contains("report-action");
        assertThat(report).contains("Status      : PASS");
        assertThat(report).contains("Finished at :");
        assertThat(report).contains("Total time  :");
        assertThat(report).doesNotContain("RUN  |");
        assertThat(report).doesNotContain("EXCEPTION |");
    }

    @Test
    @DisplayName("runCompleted creates parent directories for report file")
    void runCompletedCreatesParentDirectoriesForReportFile() throws Exception {
        Path reportFile = tempDir.resolve("reports/nested/paramixel.log");
        Runner runner = Runner.builder()
                .listener(new ReportListener(reportFile.toString()))
                .build();

        runner.run(Noop.of("nested-report-action"));

        assertThat(reportFile).exists();
        assertThat(Files.readString(reportFile, StandardCharsets.UTF_8)).contains("nested-report-action");
    }

    @Test
    @DisplayName("runCompleted overwrites existing report file")
    void runCompletedOverwritesExistingReportFile() throws Exception {
        Path reportFile = tempDir.resolve("paramixel.log");
        Files.writeString(reportFile, "old content", StandardCharsets.UTF_8);
        Runner runner = Runner.builder()
                .listener(new ReportListener(reportFile.toString()))
                .build();

        runner.run(Noop.of("overwrite-report-action"));

        String report = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(report).contains("overwrite-report-action");
        assertThat(report).doesNotContain("old content");
    }
}

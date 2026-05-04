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
    @DisplayName("runCompleted writes plain text summary report file")
    void runCompletedWritesPlainTextSummaryReportFile() throws Exception {
        Runner runner = Runner.builder()
                .listener(new ReportListener(tempDir.toString()))
                .build();

        runner.run(Noop.of("report-action"));

        Path reportFile = Files.list(tempDir).findFirst().orElseThrow();
        String report = Files.readString(reportFile, StandardCharsets.UTF_8);

        assertThat(reportFile.getFileName().toString()).matches("paramixel_\\d{8}-\\d{6}\\.log");
        assertThat(report).contains(Constants.PARAMIXEL_PLAIN);
        assertThat(report).contains("report-action");
        assertThat(report).contains("Status      : PASS");
        assertThat(report).doesNotContain("\u001B[");
        assertThat(report).doesNotContain("RUN  |");
        assertThat(report).doesNotContain("EXCEPTION |");
    }
}

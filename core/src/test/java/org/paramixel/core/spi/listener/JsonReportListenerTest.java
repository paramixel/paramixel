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
import org.paramixel.core.action.Parallel;

@DisplayName("JsonReportListener")
class JsonReportListenerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("constructor rejects null and blank reportFile")
    void constructorRejectsNullAndBlankReportFile() {
        assertThatThrownBy(() -> new JsonReportListener((String) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("reportFile must not be null");
        assertThatThrownBy(() -> new JsonReportListener(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reportFile must not be blank");
        assertThatThrownBy(() -> new JsonReportListener("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reportFile must not be blank");
    }

    @Test
    @DisplayName("runCompleted writes JSON report file")
    void runCompletedWritesJsonReportFile() throws Exception {
        Path reportFile = tempDir.resolve("paramixel.json");
        Runner runner = Runner.builder()
                .listener(new JsonReportListener(reportFile.toString()))
                .build();

        runner.run(Noop.of("json-action"));

        String report = Files.readString(reportFile, StandardCharsets.UTF_8);

        assertThat(reportFile).exists();
        assertThat(report).startsWith("{\n");
        assertThat(report).contains("\"name\": \"json-action\"");
        assertThat(report).contains("\"status\": \"PASS\"");
        assertThat(report).contains("\"kind\": \"Noop\"");
        assertThat(report).contains("\"runDuration\":");
        assertThat(report).contains("\"children\": []");
        assertThat(report).contains("\"version\":");
    }

    @Test
    @DisplayName("runCompleted writes JSON report with Parallel root from resolver")
    void runCompletedWritesJsonReportWithParallelRoot() throws Exception {
        Path reportFile = tempDir.resolve("paramixel.json");
        Parallel parallel = Parallel.builder(Constants.ROOT_NAME)
                .parallelism(1)
                .child(Noop.of("child-action"))
                .build();
        Runner runner = Runner.builder()
                .listener(new JsonReportListener(reportFile.toString()))
                .build();

        runner.run(parallel);

        String report = Files.readString(reportFile, StandardCharsets.UTF_8);

        assertThat(report).contains("\"name\": \"Paramixel v");
        assertThat(report).contains("\"kind\": \"Parallel\"");
        assertThat(report).contains("\"name\": \"child-action\"");
    }
}

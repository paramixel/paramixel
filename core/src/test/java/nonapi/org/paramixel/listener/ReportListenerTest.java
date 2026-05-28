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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import nonapi.org.paramixel.ConcreteConfiguration;
import nonapi.org.paramixel.ConcreteResult;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.api.Configuration;
import org.paramixel.api.Result;
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;

@DisplayName("ReportListener")
class ReportListenerTest {

    @TempDir
    Path tempDir;

    private ReportListener createListener(String reportFilePath) {
        var listener = new ReportListener();
        var config = new ConcreteConfiguration(Map.of(Configuration.REPORT_FILE, reportFilePath));
        listener.initialize(config);
        return listener;
    }

    @Test
    @DisplayName("writes no-tests message when result has no descriptor")
    void writesNoTestsMessageWhenNoDescriptor() throws Exception {
        Path reportFile = tempDir.resolve("report.txt");
        var listener = createListener(reportFile.toString());
        Result result = new ConcreteResult(Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        assertThat(reportFile).exists();
        String content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).isEqualTo("No Paramixel tests found" + System.lineSeparator());
    }

    @Test
    @DisplayName("writes descriptor tree with indentation and sanitized message")
    void writesDescriptorTreeWithIndentationAndSanitizedMessage() throws Exception {
        Path reportFile = tempDir.resolve("report.txt");
        var listener = createListener(reportFile.toString());

        var root = new ConcreteDescriptor(Step.of("root", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);

        var child = new ConcreteDescriptor(Step.of("child", v -> {}));
        child.setStatus(Status.RUNNING);
        child.setStatus(Status.failed("first\n  second\tthird"));

        root.addChild(child);

        Result result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        String content = Files.readString(reportFile, StandardCharsets.UTF_8);
        var lineSeparator = System.lineSeparator();
        assertThat(content)
                .contains("PASSED | root" + lineSeparator + "  FAILED | child | first - second third" + lineSeparator);
    }

    @Test
    @DisplayName("throws UncheckedIOException with text format label when report path is a directory")
    void throwsUncheckedIOExceptionWhenReportPathIsDirectory() throws Exception {
        Path dir = tempDir.resolve("blocked.txt");
        Files.createDirectory(dir);
        var listener = createListener(dir.toString());
        Result result = new ConcreteResult(Configuration.defaultConfiguration());

        assertThatThrownBy(() -> listener.onRunCompleted(result))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("text report file");
    }
}

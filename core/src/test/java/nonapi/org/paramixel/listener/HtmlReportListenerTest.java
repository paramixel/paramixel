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

@DisplayName("HtmlReportListener")
class HtmlReportListenerTest {

    @TempDir
    Path tempDir;

    private HtmlReportListener createListener(String reportFilePath) {
        var listener = new HtmlReportListener();
        var config = new ConcreteConfiguration(Map.of(Configuration.REPORT_FILE, reportFilePath));
        listener.initialize(config);
        return listener;
    }

    @Test
    @DisplayName("writes no-tests message when result has no descriptor")
    void writesNoTestsMessageWhenNoDescriptor() throws Exception {
        Path reportFile = tempDir.resolve("report.html");
        var listener = createListener(reportFile.toString());
        Result result = new ConcreteResult(Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        assertThat(reportFile).exists();
        String content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("<!DOCTYPE html>");
        assertThat(content).contains("No Paramixel tests found");
        assertThat(content).contains("</html>");
    }

    @Test
    @DisplayName("writes root descriptor status and name")
    void writesRootDescriptor() throws Exception {
        Path reportFile = tempDir.resolve("report.html");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("root-action", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        Result result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        String content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("<!DOCTYPE html>");
        assertThat(content).contains("PASSED");
        assertThat(content).contains("root-action");
        assertThat(content).contains("</html>");
    }

    @Test
    @DisplayName("writes descriptor tree with indented children")
    void writesDescriptorTreeWithChildren() throws Exception {
        Path reportFile = tempDir.resolve("report.html");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("parent", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var child1 = new ConcreteDescriptor(Step.of("child-a", v -> {}));
        child1.setStatus(Status.RUNNING);
        child1.setStatus(Status.PASSED);
        var child2 = new ConcreteDescriptor(Step.of("child-b", v -> {}));
        child2.setStatus(Status.RUNNING);
        child2.setStatus(Status.ABORTED);
        root.addChild(child1);
        root.addChild(child2);
        Result result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        String content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("parent");
        assertThat(content).contains("child-a");
        assertThat(content).contains("child-b");
        assertThat(content).contains("ABORTED");
    }

    @Test
    @DisplayName("renders hierarchical indentation consistently")
    void rendersHierarchicalIndentationConsistently() throws Exception {
        Path reportFile = tempDir.resolve("report.html");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("root", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var child = new ConcreteDescriptor(Step.of("child", v -> {}));
        child.setStatus(Status.RUNNING);
        child.setStatus(Status.PASSED);
        var grandchild = new ConcreteDescriptor(Step.of("grandchild", v -> {}));
        grandchild.setStatus(Status.RUNNING);
        grandchild.setStatus(Status.FAILED);
        child.addChild(grandchild);
        root.addChild(child);
        Result result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        String content = Files.readString(reportFile, StandardCharsets.UTF_8);
        var lineSeparator = System.lineSeparator();
        assertThat(content)
                .contains("PASSED | root" + lineSeparator + "  PASSED | child" + lineSeparator
                        + "    FAILED | grandchild" + lineSeparator);
    }

    @Test
    @DisplayName("escapes HTML special characters in descriptor names")
    void escapesHtmlSpecialCharacters() throws Exception {
        Path reportFile = tempDir.resolve("report.html");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("a&b<c>d", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        Result result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        String content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("a&amp;b&lt;c&gt;d");
        assertThat(content).doesNotContain("a&b<c>d");
    }

    @Test
    @DisplayName("creates parent directories for report file")
    void createsParentDirectories() throws Exception {
        Path reportFile = tempDir.resolve("deep/nested/dir/report.html");
        var listener = createListener(reportFile.toString());
        Result result = new ConcreteResult(Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        assertThat(reportFile).exists();
    }

    @Test
    @DisplayName("throws UncheckedIOException when report file path is a directory")
    void throwsUncheckedIOExceptionWhenPathIsDirectory() throws Exception {
        Path dir = tempDir.resolve("blocked.html");
        Files.createDirectory(dir);
        var listener = createListener(dir.toString());
        Result result = new ConcreteResult(Configuration.defaultConfiguration());

        assertThatThrownBy(() -> listener.onRunCompleted(result))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("html report file");
    }
}

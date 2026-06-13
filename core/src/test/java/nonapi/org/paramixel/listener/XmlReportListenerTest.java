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
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;

@DisplayName("XmlReportListener")
class XmlReportListenerTest {

    @TempDir
    Path tempDir;

    private XmlReportListener createListener(String reportFilePath) {
        var listener = new XmlReportListener();
        var configuration = new ConcreteConfiguration(Map.of(Configuration.REPORT_FILE, reportFilePath));
        listener.initialize(configuration);
        return listener;
    }

    @Test
    @DisplayName("writes self-closing tag when result has no descriptor")
    void writesSelfClosingTagWhenNoDescriptor() throws Exception {
        var reportFile = tempDir.resolve("report.xml");
        var listener = createListener(reportFile.toString());
        var result = new ConcreteResult(Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        assertThat(reportFile).exists();
        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(content).contains("<paramixel/>");
    }

    @Test
    @DisplayName("writes root descriptor with id, name, and status")
    void writesRootDescriptor() throws Exception {
        var reportFile = tempDir.resolve("report.xml");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("root-action", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(content).contains("name=\"root-action\"");
        assertThat(content).contains("status=\"PASSED\"");
        assertThat(content).contains("<descriptor ");
        assertThat(content).contains("</descriptor>");
    }

    @Test
    @DisplayName("writes descriptor tree with children")
    void writesDescriptorTreeWithChildren() throws Exception {
        var reportFile = tempDir.resolve("report.xml");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("parent", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var child1 = new ConcreteDescriptor(Step.of("child-a", v -> {}));
        child1.setStatus(Status.RUNNING);
        child1.setStatus(Status.PASSED);
        var child2 = new ConcreteDescriptor(Step.of("child-b", v -> {}));
        child2.setStatus(Status.RUNNING);
        child2.setStatus(Status.FAILED);
        root.addChild(child1);
        root.addChild(child2);
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("name=\"parent\"");
        assertThat(content).contains("name=\"child-a\"");
        assertThat(content).contains("status=\"PASSED\"");
        assertThat(content).contains("name=\"child-b\"");
        assertThat(content).contains("status=\"FAILED\"");
    }

    @Test
    @DisplayName("renders nested descriptors with consistent indentation")
    void rendersNestedDescriptorsWithConsistentIndentation() throws Exception {
        var reportFile = tempDir.resolve("report.xml");
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
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        var lineSeparator = System.lineSeparator();
        assertThat(content).contains(lineSeparator + "<descriptor id=\"");
        assertThat(content).contains(lineSeparator + "  <descriptor id=\"");
        assertThat(content).contains(lineSeparator + "    <descriptor id=\"");
        assertThat(content)
                .contains(lineSeparator + "    </descriptor>" + lineSeparator + "  </descriptor>" + lineSeparator
                        + "</descriptor>" + lineSeparator);
    }

    @Test
    @DisplayName("escapes XML special characters in descriptor names")
    void escapesXmlSpecialCharacters() throws Exception {
        var reportFile = tempDir.resolve("report.xml");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("a&b<c>d\"e", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("a&amp;b&lt;c&gt;d&quot;e");
        assertThat(content).doesNotContain("name=\"a&b<c>d\"e\"");
    }

    @Test
    @DisplayName("strips illegal XML control characters from descriptor names")
    void stripsIllegalXmlControlCharacters() throws Exception {
        var reportFile = tempDir.resolve("report.xml");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("test\u0001\u0008\u000b\u000c\u001fvalue", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("testvalue");
        assertThat(content).doesNotContain("\u0001");
        assertThat(content).doesNotContain("\u0008");
        assertThat(content).doesNotContain("\u000b");
        assertThat(content).doesNotContain("\u000c");
        assertThat(content).doesNotContain("\u001f");
    }

    @Test
    @DisplayName("preserves legal XML control characters tab newline and carriage return")
    void preservesLegalXmlControlCharacters() throws Exception {
        var reportFile = tempDir.resolve("report.xml");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("a\tb\nc\rd", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("a\tb\nc\rd");
    }

    @Test
    @DisplayName("escapes single quote in descriptor names")
    void escapesSingleQuote() throws Exception {
        var reportFile = tempDir.resolve("report.xml");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("it's", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("it&apos;s");
        assertThat(content).doesNotContain("name=\"it's\"");
    }

    @Test
    @DisplayName("escapes mixed content with entities legal and illegal control characters")
    void escapesMixedContentWithControlChars() throws Exception {
        var reportFile = tempDir.resolve("report.xml");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("a&b<c\u0001d\te", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("a&amp;b&lt;cd\te");
        assertThat(content).doesNotContain("\u0001");
    }

    @Test
    @DisplayName("creates parent directories for report file")
    void createsParentDirectories() throws Exception {
        var reportFile = tempDir.resolve("deep/nested/dir/report.xml");
        var listener = createListener(reportFile.toString());
        var result = new ConcreteResult(Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        assertThat(reportFile).exists();
    }

    @Test
    @DisplayName("throws UncheckedIOException when report file path is a directory")
    void throwsUncheckedIOExceptionWhenPathIsDirectory() throws Exception {
        var dir = tempDir.resolve("blocked.xml");
        Files.createDirectory(dir);
        var listener = createListener(dir.toString());
        var result = new ConcreteResult(Configuration.defaultConfiguration());

        assertThatThrownBy(() -> listener.onRunCompleted(result))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("xml report file");
    }
}

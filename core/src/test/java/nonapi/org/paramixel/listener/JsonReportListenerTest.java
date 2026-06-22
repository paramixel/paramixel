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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.api.Configuration;
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;

@DisplayName("JsonReportListener")
class JsonReportListenerTest {

    @TempDir
    Path tempDir;

    private JsonReportListener createListener(String reportFilePath) {
        var listener = new JsonReportListener();
        var configuration = new ConcreteConfiguration(Map.of(Configuration.REPORT_FILE, reportFilePath));
        listener.initialize(configuration);
        return listener;
    }

    @Test
    @DisplayName("writes null root when result has no descriptor")
    void writesNullRootWhenNoDescriptor() throws Exception {
        var reportFile = tempDir.resolve("report.json");
        var listener = createListener(reportFile.toString());
        var result = new ConcreteResult(Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        assertThat(reportFile).exists();
        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("{\"root\":null}");
    }

    @Test
    @DisplayName("writes root descriptor with id, name, status, and children")
    void writesRootDescriptor() throws Exception {
        var reportFile = tempDir.resolve("report.json");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("root-action", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"name\":\"root-action\"");
        assertThat(content).contains("\"status\":\"PASSED\"");
        assertThat(content).contains("\"children\":[]");
    }

    @Test
    @DisplayName("writes descriptor tree with comma-separated children")
    void writesDescriptorTreeWithChildren() throws Exception {
        var reportFile = tempDir.resolve("report.json");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("parent", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var child1 = new ConcreteDescriptor(Step.of("child-a", v -> {}));
        child1.setStatus(Status.RUNNING);
        child1.setStatus(Status.PASSED);
        var child2 = new ConcreteDescriptor(Step.of("child-b", v -> {}));
        child2.setStatus(Status.RUNNING);
        child2.setStatus(Status.SKIPPED);
        root.addChild(child1);
        root.addChild(child2);
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"name\":\"parent\"");
        assertThat(content).contains("\"name\":\"child-a\"");
        assertThat(content).contains("\"name\":\"child-b\"");
        assertThat(content).contains("\"status\":\"SKIPPED\"");
    }

    @Test
    @DisplayName("escapes JSON special characters in descriptor names")
    void escapesJsonSpecialCharacters() throws Exception {
        var reportFile = tempDir.resolve("report.json");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("a\\b\"c", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("a\\\\b\\\"c");
        assertThat(content).doesNotContain("\"name\":\"a\\b\"c\"");
    }

    @Test
    @DisplayName("encodes unsupported control characters as unicode escapes")
    void encodesUnsupportedControlCharactersAsUnicodeEscapes() throws Exception {
        var reportFile = tempDir.resolve("report.json");
        var listener = createListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("a\u0001b\u001fc", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        var content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"name\":\"a\\u0001b\\u001fc\"");
        assertThat(content).doesNotContain("\u0001");
        assertThat(content).doesNotContain("\u001f");
    }

    @Test
    @DisplayName("creates parent directories for report file")
    void createsParentDirectories() throws Exception {
        var reportFile = tempDir.resolve("deep/nested/dir/report.json");
        var listener = createListener(reportFile.toString());
        var result = new ConcreteResult(Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        assertThat(reportFile).exists();
    }

    @Test
    @DisplayName("throws UncheckedIOException when report file path is a directory")
    void throwsUncheckedIOExceptionWhenPathIsDirectory() throws Exception {
        var dir = tempDir.resolve("blocked.json");
        Files.createDirectory(dir);
        var listener = createListener(dir.toString());
        var result = new ConcreteResult(Configuration.defaultConfiguration());

        assertThatThrownBy(() -> listener.onRunCompleted(result))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("json report file");
    }

    private String reportContent(ConcreteDescriptor root) throws Exception {
        var reportFile = tempDir.resolve("report.json");
        var listener = createListener(reportFile.toString());
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        var result = new ConcreteResult(root, Configuration.defaultConfiguration());
        listener.onRunCompleted(result);
        return Files.readString(reportFile, StandardCharsets.UTF_8);
    }

    // Returns whether the descriptor containing nameField is preceded by a comma.
    private static boolean precededByComma(String content, String nameField) {
        int nameIndex = content.indexOf(nameField);
        int objectStart = content.lastIndexOf("{\"id\"", nameIndex);
        return objectStart > 0 && content.charAt(objectStart - 1) == ',';
    }

    @Nested
    @DisplayName("JSON escaping")
    class JsonEscaping {

        @Test
        @DisplayName("escapes named whitespace escapes as short forms")
        void escapesNamedWhitespaceAsShortForms() throws Exception {
            var content = reportContent(new ConcreteDescriptor(Step.of("a\nb\rc\td\be\ff", v -> {})));

            assertThat(content).contains("\"name\":\"a\\nb\\rc\\td\\be\\ff\"");
        }

        @Test
        @DisplayName("detects quote as first escaped character")
        void detectsQuoteAsFirstEscapedCharacter() throws Exception {
            var content = reportContent(new ConcreteDescriptor(Step.of("a\"b", v -> {})));

            assertThat(content).contains("\"name\":\"a\\\"b\"");
        }
    }

    @Nested
    @DisplayName("descriptor tree slots")
    class DescriptorTreeSlots {

        @Test
        @DisplayName("writes before, child, and after slots with separating commas")
        void writesBeforeChildAndAfterWithSeparatingCommas() throws Exception {
            var root = new ConcreteDescriptor(Step.of("root", v -> {}));
            root.setBefore(new ConcreteDescriptor(Step.of("before", v -> {})));
            root.addChild(new ConcreteDescriptor(Step.of("child", v -> {})));
            root.setAfter(new ConcreteDescriptor(Step.of("after", v -> {})));

            var content = reportContent(root);

            assertThat(content).containsSubsequence("\"name\":\"before\"", "\"name\":\"child\"", "\"name\":\"after\"");
            assertThat(precededByComma(content, "\"name\":\"before\"")).isFalse();
            assertThat(precededByComma(content, "\"name\":\"child\"")).isTrue();
            assertThat(precededByComma(content, "\"name\":\"after\"")).isTrue();
        }

        @Test
        @DisplayName("writes lone after slot without leading comma")
        void writesLoneAfterWithoutLeadingComma() throws Exception {
            var root = new ConcreteDescriptor(Step.of("root", v -> {}));
            root.setAfter(new ConcreteDescriptor(Step.of("after", v -> {})));

            var content = reportContent(root);

            assertThat(content).contains("\"name\":\"after\"");
            assertThat(precededByComma(content, "\"name\":\"after\"")).isFalse();
        }
    }
}

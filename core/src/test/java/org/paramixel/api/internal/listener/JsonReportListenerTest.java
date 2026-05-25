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

package org.paramixel.api.internal.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.api.Configuration;
import org.paramixel.api.Result;
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;
import org.paramixel.api.internal.ConcreteResult;
import org.paramixel.api.internal.action.ConcreteDescriptor;

@DisplayName("JsonReportListener")
class JsonReportListenerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("writes null root when result has no descriptor")
    void writesNullRootWhenNoDescriptor() throws Exception {
        Path reportFile = tempDir.resolve("report.json");
        var listener = new JsonReportListener(reportFile.toString());
        Result result = new ConcreteResult(Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        assertThat(reportFile).exists();
        String content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("{\"root\":null}");
    }

    @Test
    @DisplayName("writes root descriptor with id, name, status, and children")
    void writesRootDescriptor() throws Exception {
        Path reportFile = tempDir.resolve("report.json");
        var listener = new JsonReportListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("root-action", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        Result result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        String content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"name\":\"root-action\"");
        assertThat(content).contains("\"status\":\"PASSED\"");
        assertThat(content).contains("\"children\":[]");
    }

    @Test
    @DisplayName("writes descriptor tree with comma-separated children")
    void writesDescriptorTreeWithChildren() throws Exception {
        Path reportFile = tempDir.resolve("report.json");
        var listener = new JsonReportListener(reportFile.toString());
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
        Result result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        String content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"name\":\"parent\"");
        assertThat(content).contains("\"name\":\"child-a\"");
        assertThat(content).contains("\"name\":\"child-b\"");
        assertThat(content).contains("\"status\":\"SKIPPED\"");
    }

    @Test
    @DisplayName("escapes JSON special characters in descriptor names")
    void escapesJsonSpecialCharacters() throws Exception {
        Path reportFile = tempDir.resolve("report.json");
        var listener = new JsonReportListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("a\\b\"c", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        Result result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        String content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("a\\\\b\\\"c");
        assertThat(content).doesNotContain("\"name\":\"a\\b\"c\"");
    }

    @Test
    @DisplayName("encodes unsupported control characters as unicode escapes")
    void encodesUnsupportedControlCharactersAsUnicodeEscapes() throws Exception {
        Path reportFile = tempDir.resolve("report.json");
        var listener = new JsonReportListener(reportFile.toString());
        var root = new ConcreteDescriptor(Step.of("a\u0001b\u001fc", v -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);
        Result result = new ConcreteResult(root, Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        String content = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"name\":\"a\\u0001b\\u001fc\"");
        assertThat(content).doesNotContain("\u0001");
        assertThat(content).doesNotContain("\u001f");
    }

    @Test
    @DisplayName("creates parent directories for report file")
    void createsParentDirectories() throws Exception {
        Path reportFile = tempDir.resolve("deep/nested/dir/report.json");
        var listener = new JsonReportListener(reportFile.toString());
        Result result = new ConcreteResult(Configuration.defaultConfiguration());

        listener.onRunCompleted(result);

        assertThat(reportFile).exists();
    }

    @Test
    @DisplayName("throws UncheckedIOException when report file path is a directory")
    void throwsUncheckedIOExceptionWhenPathIsDirectory() throws Exception {
        Path dir = tempDir.resolve("blocked.json");
        Files.createDirectory(dir);
        var listener = new JsonReportListener(dir.toString());
        Result result = new ConcreteResult(Configuration.defaultConfiguration());

        assertThatThrownBy(() -> listener.onRunCompleted(result))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("json report file");
    }
}

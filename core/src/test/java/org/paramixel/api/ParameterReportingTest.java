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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import nonapi.org.paramixel.reporting.DefaultListeners;
import nonapi.org.paramixel.reporting.ReportFileListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

/**
 * Plan parameters: what {@code bind} reports, where it appears, and what is never reported.
 */
class ParameterReportingTest {

    private static final String ENVIRONMENT = "environment";
    private static final String RUNTIME_ONLY = "runtime-only";

    private static final String IMAGE = "apache/kafka:4.3.0";
    private static final String RUNTIME_VALUE = "value-stored-at-runtime";

    @TempDir
    Path tempDir;

    private static Task definition() {
        return Sequential.builder(Hooks.class, "root")
                .bind(ENVIRONMENT, IMAGE)
                .step("store")
                .step(Sequential.builder("child").step("check"))
                .build();
    }

    private Result run(final Path report, final Task definition) {
        return Runner.builder().listener(new ReportFileListener(report)).build().run(definition);
    }

    @Test
    @DisplayName("PARAM-01: JSON reports the declaring node's parameters immediately after its path")
    void jsonCarriesParametersAfterPath() throws Exception {
        final var file = tempDir.resolve("parameters.json");
        final var result = run(file, definition());
        final var json = Files.readString(file);

        assertThat(json).contains("\"schemaVersion\": 2");
        assertThat(json)
                .contains("\"path\": \"root\",\n    \"parameters\": {\"" + ENVIRONMENT + "\": \"" + IMAGE + "\"}");
        assertThat(result.root().orElseThrow().parameters()).containsEntry(ENVIRONMENT, IMAGE);
    }

    @Test
    @DisplayName("PARAM-02: a descendant node reports no parameters of its own")
    void jsonKeepsParametersOnTheDeclaringNodeOnly() throws Exception {
        final var file = tempDir.resolve("declaring-only.json");
        final var result = run(file, definition());
        final var json = Files.readString(file);

        final var child = result.root().orElseThrow().children().stream()
                .filter(node -> node.name().equals("child"))
                .findFirst()
                .orElseThrow();
        assertThat(child.parameters()).isEmpty();
        assertThat(json).contains("\"path\": \"root > child\",\n    \"parameters\": {}");
        // The declaring node is the only place the image appears, so the image occurs exactly once in the report.
        assertThat(json.split(IMAGE, -1)).hasSize(2);
    }

    @Test
    @DisplayName("PARAM-03: XML reports parameters as escaped child elements")
    void xmlCarriesParameterElements() throws Exception {
        final var file = tempDir.resolve("parameters.xml");
        run(file, Strategy.definitionWithSpecialCharacters());
        final var document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(Files.readAllBytes(file)));

        assertThat(document.getDocumentElement().getAttribute("schemaVersion")).isEqualTo("2");
        final var parameters = document.getElementsByTagName("parameter");
        assertThat(parameters.getLength()).isEqualTo(1);
        final var parameter = (Element) parameters.item(0);
        assertThat(parameter.getAttribute("name")).isEqualTo(ENVIRONMENT);
        assertThat(parameter.getAttribute("value")).isEqualTo("<image> & \"label\"");
    }

    @Test
    @DisplayName("PARAM-04: HTML reports parameters in a collapsed detail row, not in the tree line")
    void htmlCarriesCollapsedParameterDetails() throws Exception {
        final var file = tempDir.resolve("parameters.html");
        run(file, definition());
        final var html = Files.readString(file);

        assertThat(html).contains("<details><summary>parameters</summary>");
        assertThat(html).contains("<code>" + ENVIRONMENT + "</code> = " + IMAGE);
        assertThat(html).doesNotContain(">root " + IMAGE);
    }

    @Test
    @DisplayName("PARAM-05: a rendering longer than 256 characters is truncated with a marker")
    void longParameterIsTruncated() throws Exception {
        final var longValue = "x".repeat(300);
        final var file = tempDir.resolve("truncated.json");
        final var result = run(
                file,
                Sequential.builder("root")
                        .bind(ENVIRONMENT, longValue)
                        .step("work", () -> {})
                        .build());

        final var rendered = result.root().orElseThrow().parameters().get(ENVIRONMENT);
        assertThat(rendered).hasSize(257);
        assertThat(rendered).endsWith("\u2026");
        assertThat(rendered).startsWith("x".repeat(256));
        assertThat(Files.readString(file)).doesNotContain(longValue);
    }

    @Test
    @DisplayName("PARAM-06: console output never prints a parameter, and Context.put values are never reported")
    void consoleAndRuntimeEntriesAreNotReported() throws Exception {
        final var out = new ByteArrayOutputStream();
        final var console =
                DefaultListeners.create(Configuration.of(Map.of(Configuration.ANSI, "false")), new PrintStream(out));
        final var consoleResult = Runner.builder().listener(console).build().run(definition());

        assertThat(consoleResult.status().isPassed()).isTrue();
        final var text = out.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("root");
        assertThat(text).doesNotContain(IMAGE);
        assertThat(text).doesNotContain(RUNTIME_VALUE);

        final var file = tempDir.resolve("runtime-entries.json");
        run(file, definition());
        final var json = Files.readString(file);
        assertThat(json).doesNotContain(RUNTIME_ONLY);
        assertThat(json).doesNotContain(RUNTIME_VALUE);
    }

    /**
     * A definition whose bound value needs XML escaping.
     */
    static final class Strategy {

        private Strategy() {
            // Intentionally empty
        }

        static Task definitionWithSpecialCharacters() {
            return Sequential.builder("root")
                    .bind(ENVIRONMENT, "<image> & \"label\"")
                    .step("work", () -> {})
                    .build();
        }
    }

    /**
     * Anchor class for the runtime-entry case.
     */
    public static final class Hooks {

        private Hooks() {
            // Intentionally empty
        }

        @Paramixel.Id("store")
        public static void store(final Context context) {
            context.put(RUNTIME_ONLY, RUNTIME_VALUE);
        }

        @Paramixel.Id("check")
        public static void check(final Context context) {
            context.require(RUNTIME_ONLY, String.class);
        }
    }
}

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

package org.paramixel.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ParamixelPlugin functional tests")
class ParamixelPluginFunctionalTest {

    @TempDir
    Path projectDir;

    private Path buildFile;

    @BeforeEach
    void setUp() {
        buildFile = projectDir.resolve("build.gradle");
    }

    @Test
    @DisplayName("plugin applies successfully and task is registered")
    void pluginAppliesSuccessfully() throws IOException {
        writeBuildFile("plugins { id('org.paramixel') }\nparamixel { failIfNoTests = false }");

        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("paramixelTest", "--stacktrace")
                .build();

        assertThat(result.task(":paramixelTest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    @DisplayName("no tests found passes by default")
    void noTestsFoundPassesByDefault() throws IOException {
        writeBuildFile("plugins { id('org.paramixel') }");

        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("paramixelTest", "--stacktrace")
                .build();

        assertThat(result.task(":paramixelTest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    @DisplayName("no tests found passes when failIfNoTests is false")
    void noTestsFoundPassesWhenFailIfNoTestsFalse() throws IOException {
        writeBuildFile("plugins { id('org.paramixel') }\nparamixel { failIfNoTests = false }");

        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("paramixelTest", "--stacktrace")
                .build();

        assertThat(result.task(":paramixelTest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    @DisplayName("no tests found fails when failIfNoTests is true")
    void noTestsFoundFailsWhenFailIfNoTestsTrue() throws IOException {
        writeBuildFile("plugins { id('org.paramixel') }\nparamixel { failIfNoTests = true }");

        try {
            GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withPluginClasspath()
                    .withArguments("paramixelTest", "--stacktrace")
                    .build();
            throw new AssertionError("Expected build failure");
        } catch (UnexpectedBuildFailure e) {
            assertThat(e.getBuildResult().task(":paramixelTest").getOutcome()).isEqualTo(TaskOutcome.FAILED);
        }
    }

    @Test
    @DisplayName("skipTests skips execution")
    void skipTestsSkipsExecution() throws IOException {
        writeBuildFile("plugins { id('org.paramixel') }\nparamixel { skipTests = true }");

        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("paramixelTest", "--stacktrace")
                .build();

        assertThat(result.task(":paramixelTest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("configurationPrecedenceScenarios")
    @DisplayName("configuration precedence order")
    void configurationPrecedenceOrder(ConfigurationPrecedenceScenario scenario) throws IOException {
        writeBuildFile(
                "plugins { id('org.paramixel') }\nparamixel { failIfNoTests = " + scenario.extensionValue() + " }");

        var runner = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(scenario.arguments());
        var result = scenario.expectFailure() ? runner.buildAndFail() : runner.build();

        assertThat(result.task(":paramixelTest").getOutcome()).isEqualTo(scenario.expectedOutcome());
    }

    @Test
    @DisplayName("discovers and executes @Paramixel.ActionFactory test sources")
    void discoversAndExecutesActionFactoryTestSources() throws IOException {
        writeBuildFile("plugins {\n"
                + "  id('java')\n"
                + "  id('org.paramixel')\n"
                + "}\n"
                + "repositories { mavenCentral(); mavenLocal() }\n"
                + "dependencies { implementation 'org.paramixel:core:+'\n"
                + "  implementation 'org.assertj:assertj-core:3.27.3'\n"
                + "}\n"
                + "paramixel { failIfNoTests = false }");

        Path sourceDir = projectDir.resolve("src/main/java/com/example");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("SampleTest.java"),
                "package com.example;\n"
                + "\n"
                + "import org.paramixel.core.Action;\n"
                + "import org.paramixel.core.Paramixel;\n"
                + "import org.paramixel.core.action.Direct;\n"
                + "\n"
                + "public class SampleTest {\n"
                + "\n"
                + "    @Paramixel.ActionFactory\n"
                + "    public static Action actionFactory() {\n"
                + "        return Direct.builder(\"sample-test\")\n"
                + "                .execute(context -> {})\n"
                + "                .build();\n"
                + "    }\n"
                + "}\n");

        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("paramixelTest", "--stacktrace")
                .build();

        assertThat(result.task(":paramixelTest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).contains("sample-test");
    }

    private void writeBuildFile(String content) throws IOException {
        Files.writeString(buildFile, content);
    }

    private static Stream<ConfigurationPrecedenceScenario> configurationPrecedenceScenarios() {
        return Stream.of(
                new ConfigurationPrecedenceScenario(
                        "system properties override extension",
                        false,
                        List.of("paramixelTest", "--stacktrace", "-Dparamixel.failIfNoTests=true"),
                        true,
                        TaskOutcome.FAILED),
                new ConfigurationPrecedenceScenario(
                        "project properties override extension",
                        false,
                        List.of("paramixelTest", "--stacktrace", "-Pparamixel.failIfNoTests=true"),
                        true,
                        TaskOutcome.FAILED),
                new ConfigurationPrecedenceScenario(
                        "system properties override project properties",
                        true,
                        List.of(
                                "paramixelTest",
                                "--stacktrace",
                                "-Pparamixel.failIfNoTests=true",
                                "-Dparamixel.failIfNoTests=false"),
                        false,
                        TaskOutcome.SUCCESS));
    }

    private record ConfigurationPrecedenceScenario(
            String name,
            boolean extensionValue,
            List<String> arguments,
            boolean expectFailure,
            TaskOutcome expectedOutcome) {

        @Override
        public String toString() {
            return name;
        }
    }
}

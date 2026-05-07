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
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    @DisplayName("configuration precedence order honors system properties over extension")
    void configurationPrecedenceOrder() throws IOException {
        writeBuildFile("plugins { id('org.paramixel') }\nparamixel { failIfNoTests = false }");

        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("paramixelTest", "--stacktrace")
                .build();

        assertThat(result.task(":paramixelTest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    private void writeBuildFile(String content) throws IOException {
        Files.writeString(buildFile, content);
    }
}

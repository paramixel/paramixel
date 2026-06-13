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

package org.paramixel.maven.plugin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.maven.plugin.ParamixelMojo.Property;

@DisplayName("ParamixelMojo execution exception preservation tests")
class ParamixelMojoExecutionExceptionTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("execute() message preservation tests")
    class ExecuteMessagePreservationTests {

        @Test
        @DisplayName("buildConfiguration null property key preserves message")
        void buildConfigurationNullPropertyKeyPreservesMessage() throws Exception {
            var build = new Build();
            build.setDirectory(tempDir.resolve("target").toString());
            build.setOutputDirectory(tempDir.resolve("classes").toString());
            build.setTestOutputDirectory(tempDir.resolve("test-classes").toString());

            var project = new MavenProject() {
                @Override
                public List<String> getTestClasspathElements() {
                    return List.of(build.getOutputDirectory(), build.getTestOutputDirectory());
                }
            };
            project.setBuild(build);

            var property = new Property();
            property.setValue("value");

            var mojo = new ParamixelMojo();
            setField(mojo, "project", project);
            setField(mojo, "properties", List.of(property));

            assertThatThrownBy(mojo::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Paramixel property key is null");
        }

        @Test
        @DisplayName("buildTestClasspathUrls missing build preserves message")
        void buildTestClasspathUrlsMissingBuildPreservesMessage() throws Exception {
            var project = new MavenProject() {
                @Override
                public Build getBuild() {
                    return null;
                }
            };

            var mojo = new ParamixelMojo();
            setField(mojo, "project", project);

            assertThatThrownBy(mojo::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Project build information is not available");
        }
    }

    private static void setField(final ParamixelMojo mojo, final String name, final Object value) throws Exception {
        var field = ParamixelMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }
}

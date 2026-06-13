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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ParamixelMojo classpath tests")
class ParamixelMojoClasspathTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("buildTestClasspathUrls tests")
    class BuildTestClasspathUrlsTests {

        @Test
        @DisplayName("uses Maven test classpath elements exactly")
        @SuppressWarnings("unchecked")
        void usesMavenTestClasspathElementsExactly() throws Exception {
            var generatedTestOutput = tempDir.resolve("generated-test-classes").toString();
            var customTestOutput = tempDir.resolve("custom-test-classes").toString();
            var mainOutput = tempDir.resolve("classes").toString();
            var dependencyJar = tempDir.resolve("dependency.jar").toString();

            var project = new MavenProject() {
                private final List<String> testClasspath =
                        List.of(generatedTestOutput, customTestOutput, mainOutput, dependencyJar);

                @Override
                public List<String> getTestClasspathElements() {
                    return testClasspath;
                }
            };
            project.setBuild(new Build());

            var mojo = new ParamixelMojo();
            setField(mojo, "project", project);

            var urls = invokeBuildTestClasspathUrls(mojo);

            var urlPaths = urls.stream().map(URL::getPath).toList();
            assertThat(urlPaths)
                    .containsExactly(
                            new File(generatedTestOutput).toURI().toURL().getPath(),
                            new File(customTestOutput).toURI().toURL().getPath(),
                            new File(mainOutput).toURI().toURL().getPath(),
                            new File(dependencyJar).toURI().toURL().getPath());
        }

        @Test
        @DisplayName("deduplicates while preserving first occurrence")
        @SuppressWarnings("unchecked")
        void deduplicatesWhilePreservingFirstOccurrence() throws Exception {
            var a = tempDir.resolve("dir-a").toString();
            var b = tempDir.resolve("dir-b").toString();

            var project = new MavenProject() {
                private final List<String> testClasspath = List.of(a, b, a);

                @Override
                public List<String> getTestClasspathElements() {
                    return testClasspath;
                }
            };
            project.setBuild(new Build());

            var mojo = new ParamixelMojo();
            setField(mojo, "project", project);

            var urls = invokeBuildTestClasspathUrls(mojo);

            assertThat(urls).hasSize(2);
            assertThat(urls.get(0)).isEqualTo(new File(a).toURI().toURL());
            assertThat(urls.get(1)).isEqualTo(new File(b).toURI().toURL());
        }

        @Test
        @DisplayName("does not require classpath entries to exist")
        @SuppressWarnings("unchecked")
        void doesNotRequireClasspathEntriesToExist() throws Exception {
            var nonexistent = tempDir.resolve("does-not-exist-classes").toString();

            var project = new MavenProject() {
                private final List<String> testClasspath = List.of(nonexistent);

                @Override
                public List<String> getTestClasspathElements() {
                    return testClasspath;
                }
            };
            project.setBuild(new Build());

            var mojo = new ParamixelMojo();
            setField(mojo, "project", project);

            var urls = invokeBuildTestClasspathUrls(mojo);

            assertThat(urls).hasSize(1);
            assertThat(urls.get(0)).isEqualTo(new File(nonexistent).toURI().toURL());
        }

        @Test
        @DisplayName("wraps unresolved dependency errors clearly")
        @SuppressWarnings("unchecked")
        void wrapsUnresolvedDependencyErrorsClearly() throws Exception {
            var mavenProject = new MavenProject();
            mavenProject.setBuild(new Build());
            var project = new MavenProject() {
                @Override
                public List<String> getTestClasspathElements() throws DependencyResolutionRequiredException {
                    throw new DependencyResolutionRequiredException(mavenProject.getArtifact());
                }
            };
            project.setBuild(new Build());

            var mojo = new ParamixelMojo();
            setField(mojo, "project", project);

            assertThatThrownBy(() -> invokeBuildTestClasspathUrls(mojo))
                    .cause()
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageStartingWith("Failed to resolve Maven test classpath")
                    .hasCauseInstanceOf(DependencyResolutionRequiredException.class);
        }

        @Test
        @DisplayName("rejects missing build information")
        @SuppressWarnings("unchecked")
        void rejectsMissingBuildInformation() throws Exception {
            var project = new MavenProject() {
                @Override
                public Build getBuild() {
                    return null;
                }
            };
            var mojo = new ParamixelMojo();
            setField(mojo, "project", project);

            assertThatThrownBy(() -> invokeBuildTestClasspathUrls(mojo))
                    .cause()
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Project build information is not available");
        }

        @Test
        @DisplayName("skips blank classpath elements")
        @SuppressWarnings("unchecked")
        void skipsBlankClasspathElements() throws Exception {
            var valid = tempDir.resolve("valid-classes").toString();

            var project = new MavenProject() {
                private final List<String> testClasspath = Arrays.asList("", "  ", valid, null);

                @Override
                public List<String> getTestClasspathElements() {
                    return testClasspath;
                }
            };
            project.setBuild(new Build());

            var mojo = new ParamixelMojo();
            setField(mojo, "project", project);

            var urls = invokeBuildTestClasspathUrls(mojo);

            assertThat(urls).hasSize(1);
            assertThat(urls.get(0)).isEqualTo(new File(valid).toURI().toURL());
        }

        @Test
        @DisplayName("handles empty classpath elements list")
        @SuppressWarnings("unchecked")
        void handlesEmptyClasspathElementsList() throws Exception {
            var project = new MavenProject() {
                @Override
                public List<String> getTestClasspathElements() {
                    return List.of();
                }
            };
            project.setBuild(new Build());

            var mojo = new ParamixelMojo();
            setField(mojo, "project", project);

            var urls = invokeBuildTestClasspathUrls(mojo);

            assertThat(urls).isEmpty();
        }
    }

    @Nested
    @DisplayName("buildTestClassLoader tests")
    class BuildTestClassLoaderTests {

        @Test
        @DisplayName("creates URLClassLoader from Maven test classpath")
        @SuppressWarnings("unchecked")
        void createsUrlClassLoaderFromMavenTestClasspath() throws Exception {
            var classesDir = tempDir.resolve("classes").toString();

            var project = new MavenProject() {
                private final List<String> testClasspath = List.of(classesDir);

                @Override
                public List<String> getTestClasspathElements() {
                    return testClasspath;
                }
            };
            project.setBuild(new Build());

            var mojo = new ParamixelMojo();
            setField(mojo, "project", project);

            var classLoader = invokeBuildTestClassLoader(mojo);

            try {
                assertThat(classLoader.getURLs()).hasSize(1);
                assertThat(classLoader.getURLs()[0])
                        .isEqualTo(new File(classesDir).toURI().toURL());
            } finally {
                classLoader.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<URL> invokeBuildTestClasspathUrls(final ParamixelMojo mojo) throws Exception {
        var method = ParamixelMojo.class.getDeclaredMethod("buildTestClasspathUrls");
        method.setAccessible(true);
        return (List<URL>) method.invoke(mojo);
    }

    private static URLClassLoader invokeBuildTestClassLoader(final ParamixelMojo mojo) throws Exception {
        var method = ParamixelMojo.class.getDeclaredMethod("buildTestClassLoader");
        method.setAccessible(true);
        return (URLClassLoader) method.invoke(mojo);
    }

    private static void setField(final ParamixelMojo mojo, final String name, final Object value) throws Exception {
        var field = ParamixelMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }
}

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

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.paramixel.core.Action;
import org.paramixel.core.Configuration;
import org.paramixel.core.Factory;
import org.paramixel.core.Resolver;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.exception.ConfigurationException;
import org.paramixel.core.exception.ResolverException;
import org.paramixel.core.internal.TildePathExpander;
import org.paramixel.core.support.AnsiColor;
import org.paramixel.core.support.Arguments;

/**
 * Maven mojo that discovers and executes Paramixel action trees during the test phase.
 *
 * <p>The mojo resolves action factories from the test classpath using {@link Resolver},
 * executes them with a {@link Runner}, and fails the build when the root action result
 * is {@code FAIL} (or {@code SKIP} when {@code failureOnSkip} is {@code true}).
 *
 * <p>The action tree is executed exactly once. The result is returned
 * directly from the runner, avoiding redundant re-execution.
 */
@Mojo(
        name = "test",
        defaultPhase = LifecyclePhase.TEST,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true)
public class ParamixelMojo extends AbstractMojo {

    /**
     * Constructs a mojo with default parameter values; Maven injects configuration after construction.
     */
    public ParamixelMojo() {
        // Intentionally empty
    }

    /**
     * The Maven project being built.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * When {@code true}, skip Paramixel test execution entirely.
     */
    @Parameter(property = "paramixel.skipTests", defaultValue = "false")
    private boolean skipTests;

    /**
     * When {@code true}, fail the build when no action factories are discovered.
     */
    @Parameter(property = "paramixel.failIfNoTests", defaultValue = "false")
    private boolean failIfNoTests;

    /**
     * When {@code true}, SKIP results are treated as failures and cause the build to fail.
     */
    @Parameter(property = "paramixel.failureOnSkip", defaultValue = "false")
    private boolean failureOnSkip;

    /**
     * Path to the per-run summary report; when unset, no report file is generated.
     */
    @Parameter(property = "paramixel.report.file")
    private String reportFile;

    /**
     * Additional Paramixel configuration properties declared in the POM.
     */
    @Parameter
    private List<Property> properties;

    /**
     * Discovers and executes Paramixel action trees from the test classpath.
     *
     * <p>Replaces the thread context classloader with a test classloader for the
     * duration of execution. Fails the build when the root action result is
     * {@code FAIL}, or {@code SKIP} when {@code failureOnSkip} is {@code true}.
     *
     * @throws MojoExecutionException when configuration is invalid, action resolution fails,
     *     or an unexpected error occurs during execution
     * @throws MojoFailureException when the root action result is {@code FAIL},
     *     or {@code SKIP} with {@code failureOnSkip} enabled
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipTests) {
            getLog().info("Tests are skipped.");
            return;
        }

        Objects.requireNonNull(project, "MavenProject must not be null");

        final var originalClassLoader = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader testClassLoader = buildTestClassLoader()) {
            Thread.currentThread().setContextClassLoader(testClassLoader);
            final var configuration = buildConfiguration(testClassLoader);

            Optional<Action> optionalAction = Resolver.resolveActions(configuration);

            if (optionalAction.isEmpty()) {
                if (failIfNoTests) {
                    throw new MojoExecutionException("No tests found and failIfNoTests is true");
                }

                getLog().info("No Paramixel tests found.");
                return;
            }

            try (Runner runner = Runner.builder()
                    .configuration(configuration)
                    .listener(Factory.defaultListener(configuration))
                    .build()) {

                Action action = optionalAction.get();
                Result result = runner.run(action);
                var status = result.getStatus();
                if (status.isFailure() || (status.isSkip() && failureOnSkip)) {
                    throw new MojoFailureException(AnsiColor.BOLD_RED_TEXT.format("TESTS FAILED"));
                }
            }
        } catch (ConfigurationException e) {
            throw new MojoExecutionException("Failed to build Paramixel configuration: " + e.getMessage(), e);
        } catch (ResolverException e) {
            throw new MojoExecutionException("Failed to resolve Paramixel actions: " + e.getMessage(), e);
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to run Paramixel tests", e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private Map<String, String> buildConfiguration(ClassLoader classLoader) throws MojoExecutionException {
        var configuration = new LinkedHashMap<String, String>(Configuration.defaultProperties(classLoader));
        putIfNotBlank(configuration, Configuration.REPORT_FILE, reportFile);

        if (reportFile != null && !reportFile.isBlank() && project != null) {
            final var build = project.getBuild();
            if (build != null) {
                Path reportPath = TildePathExpander.expand(reportFile);
                Path targetPath = Paths.get(build.getDirectory());
                if (!reportPath.normalize().startsWith(targetPath.normalize())) {
                    getLog().warn("Report file path '" + reportFile + "' resolves outside target directory '"
                            + build.getDirectory() + "'");
                }
            }
        }

        // POM <properties> override file/defaults but not system properties
        if (properties != null) {
            var seenKeys = new LinkedHashSet<String>();
            for (Property property : properties) {
                String key = property.getKey();
                String value = property.getValue();

                if (key == null) {
                    throw new MojoExecutionException("Paramixel property key must not be null");
                }

                if (key.isBlank()) {
                    throw new MojoExecutionException("Paramixel property key must not be blank");
                }

                if (value == null) {
                    throw new MojoExecutionException("Paramixel property '" + key + "' value must not be null");
                }

                if (!seenKeys.add(key)) {
                    getLog().warn("Duplicate Paramixel property key '" + key + "' — later value overrides earlier");
                }

                configuration.put(key, value);
            }
        }

        // System properties always win (matches JUnit Platform precedence)
        var systemProps = System.getProperties().entrySet().stream().toList();
        for (var entry : systemProps) {
            String k = String.valueOf(entry.getKey());
            if (k.startsWith("paramixel.")) {
                configuration.put(k, String.valueOf(entry.getValue()));
            }
        }

        return configuration;
    }

    private static void putIfNotBlank(Map<String, String> configuration, String key, String value) {
        if (value != null && !value.isBlank()) {
            configuration.put(key, value);
        }
    }

    private URLClassLoader buildTestClassLoader() throws MojoExecutionException {
        final List<URL> classpathUrls = buildTestClasspathUrls();
        final var urls = new URL[classpathUrls.size()];
        for (int i = 0; i < classpathUrls.size(); i++) {
            var classpathUrl = classpathUrls.get(i);
            try {
                urls[i] = new File(classpathUrl.toURI()).toURI().toURL();
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to convert classpath URL: " + classpathUrl, e);
            }
        }
        return new URLClassLoader(urls, getClass().getClassLoader());
    }

    private List<URL> buildTestClasspathUrls() throws MojoExecutionException {
        final var build = project.getBuild();
        if (build == null) {
            throw new MojoExecutionException("Project build information is not available");
        }
        final var testClassesDir = new File(build.getTestOutputDirectory());
        final var classesDir = new File(build.getOutputDirectory());
        final var classpathUrls = new LinkedHashSet<URL>();

        try {
            if (testClassesDir.exists()) {
                classpathUrls.add(testClassesDir.toURI().toURL());
            }

            if (classesDir.exists()) {
                classpathUrls.add(classesDir.toURI().toURL());
            }

            final var artifacts = project.getArtifacts();
            if (artifacts != null) {
                for (var artifact : artifacts) {
                    File artifactFile = artifact.getFile();
                    if (artifactFile != null) {
                        classpathUrls.add(artifactFile.toURI().toURL());
                    }
                }
            } else {
                getLog().warn("Project artifacts are not available; classpath may be incomplete");
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build test classpath URLs", e);
        }

        return new ArrayList<>(classpathUrls);
    }

    /**
     * A key-value Paramixel configuration property declared in the POM.
     */
    public static class Property {

        private String key;

        private String value;

        /**
         * Constructs a property with null key and value; callers must set both before use.
         */
        public Property() {
            // Intentionally empty
        }

        /**
         * Returns the configuration property key.
         *
         * @return the property key, or {@code null} if not yet assigned
         */
        public String getKey() {
            return key;
        }

        /**
         * Assigns the configuration property key, rejecting blank and null values.
         *
         * @param key the configuration property key used to look up the Paramixel property; must not be blank
         * @throws NullPointerException if {@code key} is {@code null}
         * @throws IllegalArgumentException if {@code key} is blank
         */
        public void setKey(String key) {
            Objects.requireNonNull(key, "key must not be null");

            Arguments.requireNonBlank(key, "key must not be blank");

            this.key = key;
        }

        /**
         * Returns the configuration property value.
         *
         * @return the property value, or {@code null} if not yet assigned
         */
        public String getValue() {
            return value;
        }

        /**
         * Assigns the configuration property value, rejecting null.
         *
         * @param value the configuration property value; must not be null
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public void setValue(String value) {
            Objects.requireNonNull(value, "value must not be null");
            this.value = value;
        }

        /**
         * Compares this property to another for equality based on {@code key} only.
         *
         * @param o the object to compare against
         * @return {@code true} if the other object is a {@code Property} with the same key
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Property)) return false;
            Property property = (Property) o;
            return Objects.equals(key, property.key);
        }

        /**
         * Returns a hash code derived from {@code key} only.
         *
         * @return the hash code for this property
         */
        @Override
        public int hashCode() {
            return Objects.hash(key);
        }
    }
}

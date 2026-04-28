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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.paramixel.core.Action;
import org.paramixel.core.Listener;
import org.paramixel.core.Resolver;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.maven.plugin.internal.util.Arguments;

@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class ParamixelMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(property = "paramixel.maven.skipTests", defaultValue = "false")
    private boolean skipTests;

    @Parameter(property = "paramixel.maven.failIfNoTests", defaultValue = "true")
    private boolean failIfNoTests;

    @Parameter
    private List<Property> properties;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipTests || Boolean.getBoolean("paramixel.maven.skipTests")) {
            getLog().info("Tests are skipped.");
            return;
        }

        final var configuration = buildConfiguration();
        final var originalClassLoader = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader testClassLoader = buildTestClassLoader()) {
            Thread.currentThread().setContextClassLoader(testClassLoader);

            Optional<Action> optionalAction = Resolver.resolveActions(testClassLoader);

            if (optionalAction.isEmpty()) {
                if (failIfNoTests) {
                    throw new MojoFailureException("No tests found and failIfNoTests is true");
                }

                getLog().info("No Paramixel tests found.");
                return;
            }

            Runner runner = buildRunner(configuration);
            Result result = runner.run(optionalAction.get());

            if (result.status() == Result.Status.FAIL) {
                throw new MojoFailureException("There are test failures");
            }

        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute Paramixel specs", e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private Map<String, String> buildConfiguration() {
        // Start with Configuration defaults (file properties + system properties + defaults)
        Map<String, String> configuration = new LinkedHashMap<>(org.paramixel.core.Configuration.defaultProperties());

        // POM <properties> override file/defaults but not system properties
        if (properties != null) {
            for (Property property : properties) {
                String key = property.getKey();
                String value = property.getValue();

                if (key == null) {
                    throw new NullPointerException("property key must not be null");
                }

                if (key.isBlank()) {
                    throw new IllegalArgumentException("Property key must not be null or blank");
                }

                if (value == null) {
                    throw new NullPointerException("property value must not be null");
                }

                configuration.put(key, value);
            }
        }

        // System properties always win (matches JUnit Platform precedence)
        System.getProperties().forEach((key, value) -> {
            String k = String.valueOf(key);
            if (k.startsWith("paramixel.")) {
                configuration.put(k, String.valueOf(value));
            }
        });

        return configuration;
    }

    private Runner buildRunner(Map<String, String> configuration) {
        return Runner.builder()
                .configuration(configuration)
                .listener(Listener.treeListener())
                .build();
    }

    private URLClassLoader buildTestClassLoader() throws MojoExecutionException {
        final List<URL> classpathUrls = buildTestClasspathUrls();

        URL[] urls = classpathUrls.stream()
                .map(path -> {
                    try {
                        return new File(path.toURI()).toURI().toURL();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toArray(URL[]::new);

        return new URLClassLoader(urls, getClass().getClassLoader());
    }

    private List<URL> buildTestClasspathUrls() throws MojoExecutionException {
        final File testClassesDir = new File(project.getBuild().getTestOutputDirectory());
        final File classesDir = new File(project.getBuild().getOutputDirectory());
        final Set<URL> classpathUrls = new LinkedHashSet<>();

        try {
            if (testClassesDir.exists()) {
                classpathUrls.add(testClassesDir.toURI().toURL());
            }

            if (classesDir.exists()) {
                classpathUrls.add(classesDir.toURI().toURL());
            }

            for (String dependency : project.getTestClasspathElements()) {
                classpathUrls.add(new File(dependency).toURI().toURL());
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build test classpath URLs", e);
        }

        return new ArrayList<>(classpathUrls);
    }

    public static class Property {

        private String key;

        private String value;

        public Property() {}

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            Objects.requireNonNull(key, "key must not be null");

            Arguments.requireNotBlank(key, "key must not be blank");

            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            Objects.requireNonNull(value, "value must not be null");
            this.value = value;
        }
    }
}

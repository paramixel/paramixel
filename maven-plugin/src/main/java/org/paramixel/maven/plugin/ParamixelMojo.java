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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
import org.paramixel.core.Listener;
import org.paramixel.core.Runner;
import org.paramixel.core.discovery.Resolver;
import org.paramixel.core.support.Arguments;

@Mojo(
        name = "test",
        defaultPhase = LifecyclePhase.TEST,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true)
public class ParamixelMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(property = "paramixel.skipTests", defaultValue = "false")
    private boolean skipTests;

    @Parameter(property = "paramixel.failIfNoTests", defaultValue = "true")
    private boolean failIfNoTests;

    @Parameter
    private List<Property> properties;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipTests || Boolean.getBoolean("paramixel.skipTests")) {
            getLog().info("Tests are skipped.");
            return;
        }

        final var originalClassLoader = Thread.currentThread().getContextClassLoader();
        ExecutorService executorService = null;

        try (URLClassLoader testClassLoader = buildTestClassLoader()) {
            Thread.currentThread().setContextClassLoader(testClassLoader);
            final var configuration = buildConfiguration(testClassLoader);

            Optional<Action> optionalAction = Resolver.resolveActions(testClassLoader);

            if (optionalAction.isEmpty()) {
                if (failIfNoTests) {
                    throw new MojoFailureException("No tests found and failIfNoTests is true");
                }

                getLog().info("No Paramixel tests found.");
                return;
            }

            executorService = createExecutorService(configuration);

            Runner runner = Runner.builder()
                    .configuration(configuration)
                    .executorService(executorService)
                    .listener(Listener.treeListener())
                    .build();

            Action action = optionalAction.get();
            runner.run(action);

            if (action.getResult().getStatus().isFailure()) {
                throw new MojoFailureException("There are test failures");
            }
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute Paramixel specs", e);
        } finally {
            shutdownExecutorService(executorService);

            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private Map<String, String> buildConfiguration() throws MojoExecutionException {
        return buildConfiguration(Thread.currentThread().getContextClassLoader());
    }

    private Map<String, String> buildConfiguration(ClassLoader classLoader) throws MojoExecutionException {
        Map<String, String> configuration =
                new LinkedHashMap<>(withContextClassLoader(classLoader, Configuration::defaultProperties));

        // POM <properties> override file/defaults but not system properties
        if (properties != null) {
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

    private static <T> T withContextClassLoader(ClassLoader classLoader, Supplier<T> supplier) {
        Objects.requireNonNull(classLoader, "classLoader must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");

        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
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

            for (var artifact : project.getArtifacts()) {
                File artifactFile = artifact.getFile();
                if (artifactFile != null) {
                    classpathUrls.add(artifactFile.toURI().toURL());
                }
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build test classpath URLs", e);
        }

        return new ArrayList<>(classpathUrls);
    }

    private static ExecutorService createExecutorService(Map<String, String> configuration) {
        int parallelism = Integer.parseInt(configuration.getOrDefault(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(Runtime.getRuntime().availableProcessors() * 2)));

        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                parallelism, parallelism, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "paramixel");
                    thread.setDaemon(true);
                    return thread;
                });

        threadPoolExecutor.prestartAllCoreThreads();
        return threadPoolExecutor;
    }

    private static void shutdownExecutorService(ExecutorService executorService) {
        if (executorService != null) {
            executorService.shutdown();

            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
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

            Arguments.requireNonBlank(key, "key must not be blank");

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

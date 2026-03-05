/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.paramixel.api.Paramixel;

/**
 * Provides ParamixelMojo.
 *
 * @author Douglas Hoard <doug.hoard@gmail.com>
 * @since 0.0.1
 */
@Mojo(
        name = "test",
        defaultPhase = LifecyclePhase.TEST,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.TEST)
public class ParamixelMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * Whether to skip test execution.
     *
     * <p>System property: -Dparamixel.skipTests=true</p>
     */
    @Parameter(property = "paramixel.skipTests", defaultValue = "false")
    private boolean skipTests;

    /**
     * Whether to fail the build if tests fail.
     */
    @Parameter(property = "paramixel.failIfNoTests", defaultValue = "true")
    private boolean failIfNoTests;

    /**
     * The global parallelism setting for test class execution.
     */
    @Parameter(property = "paramixel.parallelism")
    private Integer parallelism;

    /**
     * Whether to print verbose output.
     */
    @Parameter(property = "paramixel.verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * Include tags for test filtering.
     * Comma-separated list of regex patterns.
     */
    @Parameter(property = "paramixel.tags.include")
    private String includeTags;

    /**
     * Exclude tags for test filtering.
     * Comma-separated list of regex patterns.
     */
    @Parameter(property = "paramixel.tags.exclude")
    private String excludeTags;

    /**
     * Creates a new Mojo instance.
     *
     * @since 0.0.1
     */
    public ParamixelMojo() {
        // INTENTIONALLY EMPTY
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipTests) {
            getLog().info("Tests are skipped.");
            return;
        }

        /*
        if (verbose) {
            getLog().info("Starting Paramixel test execution");
            getLog().info("Parallelism: " + parallelism);
            getLog().info("Fail if no tests: " + failIfNoTests);
        }
        */

        final Thread currentThread = Thread.currentThread();
        final ClassLoader previousClassLoader = currentThread.getContextClassLoader();

        try (URLClassLoader classLoader = buildTestClassLoader()) {
            currentThread.setContextClassLoader(classLoader);

            final List<Class<?>> testClasses = discoverTestClasses(classLoader);

            if (testClasses.isEmpty()) {
                final String message = "No @Paramixel.TestClass annotated classes found";
                if (failIfNoTests) {
                    throw new MojoFailureException(message);
                } else {
                    getLog().warn(message);
                    return;
                }
            }

            /*
            if (verbose) {
                getLog().info("Found " + testClasses.size() + " test classes:");
                for (final Class<?> testClass : testClasses) {
                    getLog().info("  - " + testClass.getName());
                }
            }
            */

            executeTests(testClasses);
        } catch (MojoExecutionException | MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute Paramixel tests", e);
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    /**
     * Discovers all test classes annotated with @Paramixel.TestClass.
     *
     * @param classLoader the classLoader
     * @return a list of test classes to execute
     * @since 0.0.1
     */
    private List<Class<?>> discoverTestClasses(final @NonNull ClassLoader classLoader) {
        final File testClassesDir = new File(project.getBuild().getTestOutputDirectory());
        final File classesDir = new File(project.getBuild().getOutputDirectory());

        if (!testClassesDir.exists() && !classesDir.exists()) {
            getLog().warn("No test classes directory found");
            return new ArrayList<>();
        }

        final Set<String> classNames = scanForTestClasses(testClassesDir);
        final List<Class<?>> testClasses = new ArrayList<>();

        for (String className : classNames) {
            try {
                final Class<?> clazz = classLoader.loadClass(className);
                if (clazz.isAnnotationPresent(Paramixel.TestClass.class)) {
                    testClasses.add(clazz);
                }
            } catch (ClassNotFoundException e) {
                getLog().warn("Could not load class: " + className);
            } catch (NoClassDefFoundError e) {
                getLog().warn("Could not load class definition: " + className);
            }
        }

        return testClasses;
    }

    /**
     * Scans a directory for class files.
     *
     * @param directory the directory to scan
     * @return a set of fully qualified class names
     * @since 0.0.1
     */
    private Set<String> scanForTestClasses(final @NonNull File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return Collections.emptySet();
        }

        final Set<String> classNames = new HashSet<>();
        scanDirectoryRecursive(directory, directory.getPath(), classNames);
        return classNames;
    }

    /**
     * Performs scanDirectoryRecursive.
     *
     * @param directory the directory
     * @param basePath the basePath
     * @param classNames the classNames
     * @since 0.0.1
     */
    private void scanDirectoryRecursive(
            final @NonNull File directory, final @NonNull String basePath, final @NonNull Set<String> classNames) {
        final File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryRecursive(file, basePath, classNames);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                final String relativePath = file.getPath().substring(basePath.length());
                final String className =
                        relativePath.replace(File.separatorChar, '.').replace(".class", "");
                classNames.add(className.startsWith(".") ? className.substring(1) : className);
            }
        }
    }

    /**
     * Executes the tests using the JUnit Platform.
     *
     * @param testClasses the classes to execute
     * @throws Exception if execution fails
     * @since 0.0.1
     */
    private void executeTests(final @NonNull List<Class<?>> testClasses) throws Exception {
        getLog().info("Executing " + testClasses.size() + " test classes");

        final LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();

        for (Class<?> testClass : testClasses) {
            requestBuilder.selectors(DiscoverySelectors.selectClass(testClass));
        }

        requestBuilder.filters(EngineFilter.includeEngines("paramixel"));

        requestBuilder.configurationParameter("invokedBy", "maven");

        if (parallelism != null) {
            requestBuilder.configurationParameter("paramixel.parallelism", String.valueOf(parallelism));
            getLog().info("Paramixel parallelism: " + parallelism);
        }

        if (includeTags != null && !includeTags.trim().isEmpty()) {
            requestBuilder.configurationParameter("paramixel.tags.include", includeTags.trim());
            getLog().info("Including tests with tags matching: " + includeTags.trim());
        }

        if (excludeTags != null && !excludeTags.trim().isEmpty()) {
            requestBuilder.configurationParameter("paramixel.tags.exclude", excludeTags.trim());
            getLog().info("Excluding tests with tags matching: " + excludeTags.trim());
        }

        final LauncherDiscoveryRequest request = requestBuilder.build();

        final Launcher launcher = LauncherFactory.create();

        final SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(summaryListener);

        launcher.execute(request);

        final TestExecutionSummary summary = summaryListener.getSummary();

        final long testsFailed = summary.getTotalFailureCount();

        if (testsFailed > 0) {
            throw new MojoFailureException(
                    "Tests failed: " + testsFailed + " of " + summary.getTestsFoundCount() + " tests");
        }
    }

    /**
     * Builds a class loader that includes test and main outputs plus test classpath.
     *
     * @return a class loader for executing tests
     * @throws Exception if classpath URLs cannot be built
     * @since 0.0.1
     */
    private URLClassLoader buildTestClassLoader() throws Exception {
        final List<URL> classpathUrls = buildTestClasspathUrls();
        return new URLClassLoader(
                classpathUrls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
    }

    /**
     * Builds the classpath URL list for test execution.
     *
     * @return the ordered list of classpath URLs
     * @throws Exception if classpath elements cannot be resolved
     * @since 0.0.1
     */
    private List<URL> buildTestClasspathUrls() throws Exception {
        final File testClassesDir = new File(project.getBuild().getTestOutputDirectory());
        final File classesDir = new File(project.getBuild().getOutputDirectory());

        final List<URL> classpathUrls = new ArrayList<>();

        if (testClassesDir.exists()) {
            classpathUrls.add(testClassesDir.toURI().toURL());
        }
        if (classesDir.exists()) {
            classpathUrls.add(classesDir.toURI().toURL());
        }

        for (String dependency : project.getTestClasspathElements()) {
            classpathUrls.add(new File(dependency).toURI().toURL());
        }

        return classpathUrls;
    }
}

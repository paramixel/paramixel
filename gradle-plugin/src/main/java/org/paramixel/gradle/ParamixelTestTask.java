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

package org.paramixel.gradle;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Gradle task for executing Paramixel tests.
 *
 * @author Douglas Hoard
 * @since 0.0.1
 */
public abstract class ParamixelTestTask extends DefaultTask {

    @Input
    public abstract Property<Boolean> getSkipTests();

    @Input
    public abstract Property<Boolean> getFailIfNoTests();

    @Input
    @Optional
    public abstract Property<Integer> getParallelism();

    @Input
    @Optional
    public abstract Property<String> getIncludeTags();

    @Input
    @Optional
    public abstract Property<String> getExcludeTags();

    @Classpath
    public abstract Property<FileCollection> getClasspath();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract Property<FileCollection> getTestClassesDirs();

    @Inject
    public ParamixelTestTask() {
        // INTENTIONALLY EMPTY
    }

    @TaskAction
    public final void execute() {
        if (getSkipTests().get()) {
            getLogger().info("Tests are skipped.");
            return;
        }

        final Thread currentThread = Thread.currentThread();
        final ClassLoader previousClassLoader = currentThread.getContextClassLoader();

        try (URLClassLoader classLoader = buildTestClassLoader()) {
            currentThread.setContextClassLoader(classLoader);

            final List<Class<?>> testClasses = discoverTestClasses(classLoader);

            if (testClasses.isEmpty()) {
                final String message = "No @Paramixel.TestClass annotated classes found";
                if (getFailIfNoTests().get()) {
                    throw new GradleException(message);
                } else {
                    getLogger().warn(message);
                    return;
                }
            }

            executeTests(testClasses);
        } catch (final GradleException e) {
            throw e;
        } catch (final Exception e) {
            throw new GradleException("Failed to execute Paramixel tests", e);
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    private URLClassLoader buildTestClassLoader() throws Exception {
        final List<URL> urls = new ArrayList<>();

        for (final File dir : getTestClassesDirs().get()) {
            if (dir.exists()) {
                urls.add(dir.toURI().toURL());
            }
        }

        for (final File file : getClasspath().get()) {
            urls.add(file.toURI().toURL());
        }

        return new URLClassLoader(
                urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
    }

    private List<Class<?>> discoverTestClasses(final @NonNull ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader must not be null");

        final List<Class<?>> testClasses = new ArrayList<>();
        final TestClassScanner scanner = new TestClassScanner();

        for (final File dir : getTestClassesDirs().get()) {
            if (dir.exists()) {
                testClasses.addAll(scanner.scan(dir, classLoader));
            }
        }

        return testClasses;
    }

    private void executeTests(final @NonNull List<Class<?>> testClasses) throws Exception {
        Objects.requireNonNull(testClasses, "testClasses must not be null");

        getLogger().info("Executing " + testClasses.size() + " test classes");

        final LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();

        for (final Class<?> testClass : testClasses) {
            requestBuilder.selectors(DiscoverySelectors.selectClass(testClass));
        }

        requestBuilder.filters(EngineFilter.includeEngines("paramixel"));
        requestBuilder.configurationParameter("invokedBy", "gradle");

        if (getParallelism().isPresent()) {
            requestBuilder.configurationParameter(
                    "paramixel.parallelism", String.valueOf(getParallelism().get()));
            getLogger().info("Paramixel parallelism: " + getParallelism().get());
        }

        if (getIncludeTags().isPresent()) {
            requestBuilder.configurationParameter(
                    "paramixel.tags.include", getIncludeTags().get().trim());
            getLogger()
                    .info("Including tests with tags matching: "
                            + getIncludeTags().get().trim());
        }

        if (getExcludeTags().isPresent()) {
            requestBuilder.configurationParameter(
                    "paramixel.tags.exclude", getExcludeTags().get().trim());
            getLogger()
                    .info("Excluding tests with tags matching: "
                            + getExcludeTags().get().trim());
        }

        final LauncherDiscoveryRequest request = requestBuilder.build();
        final Launcher launcher = LauncherFactory.create();

        final SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(summaryListener);
        launcher.execute(request);

        final TestExecutionSummary summary = summaryListener.getSummary();
        final long testsFailed = summary.getTotalFailureCount();

        if (testsFailed > 0) {
            throw new GradleException(
                    "Tests failed: " + testsFailed + " of " + summary.getTestsFoundCount() + " tests");
        }
    }
}

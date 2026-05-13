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

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Resolver;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.gradle.internal.ConfigurationBuilder;

/**
 * Gradle task that discovers and executes Paramixel action trees.
 *
 * <p>The task builds a {@link URLClassLoader} from the test runtime classpath, resolves action
 * factories via {@link Resolver}, executes them with a {@link Runner}, and fails the build when the
 * root action result is {@code FAIL} (or {@code SKIP} when {@code failureOnSkip} is
 * {@code true}).
 */
public abstract class ParamixelTestTask extends DefaultTask {

    /**
     * Constructs a task instance; Gradle registers and configures this task automatically.
     */
    public ParamixelTestTask() {
        // Intentionally empty
    }

    /**
     * Returns whether Paramixel test execution is skipped entirely.
     *
     * @return whether test execution is skipped
     */
    @Input
    public abstract Property<Boolean> getSkipTests();

    /**
     * Returns whether the build should fail when no action factories are discovered.
     *
     * @return whether the build fails on zero discovered actions
     */
    @Input
    public abstract Property<Boolean> getFailIfNoTests();

    /**
     * Returns whether skipped results are treated as failures.
     *
     * @return whether a SKIP result is treated as a failure
     */
    @Input
    public abstract Property<Boolean> getFailureOnSkip();

    /**
     * Returns the runner parallelism level.
     *
     * @return the parallelism property, or an unset property to use the framework default
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<Integer> getParallelism();

    /**
     * Returns the package-name regex used to filter discovered action factories.
     *
     * @return the package-match property, or an unset property to include all packages
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getMatchPackage();

    /**
     * Returns the fully-qualified class-name regex used to filter discovered action factories.
     *
     * @return the class-match property, or an unset property to include all classes
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getMatchClass();

    /**
     * Returns the tag regex used to filter discovered action factories.
     *
     * @return the tag-match property, or an unset property to include all tags
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getMatchTag();

    /**
     * Returns the file used for the summary report.
     *
     * @return the report-file property, or an unset property to disable report output
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getReportFile();

    /**
     * Returns the test runtime classpath used to discover and execute action factories.
     *
     * @return the test runtime classpath used for action discovery and execution
     */
    @Classpath
    public abstract ConfigurableFileCollection getTestClasspath();

    /**
     * Discovers and executes Paramixel action trees.
     *
     * <p>If {@link #getSkipTests()} is {@code true}, logs a skip message and returns immediately.
     * Otherwise, builds a configuration from extension defaults and system properties, resolves
     * action factories, and executes them. The build fails on {@code FAIL} results, or on
     * {@code SKIP} results when {@link #getFailureOnSkip()} is {@code true}.</p>
     *
     * @throws GradleException if execution fails or no tests are found when
     *     {@link #getFailIfNoTests()} is {@code true}
     */
    @TaskAction
    public void execute() {
        if (getSkipTests().get()) {
            getLogger().info("Tests are skipped.");
            return;
        }

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader testClassLoader = buildTestClassLoader()) {
            Thread.currentThread().setContextClassLoader(testClassLoader);

            var failureOnSkip = getFailureOnSkip();

            Map<String, String> configuration = ConfigurationBuilder.buildConfiguration(
                    testClassLoader,
                    getParallelism(),
                    failureOnSkip,
                    getMatchPackage(),
                    getMatchClass(),
                    getMatchTag(),
                    getReportFile());

            Optional<Action> optionalAction = Resolver.resolveActions(configuration);

            if (optionalAction.isEmpty()) {
                if (getFailIfNoTests().get()) {
                    throw new GradleException("No Paramixel tests found and failIfNoTests is true");
                }
                getLogger().info("No Paramixel tests found.");
                return;
            }

            try (Runner runner = Runner.builder()
                    .configuration(configuration)
                    .listener(Factory.defaultListener(configuration))
                    .build()) {

                Result result = runner.run(optionalAction.get());

                var status = result.getStatus();
                if (status.isFailure() || (status.isSkip() && failureOnSkip.get())) {
                    throw new GradleException("TESTS FAILED");
                }
            }
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException("Failed to execute Paramixel tests", e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private URLClassLoader buildTestClassLoader() throws Exception {
        var urls = new ArrayList<URL>();
        for (File file : getTestClasspath().getFiles()) {
            urls.add(file.toURI().toURL());
        }
        return new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
    }
}

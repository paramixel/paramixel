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

import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * DSL extension for configuring the Paramixel Gradle plugin.
 *
 * <p>Properties set here convention-map to the {@link ParamixelTestTask} inputs. Unset optional
 * properties ({@code parallelism}, {@code matchPackage}, {@code matchClass}, {@code matchTag},
 * {@code reportFile}) remain absent and will not overlay the defaults from
 * {@link org.paramixel.core.Configuration#defaultProperties()}.</p>
 */
public abstract class ParamixelExtension {

    private final Property<Boolean> skipTests;
    private final Property<Boolean> failIfNoTests;
    private final Property<Boolean> failureOnSkip;
    private final Property<Integer> parallelism;
    private final Property<String> matchPackage;
    private final Property<String> matchClass;
    private final Property<String> matchTag;
    private final Property<String> reportFile;

    /**
     * Creates the extension with Gradle-injected property defaults.
     *
     * @param objects the Gradle {@link ObjectFactory} used to create typed properties
     */
    @Inject
    public ParamixelExtension(ObjectFactory objects) {
        skipTests = objects.property(Boolean.class).convention(false);
        failIfNoTests = objects.property(Boolean.class).convention(false);
        failureOnSkip = objects.property(Boolean.class).convention(false);
        parallelism = objects.property(Integer.class);
        matchPackage = objects.property(String.class);
        matchClass = objects.property(String.class);
        matchTag = objects.property(String.class);
        reportFile = objects.property(String.class);
    }

    /**
     * Returns whether Paramixel test execution is skipped entirely.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return the skip-tests property
     */
    public Property<Boolean> getSkipTests() {
        return skipTests;
    }

    /**
     * Returns whether the build should fail when no action factories are discovered.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return the fail-if-no-tests property
     */
    public Property<Boolean> getFailIfNoTests() {
        return failIfNoTests;
    }

    /**
     * Returns whether skipped results are treated as failures.
     *
     * <p>Defaults to {@code false}. When {@code true}, a {@code SKIP} result causes the build to
     * fail.</p>
     *
     * @return the failure-on-skip property
     */
    public Property<Boolean> getFailureOnSkip() {
        return failureOnSkip;
    }

    /**
     * Returns the runner parallelism level.
     *
     * <p>Unset by default; when absent, the framework default from
     * {@link org.paramixel.core.Configuration#defaultProperties()} is used.</p>
     *
     * @return the parallelism property
     */
    public Property<Integer> getParallelism() {
        return parallelism;
    }

    /**
     * Returns the package-name regex used to filter discovered action factories.
     *
     * <p>Unset by default; when absent, all packages are included.</p>
     *
     * @return the package-match property
     */
    public Property<String> getMatchPackage() {
        return matchPackage;
    }

    /**
     * Returns the fully-qualified class-name regex used to filter discovered action factories.
     *
     * <p>Unset by default; when absent, all classes are included.</p>
     *
     * @return the class-match property
     */
    public Property<String> getMatchClass() {
        return matchClass;
    }

    /**
     * Returns the tag regex used to filter discovered action factories.
     *
     * <p>Unset by default; when absent, all tags are included.</p>
     *
     * @return the tag-match property
     */
    public Property<String> getMatchTag() {
        return matchTag;
    }

    /**
     * Returns the file used for the summary report.
     *
     * <p>Unset by default; when absent, no report is written.</p>
     *
     * @return the report-file property
     */
    public Property<String> getReportFile() {
        return reportFile;
    }

}

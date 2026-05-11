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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Gradle plugin that discovers and executes Paramixel action trees during the verification phase.
 *
 * <p>Applies the {@code java} plugin, registers the {@code paramixel} DSL extension, and creates
 * a {@code paramixelTest} task wired to the test runtime classpath.
 */
public class ParamixelPlugin implements Plugin<Project> {

    /** Creates a Paramixel plugin instance. */
    public ParamixelPlugin() {}

    /**
     * The Gradle plugin identifier used in {@code plugins { id(...) }} blocks.
     */
    public static final String PLUGIN_ID = "org.paramixel";

    /**
     * The DSL extension name used in {@code paramixel { ... }} configuration blocks.
     */
    public static final String EXTENSION_NAME = "paramixel";

    /**
     * The name of the registered verification task.
     */
    public static final String TASK_NAME = "paramixelTest";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");

        ParamixelExtension extension =
                project.getExtensions().create(EXTENSION_NAME, ParamixelExtension.class, project.getObjects());

        ProviderFactory providers = project.getProviders();

        project.getTasks().register(TASK_NAME, ParamixelTestTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Discovers and executes Paramixel action trees");

            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            SourceSet testSourceSet = sourceSets.getByName("test");

            task.getTestClasspath().from(testSourceSet.getRuntimeClasspath());
            task.dependsOn(project.getTasks().named("testClasses"));

            task.getSkipTests().convention(
                    booleanProvider(providers, "paramixel.skipTests").orElse(extension.getSkipTests()));
            task.getFailIfNoTests().convention(
                    booleanProvider(providers, "paramixel.failIfNoTests").orElse(extension.getFailIfNoTests()));
            task.getFailureOnSkip().convention(
                    booleanProvider(providers, "paramixel.failureOnSkip").orElse(extension.getFailureOnSkip()));
            task.getParallelism().convention(
                    integerProvider(providers, "paramixel.parallelism").orElse(extension.getParallelism()));
            task.getMatchPackage().convention(
                    stringProvider(providers, "paramixel.match.package").orElse(extension.getMatchPackage()));
            task.getMatchClass().convention(
                    stringProvider(providers, "paramixel.match.class").orElse(extension.getMatchClass()));
            task.getMatchTag().convention(
                    stringProvider(providers, "paramixel.match.tag").orElse(extension.getMatchTag()));
            task.getReportFile().convention(
                    stringProvider(providers, "paramixel.report.file").orElse(extension.getReportFile()));
        });

        project.getTasks().named("check", check -> check.dependsOn(TASK_NAME));
    }

    private static Provider<Boolean> booleanProvider(ProviderFactory providers, String key) {
        return providers.systemProperty(key)
                .map(Boolean::parseBoolean)
                .orElse(providers.gradleProperty(key).map(Boolean::parseBoolean));
    }

    private static Provider<Integer> integerProvider(ProviderFactory providers, String key) {
        return providers.systemProperty(key)
                .map(Integer::parseInt)
                .orElse(providers.gradleProperty(key).map(Integer::parseInt));
    }

    private static Provider<String> stringProvider(ProviderFactory providers, String key) {
        return providers.systemProperty(key).orElse(providers.gradleProperty(key));
    }
}

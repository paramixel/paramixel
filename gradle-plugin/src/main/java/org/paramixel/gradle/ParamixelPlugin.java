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
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Gradle plugin that discovers and executes Paramixel action trees during the verification phase.
 *
 * <p>Applies the {@code java} plugin, registers the {@code paramixel} DSL extension, and creates
 * a {@code paramixelTest} task wired to the test runtime classpath.</p>
 */
public class ParamixelPlugin implements Plugin<Project> {

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

        project.getTasks().register(TASK_NAME, ParamixelTestTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Discovers and executes Paramixel action trees");

            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            SourceSet testSourceSet = sourceSets.getByName("test");

            task.getTestClasspath().from(testSourceSet.getRuntimeClasspath());
            task.dependsOn(project.getTasks().named("testClasses"));

            task.getSkipTests().convention(extension.getSkipTests());
            task.getFailIfNoTests().convention(extension.getFailIfNoTests());
            task.getFailureOnSkip().convention(extension.getFailureOnSkip());
            task.getParallelism().convention(extension.getParallelism());
            task.getMatchPackage().convention(extension.getMatchPackage());
            task.getMatchClass().convention(extension.getMatchClass());
            task.getMatchTag().convention(extension.getMatchTag());
            task.getReportFile().convention(extension.getReportFile());
            task.getReportFormat().convention(extension.getReportFormat());
        });

        project.getTasks().named("check", check -> check.dependsOn(TASK_NAME));
    }
}

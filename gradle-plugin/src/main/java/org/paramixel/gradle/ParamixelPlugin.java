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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Paramixel Gradle plugin.
 *
 * @author Douglas Hoard
 * @since 0.0.1
 */
public final class ParamixelPlugin implements Plugin<Project> {

    public ParamixelPlugin() {
        // INTENTIONALLY EMPTY
    }

    @Override
    public void apply(final Project project) {
        final ParamixelExtension extension = project.getExtensions().create("paramixel", ParamixelExtension.class);

        // Set conventions
        extension.getSkipTests().convention(false);
        extension.getFailIfNoTests().convention(true);

        project.getTasks().register("paramixelTest", ParamixelTestTask.class, task -> {
            task.getSkipTests().set(extension.getSkipTests());
            task.getFailIfNoTests().set(extension.getFailIfNoTests());
            task.getParallelism().set(extension.getParallelism());
            task.getIncludeTags().set(extension.getIncludeTags());
            task.getExcludeTags().set(extension.getExcludeTags());

            project.getPlugins().withType(JavaPlugin.class, plugin -> {
                final SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
                final SourceSet testSourceSet = sourceSets.getByName("test");

                task.getTestClassesDirs().set(testSourceSet.getOutput().getClassesDirs());
                task.getClasspath().set(testSourceSet.getRuntimeClasspath());

                task.dependsOn(testSourceSet.getClassesTaskName());
            });
        });

        project.getTasks().named("check").configure(check -> {
            check.dependsOn("paramixelTest");
        });
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ParamixelPlugin.
 */
public class ParamixelPluginTest {

    @Test
    public void pluginRegistersTask() {
        final Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply("org.paramixel");

        assertThat(project.getTasks().findByName("paramixelTest")).isNotNull();
    }

    @Test
    public void pluginRegistersExtension() {
        final Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("org.paramixel");

        assertThat(project.getExtensions().findByName("paramixel")).isNotNull();
        assertThat(project.getExtensions().findByType(ParamixelExtension.class)).isNotNull();
    }

    @Test
    public void pluginConfiguresExtensionDefaults() {
        final Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("org.paramixel");

        final ParamixelExtension extension = project.getExtensions().getByType(ParamixelExtension.class);

        assertThat(extension.getSkipTests().get()).isFalse();
        assertThat(extension.getFailIfNoTests().get()).isTrue();
    }

    @Test
    public void taskAddedToCheckLifecycle() {
        final Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply("org.paramixel");

        assertThat(project.getTasks().getByName("check").getDependsOn())
                .anyMatch(task -> task.toString().contains("paramixelTest"));
    }
}

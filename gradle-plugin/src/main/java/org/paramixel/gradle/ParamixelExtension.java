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

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Configuration DSL for Paramixel Gradle plugin.
 *
 * @author Douglas Hoard
 * @since 0.0.1
 */
public interface ParamixelExtension {

    @Input
    @Optional
    Property<Boolean> getSkipTests();

    @Input
    @Optional
    Property<Boolean> getFailIfNoTests();

    @Input
    @Optional
    Property<Integer> getParallelism();

    @Input
    @Optional
    Property<String> getIncludeTags();

    @Input
    @Optional
    Property<String> getExcludeTags();
}

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

package org.paramixel.maven.plugin.fixtures;

import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Sequential;
import org.paramixel.api.Task;

/**
 * Fixture whose definition completes successfully, used to verify that the Maven mojo completes without
 * error on a passing execution path.
 */
public final class PassingMojoFixture {

    private PassingMojoFixture() {}

    /**
     * Creates a definition that always passes.
     *
     * @return a definition named {@code "mojo-pass"}
     */
    @Paramixel.TaskFactory
    public static Task task() {
        return Sequential.builder(PassingMojoFixture.class, "mojo-pass")
                .step("test")
                .build();
    }

    /**
     * A step that does nothing.
     *
     * @param context the execution context
     */
    @Paramixel.Id("test")
    public static void test(final Context context) {
        // Intentionally empty
    }
}

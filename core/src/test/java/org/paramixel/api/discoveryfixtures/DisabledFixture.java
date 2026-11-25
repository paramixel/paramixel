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

package org.paramixel.api.discoveryfixtures;

import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Sequential;
import org.paramixel.api.Task;

/**
 * A disabled factory that must never be invoked.
 */
public final class DisabledFixture {

    private DisabledFixture() {
        // Intentionally empty
    }

    /**
     * Returns a definition that would fail if it ever executed.
     *
     * @return the definition
     */
    @Paramixel.Disabled("disabled for discovery tests")
    @Paramixel.TaskFactory
    public static Task factory() {
        return Sequential.builder(DisabledFixture.class, "disabled").step("run").build();
    }

    /**
     * Records this fixture's execution.
     *
     * @param context the execution context
     */
    @Paramixel.Id("run")
    public static void run(final Context context) {
        throw new IllegalStateException("disabled factory was invoked");
    }
}

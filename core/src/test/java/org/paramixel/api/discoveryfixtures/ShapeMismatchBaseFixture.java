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
import org.paramixel.api.TaskBuilder;

/**
 * Base class of a fixture pair whose subclass changes the inherited factory's parameter shape, which discovery rejects
 * instead of silently preferring one declaration.
 */
public class ShapeMismatchBaseFixture {

    /**
     * Creates the fixture; exists only so the subclass can extend it.
     */
    protected ShapeMismatchBaseFixture() {
        // Intentionally empty
    }

    /**
     * Declares the no-parameter shape.
     *
     * @return the builder the framework would build
     */
    @Paramixel.TaskFactory
    public static TaskBuilder<?> factory() {
        return Sequential.builder(ShapeMismatchBaseFixture.class, "inherited").step("run");
    }

    /**
     * Records this fixture's execution.
     *
     * @param context the execution context
     */
    @Paramixel.Id("run")
    public static void run(final Context context) {
        DiscoveryFixtures.RECORDED.add("inherited-shape");
    }
}

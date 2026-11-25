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

import org.paramixel.api.Configuration;
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Sequential;
import org.paramixel.api.TaskBuilder;

/**
 * A subclass that declares its factory with the {@link Configuration} parameter instead of the inherited
 * {@link ShapeMismatchBaseFixture#factory()} shape.
 *
 * <p>Different parameter lists are different methods, so the inherited factory is not overridden and discovery reports
 * two factories rather than choosing one.
 */
public final class ShapeMismatchSubFixture extends ShapeMismatchBaseFixture {

    private ShapeMismatchSubFixture() {
        // Intentionally empty
    }

    /**
     * Declares the configuration shape.
     *
     * @param configuration the run's effective configuration
     * @return the builder the framework would build
     */
    @Paramixel.TaskFactory
    public static TaskBuilder<?> factory(final Configuration configuration) {
        return Sequential.builder(ShapeMismatchSubFixture.class, "sub-" + configuration)
                .step("run");
    }

    /**
     * Records this fixture's execution.
     *
     * @param context the execution context
     */
    @Paramixel.Id("run")
    public static void run(final Context context) {
        DiscoveryFixtures.RECORDED.add("sub-shape");
    }
}

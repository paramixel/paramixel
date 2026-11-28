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
 * A {@link Paramixel.TaskFactory} that returns a builder instead of a built definition.
 *
 * <p>The framework builds what it returns, so a factory can hand over an authoring surface exactly as a {@code main}
 * method does.
 */
public final class BuilderFactoryFixture {

    private BuilderFactoryFixture() {
        // Intentionally empty
    }

    /**
     * Returns an unbuilt definition.
     *
     * @return the builder the framework must build
     */
    @Paramixel.TaskFactory
    public static TaskBuilder<?> factory() {
        return Sequential.builder(BuilderFactoryFixture.class, "builder-factory")
                .step("run");
    }

    /**
     * Records this fixture's execution.
     *
     * @param context the execution context
     */
    @Paramixel.Id("run")
    public static void run(final Context context) {
        DiscoveryFixtures.RECORDED.add("builder-factory");
    }
}

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
import org.paramixel.api.Task;

/**
 * A {@link Paramixel.TaskFactory} that declares the optional {@link Configuration} parameter and returns a built
 * {@link Task}, proving the parameter shape is independent of the return type.
 */
public final class ConfiguredTaskFactoryFixture {

    /** Configuration key whose value becomes part of the definition's name. */
    public static final String SUFFIX_KEY = "fixture.suffix";

    private ConfiguredTaskFactoryFixture() {
        // Intentionally empty
    }

    /**
     * Returns a built definition named from the supplied configuration.
     *
     * @param configuration the run's effective configuration
     * @return the definition
     */
    @Paramixel.TaskFactory
    public static Task factory(final Configuration configuration) {
        final var suffix = configuration.getString(SUFFIX_KEY).orElse("unset");
        return Sequential.builder(ConfiguredTaskFactoryFixture.class, "configured-" + suffix)
                .step("run")
                .build();
    }

    /**
     * Records this fixture's execution.
     *
     * @param context the execution context
     */
    @Paramixel.Id("run")
    public static void run(final Context context) {
        DiscoveryFixtures.RECORDED.add("configured-task");
    }
}

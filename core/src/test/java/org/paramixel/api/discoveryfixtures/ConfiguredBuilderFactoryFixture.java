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
 * A {@link Paramixel.TaskFactory} that declares the optional {@link Configuration} parameter and lets it decide the
 * shape of the definition it returns.
 *
 * <p>The {@code base} step is always authored; the {@code extra} step exists only when the fixture key is enabled, so a
 * test proves the injection carries the run's configuration rather than a rebuilt default.
 */
public final class ConfiguredBuilderFactoryFixture {

    /** Configuration key that, when {@code true}, adds one more step to the definition. */
    public static final String EXTRA_STEP_KEY = "fixture.extraStep";

    private ConfiguredBuilderFactoryFixture() {
        // Intentionally empty
    }

    /**
     * Returns a definition whose structure depends on the supplied configuration.
     *
     * @param configuration the run's effective configuration
     * @return the builder the framework must build
     */
    @Paramixel.TaskFactory
    public static TaskBuilder<?> factory(final Configuration configuration) {
        final var root = Sequential.builder(ConfiguredBuilderFactoryFixture.class, "configured")
                .step("base");
        if (configuration.getBoolean(EXTRA_STEP_KEY).orElse(false)) {
            root.step("extra");
        }
        return root;
    }

    /**
     * Records the always-authored step.
     *
     * @param context the execution context
     */
    @Paramixel.Id("base")
    public static void base(final Context context) {
        DiscoveryFixtures.RECORDED.add("base");
    }

    /**
     * Records the configuration-dependent step.
     *
     * @param context the execution context
     */
    @Paramixel.Id("extra")
    public static void extra(final Context context) {
        DiscoveryFixtures.RECORDED.add("extra");
    }
}

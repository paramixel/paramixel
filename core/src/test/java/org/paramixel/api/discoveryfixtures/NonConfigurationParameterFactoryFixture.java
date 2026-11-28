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
 * An invalid {@link Paramixel.TaskFactory} whose single parameter is not a {@code Configuration}.
 */
public final class NonConfigurationParameterFactoryFixture {

    private NonConfigurationParameterFactoryFixture() {
        // Intentionally empty
    }

    /**
     * Declares an unsupported parameter type.
     *
     * @param value an unsupported parameter
     * @return the builder the framework would build
     */
    @Paramixel.TaskFactory
    public static TaskBuilder<?> factory(final String value) {
        return Sequential.builder(NonConfigurationParameterFactoryFixture.class, "invalid-" + value)
                .step("run");
    }

    /**
     * Records this fixture's execution.
     *
     * @param context the execution context
     */
    @Paramixel.Id("run")
    public static void run(final Context context) {
        DiscoveryFixtures.RECORDED.add("non-configuration-parameter");
    }
}

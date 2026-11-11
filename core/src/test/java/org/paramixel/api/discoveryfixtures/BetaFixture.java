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

import org.paramixel.api.Paramixel;
import org.paramixel.api.SequentialBuilder;
import org.paramixel.api.Tasks;

/**
 * A builder-returning, high-priority factory used by discovery tests.
 */
@Paramixel.Priority(10)
public final class BetaFixture {

    private BetaFixture() {
        // Intentionally empty
    }

    /**
     * Returns a builder that records its execution; a factory may return a builder instead of a task.
     *
     * @return the definition builder
     */
    @Paramixel.Factory
    @Paramixel.Tag("beta")
    public static SequentialBuilder<Void> factory() {
        return Tasks.task("beta").tags("beta").step("run", () -> DiscoveryFixtures.RECORDED.add("beta"));
    }
}

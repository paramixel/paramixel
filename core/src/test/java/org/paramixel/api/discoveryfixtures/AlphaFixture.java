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
import org.paramixel.api.Task;
import org.paramixel.api.Tasks;

/**
 * A tagged factory used by discovery tests.
 */
public final class AlphaFixture {

    private AlphaFixture() {
        // Intentionally empty
    }

    /**
     * Returns a definition that records its execution.
     *
     * @return the definition
     */
    @Paramixel.Factory
    @Paramixel.Tag("alpha")
    public static Task factory() {
        return Tasks.task("alpha")
                .tags("alpha")
                .step("run", () -> DiscoveryFixtures.RECORDED.add("alpha"))
                .build();
    }
}

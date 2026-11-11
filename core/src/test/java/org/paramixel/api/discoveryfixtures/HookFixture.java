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
 * Run-level hooks used by discovery tests.
 */
public final class HookFixture {

    private HookFixture() {
        // Intentionally empty
    }

    /**
     * Runs before the discovered factories.
     *
     * @return the hook definition
     */
    @Paramixel.BeforeAll
    public static Task beforeAll() {
        return Tasks.task("beforeAll")
                .step("run", () -> {
                    DiscoveryFixtures.RECORDED.add("beforeAll");
                    if (DiscoveryFixtures.failBeforeAll) {
                        throw new IllegalStateException("beforeAll failed");
                    }
                })
                .build();
    }

    /**
     * Runs after the discovered factories.
     *
     * @return the hook definition
     */
    @Paramixel.AfterAll
    public static Task afterAll() {
        return Tasks.task("afterAll")
                .step("run", () -> DiscoveryFixtures.RECORDED.add("afterAll"))
                .build();
    }
}

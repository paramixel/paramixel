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

import static org.paramixel.api.Tasks.sequential;

import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Task;

/**
 * Run-level hooks used by discovery tests.
 *
 * <p>The three definitions share one hook method and one step name. Each binds its own name, so the hook branches on
 * data instead of on a method reference, and every definition keeps the exact shape (and path) the discovery assertions
 * expect.
 */
public final class HookFixture {

    private static final String HOOK = "hook";

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
        return sequential(HookFixture.class, "beforeAll")
                .bind(HOOK, "beforeAll")
                .step("run")
                .build();
    }

    /**
     * Runs after the discovered factories.
     *
     * @return the hook definition
     */
    @Paramixel.AfterAll
    public static Task afterAll() {
        return sequential(HookFixture.class, "afterAll")
                .bind(HOOK, "afterAll")
                .step("run")
                .build();
    }

    /**
     * Runs before the normal after-all hook when the failure scenario is enabled.
     *
     * @return the failing hook definition, or {@code null}
     */
    @Paramixel.AfterAll
    public static Task failingAfterAll() {
        if (!DiscoveryFixtures.failAfterAll) {
            return null;
        }
        return sequential(HookFixture.class, "failingAfterAll")
                .bind(HOOK, "failingAfterAll")
                .step("run")
                .build();
    }

    /**
     * Runs one of the three hook definitions.
     *
     * @param context the execution context
     */
    @Paramixel.Id("run")
    public static void run(final Context context) {
        final var name = context.require(HOOK, String.class);
        DiscoveryFixtures.RECORDED.add(name);
        if ("beforeAll".equals(name) && DiscoveryFixtures.failBeforeAll) {
            throw new IllegalStateException("beforeAll failed");
        }
        if ("failingAfterAll".equals(name)) {
            throw new IllegalStateException("afterAll failed");
        }
    }
}

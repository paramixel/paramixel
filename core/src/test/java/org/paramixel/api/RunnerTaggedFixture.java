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

package org.paramixel.api;

import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;

/**
 * Fixture tagged {@code "smoke"} used to verify that the runner selects actions by tag when a tag
 * filter is active.
 */
public final class RunnerTaggedFixture {

    private RunnerTaggedFixture() {
        // Intentionally empty
    }

    /**
     * Creates a no-op action tagged {@code "smoke"} for runner tag-filtering tests.
     *
     * @return a {@link Step} action with the identifier {@code "runner-tagged-fixture"}
     */
    @Paramixel.Factory
    @Paramixel.Tag("smoke")
    public static Action<?> factory() {
        return Instance.<Object>of("runner-tagged-fixture", () -> new Object())
                .child("test", obj -> {})
                .resolve();
    }
}

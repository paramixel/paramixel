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

@Paramixel.Priority(10)
/**
 * A factory admitted after the fail-fast trigger; it records whether it ran.
 */
public final class FailFastFollower2Fixture {

    private FailFastFollower2Fixture() {
        // Intentionally empty
    }

    /**
     * Returns a definition that records its execution.
     *
     * @return the definition
     */
    @Paramixel.Factory
    public static Task factory() {
        return sequential(FailFastFollower2Fixture.class, "follower-2")
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
        DiscoveryFixtures.RECORDED.add("follower-2");
    }
}

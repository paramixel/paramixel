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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared recording state for discovery fixtures.
 */
public final class DiscoveryFixtures {

    /** Recorded lifecycle events. */
    public static final List<String> RECORDED = Collections.synchronizedList(new ArrayList<>());

    /** Whether the before-all hook should fail. */
    public static volatile boolean failBeforeAll;

    /** Whether to add an after-all hook that fails before the normal after-all hook. */
    public static volatile boolean failAfterAll;

    /** A task the close-during-discovery factory runs while it is being invoked, or {@code null}. */
    public static volatile Runnable factoryHook;

    /**
     * How the fail-fast trigger factory ends: {@code pass}, {@code skip}, {@code condition}, {@code abort},
     * {@code fail} or {@code timeout}.
     */
    public static volatile String failFastMode = "pass";

    private DiscoveryFixtures() {
        // Intentionally empty
    }

    /**
     * Clears the recorded state.
     */
    public static void reset() {
        RECORDED.clear();
        failBeforeAll = false;
        failAfterAll = false;
        factoryHook = null;
        failFastMode = "pass";
    }
}

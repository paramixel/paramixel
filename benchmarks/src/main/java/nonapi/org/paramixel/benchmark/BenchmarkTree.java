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

package nonapi.org.paramixel.benchmark;

import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;

/**
 * Anchor class and hook bodies for the synthetic trees the benchmarks build.
 *
 * <p>A benchmark only needs occurrences with bodies that do nothing (or sleep); naming this class as the anchor makes
 * every synthetic tree resolve the same two hooks, so the benchmark measures the engine rather than the authoring
 * surface.
 */
public final class BenchmarkTree {

    /** Identifier of a body that does nothing. */
    public static final String ID_NOOP = "noop";

    /** Identifier of a body that sleeps for one millisecond. */
    public static final String ID_SLEEP = "sleep";

    private BenchmarkTree() {
        // Intentionally empty
    }

    /**
     * A body that does nothing.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_NOOP)
    public static void noop(final Context context) {
        // Intentionally empty
    }

    /**
     * A body that sleeps for one millisecond.
     *
     * @param context the execution context
     * @throws InterruptedException if the callback is interrupted
     */
    @Paramixel.Id(ID_SLEEP)
    public static void sleep(final Context context) throws InterruptedException {
        Thread.sleep(1L);
    }
}

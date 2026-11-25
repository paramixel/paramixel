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

package examples.annotation.tags;

import static org.paramixel.api.Tasks.sequential;

import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Task;

/**
 * Example factory tagged as {@code "smoke"} for tag-based selection.
 *
 * <p>The annotation feeds discovery; the builder call feeds the compiled task's own metadata. Tags select suites, not
 * hooks, so the hook below is not tagged.
 */
public class SmokeTaggedTest {

    private static final String ID_TEST = "test";

    /**
     * Returns a definition tagged as {@code "smoke"}.
     *
     * @return the definition
     */
    @Paramixel.Factory
    @Paramixel.Tag("smoke")
    public static Task factory() {
        return sequential(SmokeTaggedTest.class, "SmokeTaggedTest")
                .tags("smoke")
                .step(ID_TEST)
                .build();
    }

    /**
     * Trivial body so the example stays focused on selection.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST)
    public static void test(final Context context) {
        // Intentionally empty
    }
}

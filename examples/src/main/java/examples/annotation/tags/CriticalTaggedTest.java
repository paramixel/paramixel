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

import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Sequential;
import org.paramixel.api.Task;

/**
 * Example factory tagged as both {@code "smoke-fast"} and {@code "critical"} for tag-based selection.
 */
public class CriticalTaggedTest {

    private static final String ID_TEST = "test";

    /**
     * Returns a definition tagged as both {@code "smoke-fast"} and {@code "critical"}.
     *
     * @return the definition
     */
    @Paramixel.TaskFactory
    @Paramixel.Tag("smoke-fast")
    @Paramixel.Tag("critical")
    public static Task factory() {
        return Sequential.builder(CriticalTaggedTest.class, "CriticalTaggedTest")
                .tags("smoke-fast", "critical")
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

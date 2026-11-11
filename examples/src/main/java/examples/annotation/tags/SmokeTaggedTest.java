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

import static org.paramixel.api.Tasks.forInstance;

import org.paramixel.api.Paramixel;
import org.paramixel.api.Task;

/**
 * Example factory tagged as {@code "smoke"} for tag-based selection.
 */
public class SmokeTaggedTest {

    /**
     * Returns a definition tagged as {@code "smoke"}.
     *
     * @return the definition
     */
    @Paramixel.Factory
    @Paramixel.Tag("smoke")
    public static Task factory() {
        return forInstance("SmokeTaggedTest", SmokeTaggedTest::new)
                .tags("smoke")
                .step("test", SmokeTaggedTest::test)
                .build();
    }

    /**
     * No-argument constructor used by the framework.
     */
    public SmokeTaggedTest() {
        // Intentionally empty
    }

    /**
     * Trivial body so the example stays focused on selection.
     */
    public void test() {
        // Intentionally empty
    }
}

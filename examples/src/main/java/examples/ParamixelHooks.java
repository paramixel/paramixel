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

package examples;

import org.paramixel.api.Paramixel;
import org.paramixel.api.Task;
import org.paramixel.api.Tasks;

/**
 * Demonstrates run-level before and after hooks.
 *
 * <p>Hooks are ordinary task definitions: {@code BeforeAll} runs before the discovered factories and gates them, and
 * {@code AfterAll} runs after the started run even when a discovered factory failed. Both are discovered globally, not
 * by the package selector, because they wrap the whole run.
 */
public final class ParamixelHooks {

    private ParamixelHooks() {
        // Intentionally empty
    }

    /**
     * Runs once before all discovered factories.
     *
     * @return the hook definition
     */
    @Paramixel.BeforeAll
    public static Task beforeAll() {
        return Tasks.task(ParamixelHooks.class.getSimpleName() + ".beforeAll")
                .step("run", () -> System.out.println(ParamixelHooks.class.getSimpleName() + ".beforeAll"))
                .build();
    }

    /**
     * Runs once after all discovered factories.
     *
     * @return the hook definition
     */
    @Paramixel.AfterAll
    public static Task afterAll() {
        return Tasks.task(ParamixelHooks.class.getSimpleName() + ".afterAll")
                .step("run", () -> System.out.println(ParamixelHooks.class.getSimpleName() + ".afterAll"))
                .build();
    }
}

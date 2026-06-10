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

import static org.paramixel.api.Context.withInstance;
import static org.paramixel.api.action.Instance.instance;
import static org.paramixel.api.action.Step.step;

import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;

/**
 * Example test class annotated with multiple tags ({@code "smoke-fast"} and
 * {@code "critical"}) to demonstrate tag-based test selection with compound tags.
 */
public class CriticalTaggedTest {

    /**
     * Returns a no-op action tagged as both {@code "smoke-fast"} and {@code "critical"}.
     *
     * @return a no-op action
     */
    @Paramixel.Factory
    @Paramixel.Tag("smoke-fast")
    @Paramixel.Tag("critical")
    public static Action factory() {
        return instance("CriticalTaggedTest", CriticalTaggedTest::new)
                .body(step("test()", withInstance(CriticalTaggedTest.class, CriticalTaggedTest::test)))
                .build();
    }

    public CriticalTaggedTest() {
        // Intentionally empty
    }

    public void test() {
        // Intentionally empty
    }
}

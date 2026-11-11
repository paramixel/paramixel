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

package examples.annotation;

import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;
import org.paramixel.api.Tasks;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.selector.Selector;

/**
 * Verifies that {@code @Paramixel.Disabled} prevents a factory from being invoked.
 *
 * <p>The disabled factory would fail if it ever ran, so a passing run proves discovery excluded it before invoking any
 * user code.
 */
public class DisabledTest {

    /**
     * Runs discovery in this package and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        System.exit(Runner.builder()
                .build()
                .run(Selector.builder().packageTreeOf(DisabledTest.class).build())
                .exitCode());
    }

    /**
     * A disabled factory that must never be invoked.
     *
     * @return a task that would fail if executed
     */
    @Paramixel.Disabled("covered by discovery skip behavior")
    @Paramixel.Factory
    public static Task factory() {
        return Tasks.task("[scenario]")
                .step("should-not-run", () -> {
                    throw FailException.fail("@Paramixel.Disabled factory was invoked");
                })
                .build();
    }
}

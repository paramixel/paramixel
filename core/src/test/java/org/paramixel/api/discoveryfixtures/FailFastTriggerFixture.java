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

import static org.paramixel.api.exception.AbortException.abort;
import static org.paramixel.api.exception.SkipException.skip;

import java.time.Duration;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Task;
import org.paramixel.api.Tasks;

/**
 * The first factory of the fail-fast scenarios; it ends as {@link DiscoveryFixtures#failFastMode} selects.
 */
@Paramixel.Priority(20)
public final class FailFastTriggerFixture {

    private FailFastTriggerFixture() {
        // Intentionally empty
    }

    /**
     * Returns a definition that passes, skips, aborts, fails or times out.
     *
     * @return the definition
     */
    @Paramixel.Factory
    public static Task factory() {
        final var mode = DiscoveryFixtures.failFastMode;
        final var trigger = Tasks.task("trigger");
        switch (mode) {
            case "condition" -> trigger.onlyIf(context -> false);
            case "timeout" -> trigger.timeout(Duration.ofMillis(50));
            default -> {
                // No extra declaration
            }
        }
        return trigger.step("run", () -> {
                    DiscoveryFixtures.RECORDED.add("trigger");
                    switch (mode) {
                        case "skip" -> throw skip("deliberately skipped");
                        case "abort" -> throw abort("assumption not met");
                        case "fail" -> throw new IllegalStateException("trigger failed");
                        case "timeout" -> Thread.sleep(5000L);
                        default -> {
                            // Passes
                        }
                    }
                })
                .build();
    }
}

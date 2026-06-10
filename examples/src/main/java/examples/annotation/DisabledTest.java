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

import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Step.step;

import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.selector.Selector;

/**
 * Verifies that {@code @Paramixel.Disabled} prevents the annotated action factory
 * from being invoked by the runner.
 */
public class DisabledTest {

    /**
     * Discovers and executes all action factories in this package, then exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(Selector.packageTreeOf(DisabledTest.class));
    }

    /**
     * Action factory annotated with {@code @Paramixel.Disabled} — must not be
     * invoked by the runner. If this factory is ever called, the test fails.
     *
     * @return a flow that would fail if ever executed
     */
    @Paramixel.Disabled("covered by resolver skip behavior")
    @Paramixel.Factory
    public static Action factory() {
        return scope("[scenario]")
                .body(step("should-not-run", context -> {
                    FailException.fail("@Paramixel.Disabled action factory was invoked");
                }))
                .build();
    }
}

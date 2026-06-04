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

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.Context.withInstance;

import java.util.concurrent.atomic.AtomicBoolean;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Step;

/**
 * Verifies that {@code @Paramixel.Disabled} prevents the annotated action factory
 * from being invoked by the runner. A separate verification factory asserts the
 * disabled factory was never called.
 */
public class DisabledTest {

    private static final AtomicBoolean factoryInvoked = new AtomicBoolean();

    /**
     * Runs the verification factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(disabledVerificationFactory());
    }

    /**
     * Verification factory that asserts the disabled factory was never invoked.
     *
     * @return a single-step action that checks the disabled flag
     */
    @Paramixel.Factory
    public static Action disabledVerificationFactory() {
        return Instance.builder("DisabledVerification", DisabledTest::new)
                .body(Step.of("verify()", withInstance(DisabledTest.class, DisabledTest::verify)))
                .build();
    }

    /**
     * Action factory annotated with {@code @Paramixel.Disabled} — must not be
     * invoked by the runner.
     *
     * @return a flow that would fail if ever executed
     */
    @Paramixel.Disabled("covered by resolver skip behavior")
    @Paramixel.Factory
    public static Action factory() {
        factoryInvoked.set(true);

        return Instance.builder("DisabledTest", DisabledTest::new)
                .body(Scope.<DisabledTest>builder("lifecycle")
                        .before(Step.of("before()", withInstance(DisabledTest.class, DisabledTest::before)))
                        .body(Step.of("disabledLeaf()", withInstance(DisabledTest.class, DisabledTest::disabledLeaf)))
                        .after(Step.of("after()", withInstance(DisabledTest.class, DisabledTest::after)))
                        .build())
                .build();
    }

    public DisabledTest() {
        // Intentionally empty
    }

    public void verify() {
        assertThat(factoryInvoked.get())
                .as("@Paramixel.Disabled must prevent the action factory from being invoked")
                .isFalse();
    }

    public void before() {
        throw new AssertionError("Disabled action must not execute");
    }

    public void disabledLeaf() {
        throw new AssertionError("Disabled action must not execute");
    }

    public void after() {
        throw new AssertionError("Disabled action must not execute");
    }
}

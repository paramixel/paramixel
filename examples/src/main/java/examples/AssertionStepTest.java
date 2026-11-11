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

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.exception.FailException.fail;

import nonapi.org.paramixel.action.ConcreteContext;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;

/**
 * Demonstrates the one way to express an assertion: a {@link org.paramixel.api.action.Step} that throws {@link
 * org.paramixel.api.exception.FailException} when the check does not hold.
 *
 * <p>Every check below holds, so the run passes. A failing check reports the step as {@code FAILED}
 * with the message from {@code fail(...)}.
 */
public class AssertionStepTest {

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds a tree of assertion steps.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        return sequential("assertion-steps")
                .child(step("one-equals-one", context -> {
                    if (1 != 1) {
                        throw fail("one is not equal to one");
                    }
                }))
                .child(step("two-is-even", context -> {
                    var value = 2;
                    if (value % 2 != 0) {
                        throw fail("expected an even value but was " + value);
                    }
                }))
                .child(step("report statuses", context -> {
                    var children = ConcreteContext.require(context)
                            .descriptor()
                            .parent()
                            .orElseThrow()
                            .children();
                    assertThat(children).isNotEmpty();
                    assertThat(children.get(0).status()).isSameAs(Status.PASSED);
                    assertThat(children.get(1).status().isPassed()).isTrue();
                }))
                .build();
    }
}

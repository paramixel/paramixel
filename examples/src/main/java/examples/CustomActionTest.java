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

import java.util.Objects;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Context;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Spec;
import org.paramixel.api.action.Step;

/**
 * Demonstrates implementing a custom {@link Action} that operates on a test
 * fixture instance managed by {@link Instance}. The {@link CustomAction} leaf
 * action retrieves the fixture from {@link Context#instance} and
 * sets a value; a sibling {@link Step} validates the value was set correctly.
 */
public class CustomActionTest {

    /**
     * The value set by {@link CustomAction} and validated by the sibling step.
     */
    String value;

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    /**
     * Builds an action tree where {@link CustomAction} sets a value on the
     * fixture instance and a sibling step validates it.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Spec<?> factory() {
        var testName = CustomActionTest.class.getName();

        return Instance.of(testName, CustomActionTest::new)
                .child(new CustomAction("test"))
                .child(Step.of("validate", CustomActionTest::validate));
    }

    /**
     * Asserts that the {@link CustomAction} set the expected value on this
     * fixture instance.
     */
    public void validate() {
        if (!Objects.equals(value, "set by CustomAction")) {
            throw new AssertionError("Expected <set by CustomAction> but was <" + value + ">");
        }
    }

    /**
     * A leaf {@link Action} that retrieves the fixture instance from the
     * execution context and sets a value on it. Demonstrates implementing
     * {@link Action} directly with listener callbacks and
     * {@link Context#instance} for accessing the parent
     * {@link Instance} fixture.
     */
    private static final class CustomAction implements Action<Void> {

        private final String name;

        /**
         * Creates a {@code CustomAction} with the given name.
         *
         * @param name the action display name; must not be {@code null} or blank
         * @throws NullPointerException if {@code name} is {@code null}
         * @throws IllegalArgumentException if {@code name} is blank
         */
        CustomAction(final String name) {
            Objects.requireNonNull(name, "name is null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name is blank");
            }
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String kind() {
            return "CustomAction";
        }

        @Override
        public void execute(final Context context) {
            Objects.requireNonNull(context);
            var descriptor = context.descriptor();
            var listener = context.listener();
            listener.onBeforeExecution(descriptor);
            context.setStatus(Status.RUNNING);
            try {
                var instance = context.instance(CustomActionTest.class).orElseThrow();
                instance.value = "set by CustomAction";
                context.setStatus(Status.PASSED);
            } catch (Throwable t) {
                context.setStatus(Status.fromThrowable(t));
            }
            listener.onAfterExecution(descriptor);
        }
    }
}

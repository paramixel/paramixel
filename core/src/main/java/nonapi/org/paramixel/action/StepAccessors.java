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

package nonapi.org.paramixel.action;

import java.util.Objects;
import java.util.function.Function;
import org.paramixel.api.ContextConsumer;
import org.paramixel.api.action.Step;

/**
 * Reads a {@link Step}'s executable without exposing it on the public API.
 *
 * <p>A public getter would be callable by users because {@code api} and {@code nonapi} share one module. Instead
 * {@code Step} registers this accessor from a static initializer, which is private to the framework by
 * convention and, once {@code module-info.java} lands, by module encapsulation.
 *
 * <p>An accessor can only be needed for a {@code Step} instance, and creating that instance initializes
 * {@link Step}, which registers the accessor, so no initialization ordering problem exists.
 */
public final class StepAccessors {

    private static volatile Function<Step, ContextConsumer> accessor;

    private StepAccessors() {
        // Intentionally empty
    }

    /**
     * Registers the accessor. Called once from {@link Step}'s static initializer.
     *
     * @param stepAccessor the accessor; must not be {@code null}
     */
    public static void register(final Function<Step, ContextConsumer> stepAccessor) {
        accessor = Objects.requireNonNull(stepAccessor, "stepAccessor is null");
    }

    /**
     * Returns the executable of the supplied step.
     *
     * @param step the step; must not be {@code null}
     * @return the executable; never {@code null}
     * @throws NullPointerException if {@code step} is {@code null}
     * @throws IllegalStateException if {@link Step} has not registered its accessor
     */
    public static ContextConsumer consumerOf(final Step step) {
        Objects.requireNonNull(step, "step is null");
        var current = accessor;
        if (current == null) {
            throw new IllegalStateException("Step accessor is not registered; Step was not initialized");
        }
        return Objects.requireNonNull(current.apply(step), "step consumer is null");
    }
}

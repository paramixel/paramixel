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

package nonapi.org.paramixel;

import java.util.Objects;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import org.paramixel.api.action.Action;

/**
 * Compiles an action tree and returns its descriptor tree.
 *
 * <p>A thin public entry point for code that lives outside {@code nonapi.org.paramixel} (the benchmarks) and needs
 * to measure or inspect compilation. The runner uses {@link CompiledPlan} directly, so this class adds no behavior.
 */
public final class CompiledPlans {

    private CompiledPlans() {
        // Intentionally empty
    }

    /**
     * Compiles the supplied action tree.
     *
     * @param action the root action; must not be {@code null}
     * @return the root descriptor of the compiled tree; never {@code null}
     * @throws NullPointerException if {@code action} is {@code null}
     * @throws IllegalStateException if an action type has no compilation branch
     */
    public static ConcreteDescriptor compile(final Action action) {
        return CompiledPlan.compile(Objects.requireNonNull(action, "action is null"))
                .root()
                .descriptor;
    }
}

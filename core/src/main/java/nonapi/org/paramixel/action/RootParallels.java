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
import org.paramixel.api.action.Parallel;

/**
 * Builds the runner's discovery fan-out, which is the only {@link Parallel} bound to the root pool.
 *
 * <p>{@link Parallel}'s public factory accepts only user pool names, so a user cannot name the root pool. The
 * framework still needs it: {@code Parallel} registers a factory here from its static initializer, exactly like
 * {@link StepAccessors}. {@link #create(String)} forces {@code Parallel} initialization when it has not run yet.
 */
public final class RootParallels {

    private static volatile Function<String, Parallel.Builder> factory;

    private RootParallels() {
        // Intentionally empty
    }

    /**
     * Registers the root-parallel factory. Called once from {@link Parallel}'s static initializer.
     *
     * @param rootFactory the factory from display name to builder; must not be {@code null}
     */
    public static void register(final Function<String, Parallel.Builder> rootFactory) {
        factory = Objects.requireNonNull(rootFactory, "rootFactory is null");
    }

    /**
     * Returns a builder for a {@link Parallel} that uses the runner's root pool.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return the builder; never {@code null}
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static Parallel.Builder create(final String displayName) {
        var current = factory;
        if (current == null) {
            initializeParallel();
            current = factory;
        }
        if (current == null) {
            throw new IllegalStateException("Parallel did not register the root-parallel factory");
        }
        return current.apply(displayName);
    }

    private static void initializeParallel() {
        try {
            Class.forName(Parallel.class.getName(), true, RootParallels.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Parallel class is missing", e);
        }
    }
}

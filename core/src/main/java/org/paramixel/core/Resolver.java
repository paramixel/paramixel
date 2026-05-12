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

package org.paramixel.core;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.paramixel.core.internal.DefaultConfiguration;
import org.paramixel.core.internal.DefaultResolver;

/**
 * Discovers {@link Action} instances from {@code @Paramixel.ActionFactory} methods.
 *
 * <p>The resolver scans the active classpath, filters candidate classes using optional {@link Selector} criteria and
 * configuration properties, invokes eligible action factory methods, and collapses multiple discovered actions into a
 * single root action using the framework's default parallel composition.
 */
public final class Resolver {

    private Resolver() {
        // Intentionally empty
    }

    /**
     * Resolves actions from the full classpath using {@link Configuration#defaultProperties()}.
     *
     * @return the resolved root action, or an empty {@link Optional} when no actions are discovered
     */
    public static Optional<Action> resolveActions() {
        return new DefaultResolver(new DefaultConfiguration(null)).resolveActions(null);
    }

    /**
     * Resolves actions matching the supplied selector using {@link Configuration#defaultProperties()}.
     *
     * @param selector the selector describing which classes and tags should match
     * @return the resolved root action, or an empty {@link Optional} when no actions are discovered
     * @throws NullPointerException if {@code selector} is {@code null}
     */
    public static Optional<Action> resolveActions(Selector selector) {
        return new DefaultResolver(new DefaultConfiguration(null))
                .resolveActions(Objects.requireNonNull(selector, "selector must not be null"));
    }

    /**
     * Resolves actions from the full classpath using the supplied configuration.
     *
     * @param configuration the configuration properties to use during discovery
     * @return the resolved root action, or an empty {@link Optional} when no actions are discovered
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public static Optional<Action> resolveActions(Map<String, String> configuration) {
        return new DefaultResolver(new DefaultConfiguration(
                        Objects.requireNonNull(configuration, "configuration must not be null")))
                .resolveActions(null);
    }

    /**
     * Resolves actions matching the supplied selector using the supplied configuration.
     *
     * @param configuration the configuration properties to use during discovery
     * @param selector the selector describing which classes and tags should match
     * @return the resolved root action, or an empty {@link Optional} when no actions are discovered
     * @throws NullPointerException if {@code configuration} or {@code selector} is {@code null}
     */
    public static Optional<Action> resolveActions(Map<String, String> configuration, Selector selector) {
        return new DefaultResolver(new DefaultConfiguration(
                        Objects.requireNonNull(configuration, "configuration must not be null")))
                .resolveActions(Objects.requireNonNull(selector, "selector must not be null"));
    }

    static Optional<Action> resolveActionFromClass(Class<?> clazz) {
        return new DefaultResolver(new DefaultConfiguration(null)).resolveActionFromClass(clazz);
    }

    static Optional<Action> resolveActionFromClass(
            Class<?> clazz, Pattern selectorTagPattern, Pattern configurationTagPattern) {
        return new DefaultResolver(new DefaultConfiguration(null))
                .resolveActionFromClass(clazz, selectorTagPattern, configurationTagPattern);
    }
}

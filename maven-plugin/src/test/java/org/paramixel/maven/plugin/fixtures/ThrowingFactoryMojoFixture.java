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

package org.paramixel.maven.plugin.fixtures;

import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;

/**
 * Fixture whose {@code @Paramixel.Factory} method always throws an {@link
 * IllegalStateException}, used to verify that the Maven mojo wraps factory exceptions as {@link
 * org.apache.maven.plugin.MojoExecutionException}.
 */
public final class ThrowingFactoryMojoFixture {

    private ThrowingFactoryMojoFixture() {}

    /**
     * Always throws to simulate a failing action factory.
     *
     * @return never returns normally
     * @throws IllegalStateException always, with message {@code "factory exploded"}
     */
    @Paramixel.Factory
    public static Action action() {
        throw new IllegalStateException("factory exploded");
    }
}

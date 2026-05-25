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

package org.paramixel.api;

import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;

/**
 * Fixture annotated with multiple {@code @Paramixel.Tag} values ({@code "smoke-fast"} and
 * {@code "critical"}) to verify that the resolver supports repeatable tag declarations.
 */
public final class ClasspathResolverMultiTagFixture {

    private ClasspathResolverMultiTagFixture() {
        // Intentionally empty
    }

    /**
     * Creates a no-op action with two tags for multi-tag resolver tests.
     *
     * @return a {@link Step} action with the identifier {@code "multi-tag-action"}
     */
    @Paramixel.Factory
    @Paramixel.Tag("smoke-fast")
    @Paramixel.Tag("critical")
    public static Action<?> factory() {
        return Instance.<Object>of("multi-tag-action", () -> new Object())
                .child("test", obj -> {})
                .resolve();
    }
}

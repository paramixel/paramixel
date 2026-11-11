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

package org.paramixel.api.discoveryfixtures;

import org.paramixel.api.Paramixel;
import org.paramixel.api.Task;

/**
 * A factory that returns {@code null}, which is the one defined meaning of a skipped factory.
 */
public final class SkippingFactoryFixture {

    private SkippingFactoryFixture() {
        // Intentionally empty
    }

    /**
     * Returns {@code null} to skip discovery.
     *
     * @return {@code null}
     */
    @Paramixel.Factory
    @Paramixel.Tag("skipping")
    public static Task factory() {
        return null;
    }
}

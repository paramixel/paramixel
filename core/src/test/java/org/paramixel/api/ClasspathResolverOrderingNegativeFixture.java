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
import org.paramixel.api.action.Step;

@Paramixel.Priority(-1)
public final class ClasspathResolverOrderingNegativeFixture {

    private ClasspathResolverOrderingNegativeFixture() {
        // Intentionally empty
    }

    @Paramixel.Factory
    public static Action factory() {
        return Step.of("negative", context -> {
            throw new UnsupportedOperationException("ordering fixture is resolved but not executed");
        });
    }
}

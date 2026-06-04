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
import org.paramixel.api.action.Step;

public final class ClasspathResolverInvalidBlankTagDiscoveryFixture {

    private ClasspathResolverInvalidBlankTagDiscoveryFixture() {}

    @Paramixel.Factory
    @Paramixel.Tag(" ")
    public static Action factory() {
        return Instance.builder("invalid-blank-tag-discovery", () -> new Object())
                .body(Step.of("test", context -> {}))
                .build();
    }
}

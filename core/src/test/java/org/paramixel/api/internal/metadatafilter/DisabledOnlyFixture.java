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

package org.paramixel.api.internal.metadatafilter;

import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;

/**
 * Fixture annotated with {@code @Paramixel.Disabled} to verify that the metadata filter excludes
 * actions marked as disabled regardless of other metadata.
 */
public final class DisabledOnlyFixture {

    private DisabledOnlyFixture() {}

    /**
     * Creates a no-op action marked as disabled for metadata filter exclusion tests.
     *
     * @return a {@link Step} action with the identifier {@code "disabled-action"}
     */
    @Paramixel.Factory
    @Paramixel.Disabled
    public static Action<?> factory() {
        return Instance.<Object>of("disabled-action", () -> new Object())
                .child("test", obj -> {})
                .resolve();
    }
}

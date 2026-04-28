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

package org.paramixel.core.action;

import java.time.Instant;
import org.paramixel.core.Context;
import org.paramixel.core.Result;

/**
 * An {@link AbstractAction} that completes without doing any work.
 *
 * <p>Useful for placeholder actions or for testing purposes.
 */
public final class Noop extends AbstractAction {

    private Noop(String name) {
        super(name);
    }

    /**
     * Creates a no-op action with the supplied name.
     *
     * @param name The action name; must not be null or blank.
     * @return A new no-op action.
     * @throws NullPointerException If {@code name} is null.
     * @throws IllegalArgumentException If {@code name} is blank.
     */
    public static Noop of(String name) {
        return new Noop(name);
    }

    @Override
    protected Result doExecute(Context context, Instant start) {
        return Result.pass(this, durationSince(start));
    }
}

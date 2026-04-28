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
import java.util.Objects;
import org.paramixel.core.Context;
import org.paramixel.core.Result;

/**
 * An {@link AbstractAction} that executes a provided {@link Executable} directly.
 */
public final class Direct extends AbstractAction {

    private final Executable executable;

    private Direct(String name, Executable executable) {
        super(name);
        this.executable = Objects.requireNonNull(executable, "executable must not be null");
    }

    /**
     * Creates a direct action.
     *
     * @param name The action name; must not be null.
     * @param executable The executable to run; must not be null.
     * @return A new Direct action.
     */
    public static Direct of(String name, Executable executable) {
        return new Direct(name, executable);
    }

    /**
     * Returns the executable.
     *
     * @return The executable.
     */
    public Executable executable() {
        return executable;
    }

    @Override
    protected Result doExecute(Context context, Instant start) throws Throwable {
        executable.execute(context);
        return Result.pass(this, durationSince(start));
    }
}

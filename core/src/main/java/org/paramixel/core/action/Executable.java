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

import org.paramixel.core.Context;

/**
 * A functional interface for a block of executable code within an action.
 */
@FunctionalInterface
public interface Executable {

    /**
     * Returns an executable that does nothing.
     *
     * @return A no-op executable.
     */
    static Executable noop() {
        return context -> {};
    }

    /**
     * Executes the code block.
     *
     * @param context The execution context; must not be null.
     * @throws Throwable If execution fails.
     */
    void execute(Context context) throws Throwable;
}

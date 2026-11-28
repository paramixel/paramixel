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

/**
 * One executable callback of a {@link Task}: a body operation or a lifecycle callback.
 *
 * <p>Callbacks are attached to their owning task and are invoked by the execution engine on a worker thread, in the
 * phase order the task defines. A hook declared with {@code @Paramixel.Id} is resolved to this type when the
 * definition is built; the resolved callback remembers its identifier, which is what the report labels it with.
 *
 * <p>Implementations must be safe for the concurrency the definition asks for. A callback on a direct child of a
 * {@code parallel} task may run concurrently with its siblings; they share no context scope, but they can read the
 * same bound fixture.
 */
@FunctionalInterface
public interface TaskCallback {

    /**
     * Executes the callback.
     *
     * @param context the read-only execution context of the occurrence
     * @throws Throwable if the callback fails
     */
    void execute(Context context) throws Throwable;
}

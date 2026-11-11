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
 * Creates a secondary scoped resource. Unlike a {@link TaskFactory}, a resource factory may read resources that were
 * acquired earlier in the same task scope.
 */
@FunctionalInterface
public interface ResourceFactory {

    /**
     * Creates the resource.
     *
     * @param context the read-only execution context, with resources acquired earlier in the same scope available
     * @return the resource; {@code null} is rejected and fails the task
     * @throws Throwable if creation fails
     */
    Object create(Context context) throws Throwable;
}

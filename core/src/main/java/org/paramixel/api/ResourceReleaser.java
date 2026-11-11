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
 * Releases a secondary scoped resource.
 *
 * <p>Release callbacks run in reverse acquisition order after the owning task's {@code after} callbacks, and after all
 * descendants have settled. A failing release does not prevent the remaining releases from being attempted.
 */
@FunctionalInterface
public interface ResourceReleaser {

    /**
     * Releases the resource.
     *
     * @param resource the acquired resource; never {@code null}
     * @param context the read-only execution context
     * @throws Throwable if release fails
     */
    void release(Object resource, Context context) throws Throwable;
}

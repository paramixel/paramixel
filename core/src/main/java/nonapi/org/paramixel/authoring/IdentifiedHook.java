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

package nonapi.org.paramixel.authoring;

/**
 * A callback that carries the identifier it was resolved from.
 *
 * <p>The engine prefers the identifier over the declaration position when it labels a phase or a failure, so a report
 * shows {@code before[setUp]} rather than {@code before[0]}. A callback with no identifier — a programmatic one — keeps
 * the positional label.
 */
public interface IdentifiedHook {

    /**
     * Returns the identifier the callback was resolved from.
     *
     * @return the identifier; never {@code null} or blank
     */
    String id();
}

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
 * The role a hook identifier is resolved for.
 *
 * <p>The role fixes the required signature: lifecycle hooks and bodies take one context and return nothing, while the
 * predicate roles return a {@code boolean} — with a status for {@code until}.
 */
public enum IdRole {

    /** A {@code before} hook: {@code void m(Context)}. */
    BEFORE("before"),

    /** A step body: {@code void m(Context)}. */
    STEP("step"),

    /** The body of the occurrence itself: {@code void m(Context)}. */
    BODY("body"),

    /** An {@code after} hook: {@code void m(Context)}. */
    AFTER("after"),

    /** A runtime condition: {@code boolean m(Context)}. */
    CONDITION("onlyIf"),

    /** A repeat predicate: {@code boolean m(Context, Status)}. */
    UNTIL("until");

    private final String label;

    IdRole(final String label) {
        this.label = label;
    }

    /**
     * Returns the DSL label used in diagnostics.
     *
     * @return the label; never {@code null}
     */
    public String label() {
        return label;
    }

    /**
     * Returns whether this role's hook returns a {@code boolean}.
     *
     * @return {@code true} for the predicate roles
     */
    public boolean isPredicate() {
        return this == CONDITION || this == UNTIL;
    }
}

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

import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.support.Arguments;
import org.paramixel.core.support.FastId;

/**
 * Provides generated identifiers and name validation shared by framework-provided action types.
 *
 * <p>This class provides generated identifiers and name validation shared by framework-provided action types.
 * Subclasses are responsible for implementing run and skip behavior.
 */
public abstract class AbstractAction implements Action {

    /**
     * The generated stable identifier for this action.
     */
    protected final String id;

    /**
     * The display name for this action.
     */
    protected String name;

    /**
     * Creates an action with a generated stable identifier.
     */
    protected AbstractAction() {
        this.id = FastId.generateId();
    }

    /**
     * Validates and returns an action name.
     *
     * @param name the name to validate
     * @return the validated name
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    protected String validateName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        return name;
    }

    /**
     * Performs post-construction initialization.
     *
     * <p>Subclasses may override this hook when a factory method needs to finish setup after construction.
     */
    protected void initialize() {
        // Intentionally empty
    }

    /**
     * Returns the generated identifier for this action.
     *
     * @return the action identifier
     */
    @Override
    public final String getId() {
        return id;
    }

    /**
     * Returns the configured display name for this action.
     *
     * @return the action name
     */
    @Override
    public final String getName() {
        return name;
    }
}

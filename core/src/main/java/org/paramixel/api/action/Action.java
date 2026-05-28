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

package org.paramixel.api.action;

import java.util.List;
import java.util.Optional;
import org.paramixel.api.Runner;

/**
 * Defines a reusable execution unit processed by a {@link Runner}.
 *
 * <p>Actions are reusable definitions. Discovery binds an action occurrence to a descriptor,
 * and execution uses a {@link Context} for descriptor state, listener access, and
 * fixture instances.
 *
 * @param <T> the type consumed by the action
 */
public interface Action<T> extends Spec<T> {

    /**
     * Returns the display name used in console output and reports.
     *
     * @return the action display name
     */
    String name();

    /**
     * Returns the kind for this action, used as the human-readable category in
     * console output and reports.
     *
     * <p>Built-in actions return their simple name (e.g. {@code "Step"},
     * {@code "Lifecycle"}). Custom actions must implement this method to declare
     * their own kind.
     *
     * @return the action kind; never {@code null} or blank
     */
    String kind();

    /**
     * Returns the before-action, if this action declares one.
     *
     * <p>Composite actions such as {@link Lifecycle}, {@link Static}, and {@link Instance}
     * override this method to return their before-action. Leaf and decorator actions
     * inherit the default, which returns an empty optional.
     *
     * @return the before-action, or empty if none
     */
    default Optional<Action<?>> before() {
        return Optional.empty();
    }

    /**
     * Returns the body or contained child actions.
     *
     * <p>Composite actions override this method to return their children. Leaf actions
     * inherit the default, which returns an empty list.
     *
     * @return the child actions; never {@code null}
     */
    default List<Action<?>> children() {
        return List.of();
    }

    /**
     * Returns the after-action, if this action declares one.
     *
     * <p>Composite actions such as {@link Lifecycle}, {@link Static}, and {@link Instance}
     * override this method to return their after-action. Leaf and decorator actions
     * inherit the default, which returns an empty optional.
     *
     * @return the after-action, or empty if none
     */
    default Optional<Action<?>> after() {
        return Optional.empty();
    }

    /**
     * Executes this action using the supplied execution context.
     *
     * <p>Implementations are responsible for descriptor status transitions and before/after
     * listener callbacks for their own execution boundary.
     *
     * @param context the active execution context; must not be {@code null}
     */
    void execute(Context context);

    @Override
    default Action<T> resolve() {
        return this;
    }
}

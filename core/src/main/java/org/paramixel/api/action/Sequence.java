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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import nonapi.org.paramixel.support.Arguments;

/**
 * An action that executes its children sequentially in the order they were added.
 *
 * <p>When configured as <em>dependent</em> (the default), a failure in any child causes
 * remaining children to be skipped or aborted. When configured as <em>independent</em>,
 * all children run regardless of individual outcomes.
 */
public final class Sequence implements Action {

    private final String displayName;
    private final List<Action> children;
    private final boolean dependent;

    private Sequence(final String displayName, final List<Action> children, final boolean dependent) {
        Objects.requireNonNull(displayName, "displayName is null");
        this.displayName = Arguments.requireNonBlank(displayName, "displayName is blank");
        this.children = validateChildren(children);
        this.dependent = dependent;
    }

    /**
     * Creates a new builder for a {@code Sequence} action with the given display name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return a new builder
     */
    public static Builder builder(final String displayName) {
        return new Builder(displayName);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Returns whether children are dependent — i.e., a failure in one child causes
     * remaining children to be skipped or aborted.
     *
     * @return {@code true} if children are dependent
     */
    public boolean isDependent() {
        return dependent;
    }

    /**
     * Returns whether children are independent — i.e., all run regardless of
     * individual outcomes.
     *
     * @return {@code true} if children are independent
     */
    public boolean isIndependent() {
        return !dependent;
    }

    /**
     * Returns the child actions.
     *
     * @return the child actions; never {@code null}
     */
    public List<Action> children() {
        return children;
    }

    private List<Action> validateChildren(final List<Action> children) {
        Objects.requireNonNull(children, "children is null");
        var validated = new ArrayList<Action>(children.size());
        for (Action child : children) {
            Objects.requireNonNull(child, "children contains null element");
            validated.add(child);
        }
        return List.copyOf(validated);
    }

    /**
     * Fluent builder for {@link Sequence} actions.
     */
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final String displayName;
        private final List<Action> children = new ArrayList<>();
        private boolean dependent = true;

        private Builder(final String displayName) {
            Objects.requireNonNull(displayName, "displayName is null");
            Arguments.requireNonBlank(displayName, "displayName is blank");
            this.displayName = displayName;
        }

        /**
         * Configures the sequence action as dependent, so that a failure in any child
         * causes remaining children to be skipped or aborted. This is the default.
         *
         * @return this builder
         */
        public Builder dependent() {
            dependent = true;
            return this;
        }

        /**
         * Configures the sequence action as independent, so that all children run
         * regardless of individual outcomes.
         *
         * @return this builder
         */
        public Builder independent() {
            dependent = false;
            return this;
        }

        /**
         * Adds a child action.
         *
         * @param action the child action; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder child(final Action action) {
            children.add(Objects.requireNonNull(action, "action is null"));
            return this;
        }

        /**
         * Adds a child action from a builder. The builder is built immediately
         * and the resulting action snapshot is stored.
         *
         * @param builder the child action builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code builder} is {@code null}
         */
        public Builder child(final org.paramixel.api.action.Builder builder) {
            Objects.requireNonNull(builder, "builder is null");
            children.add(Objects.requireNonNull(builder.build(), "builder.build() returned null"));
            return this;
        }

        /**
         * Builds the sequence action.
         *
         * @return a new sequence action
         */
        @Override
        public Sequence build() {
            return new Sequence(displayName, List.copyOf(children), dependent);
        }
    }
}

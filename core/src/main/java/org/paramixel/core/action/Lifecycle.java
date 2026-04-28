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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.SkipException;

/**
 * An {@link AbstractAction} that manages setup, execution, and teardown phases.
 */
public final class Lifecycle extends AbstractAction {

    private final Executable setup;
    private final Action body;
    private final Executable teardown;

    private Lifecycle(String name, Executable setup, Action body, Executable teardown) {
        super(name);
        this.setup = setup;
        this.body = Objects.requireNonNull(body, "body must not be null");
        this.teardown = teardown;
        adopt(body);
    }

    /**
     * Creates a lifecycle action with only a body.
     *
     * @param name The action name; must not be null.
     * @param body The body action; must not be null.
     * @return A new Lifecycle action.
     */
    public static Lifecycle of(String name, Action body) {
        return new Lifecycle(name, null, body, null);
    }

    /**
     * Creates a lifecycle action with a setup and body.
     *
     * @param name The action name; must not be null.
     * @param setup The setup executable; may be null.
     * @param body The body action; must not be null.
     * @return A new Lifecycle action.
     */
    public static Lifecycle of(String name, Executable setup, Action body) {
        return new Lifecycle(name, setup, body, null);
    }

    /**
     * Creates a lifecycle action with a body and teardown.
     *
     * @param name The action name; must not be null.
     * @param body The body action; must not be null.
     * @param teardown The teardown executable; may be null.
     * @return A new Lifecycle action.
     */
    public static Lifecycle of(String name, Action body, Executable teardown) {
        return new Lifecycle(name, null, body, teardown);
    }

    /**
     * Creates a lifecycle action with setup, body, and teardown.
     *
     * @param name The action name; must not be null.
     * @param setup The setup executable; may be null.
     * @param body The body action; must not be null.
     * @param teardown The teardown executable; may be null.
     * @return A new Lifecycle action.
     */
    public static Lifecycle of(String name, Executable setup, Action body, Executable teardown) {
        return new Lifecycle(name, setup, body, teardown);
    }

    /**
     * Returns the setup executable.
     *
     * @return An {@link Optional} containing the setup, or empty if not set.
     */
    public Optional<Executable> setup() {
        return Optional.ofNullable(setup);
    }

    /**
     * Returns the body action.
     *
     * @return The body action.
     */
    public Action body() {
        return body;
    }

    /**
     * Returns the teardown executable.
     *
     * @return An {@link Optional} containing the teardown, or empty if not set.
     */
    public Optional<Executable> teardown() {
        return Optional.ofNullable(teardown);
    }

    @Override
    public List<Action> children() {
        return List.of(body);
    }

    @Override
    protected Result doExecute(Context context, Instant start) throws Throwable {
        Throwable primaryFailure = null;
        boolean setupPassed = false;
        Throwable skipReason = null;

        try {
            if (setup != null) {
                setup.execute(context);
            }
            setupPassed = true;
        } catch (SkipException e) {
            skipReason = e;
        } catch (Throwable t) {
            primaryFailure = t;
        }

        List<Result> children = new ArrayList<>();
        if (setupPassed) {
            Result bodyResult = context.execute(body);
            children.add(bodyResult);
            if (bodyResult.status() == Result.Status.FAIL) {
                primaryFailure = bodyResult.failure().orElse(null);
            }
        } else {
            children.add(createSkipResult(body, skipReason));
        }

        if (teardown != null) {
            try {
                teardown.execute(context);
            } catch (Throwable t) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(t);
                } else {
                    primaryFailure = t;
                }
            }
        }

        if (primaryFailure != null) {
            return Result.of(this, Result.Status.FAIL, durationSince(start), primaryFailure, children);
        } else if (!setupPassed) {
            return Result.of(this, Result.Status.SKIP, durationSince(start), skipReason, children);
        } else {
            return Result.of(this, Result.Status.PASS, durationSince(start), null, children);
        }
    }

    private static Result createSkipResult(Action action, Throwable skipReason) {
        List<Result> children = new ArrayList<>();
        for (Action child : action.children()) {
            children.add(createSkipResult(child, skipReason));
        }
        return Result.of(action, Result.Status.SKIP, Duration.ZERO, skipReason, children);
    }
}

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

import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.paramixel.api.Status;
import org.paramixel.api.internal.support.Arguments;
import org.paramixel.spi.action.ExecutionContext;
import org.paramixel.spi.action.Mode;

/**
 * Leaf action that evaluates a boolean condition and passes when it is
 * {@code true}, failing when it is {@code false}.
 *
 * <p>Static conditions are created via {@link #of(String, boolean)} and
 * {@link #of(String, boolean, String)}. Dynamic conditions evaluated lazily
 * on each execution are created via {@link #of(String, BooleanSupplier)} and
 * {@link #of(String, BooleanSupplier, String)}.
 *
 * <p>When the condition evaluates to {@code true}, the action passes. When the
 * condition evaluates to {@code false}, the action fails. If a message is
 * provided and the condition is false, the message is included in the failed
 * status. If the {@code BooleanSupplier} throws, the throwable takes precedence
 * over any custom message.
 *
 * <p>Non-{@link Mode#RUN} modes short-circuit without evaluating the condition.
 *
 * @see AssertFalse
 */
public final class AssertTrue implements Action<Void> {

    private static final String KIND = "AssertTrue";

    private final String name;
    private final BooleanSupplier conditionSupplier;
    private final String message;

    private AssertTrue(final String name, final BooleanSupplier conditionSupplier, final String message) {
        this.name = Arguments.requireValidName(name);
        this.conditionSupplier = Objects.requireNonNull(conditionSupplier, "conditionSupplier must not be null");
        this.message = message;
    }

    /**
     * Creates an assert-true action with a static boolean condition.
     *
     * @param name the action name; must not be {@code null} or blank
     * @param condition the condition to evaluate
     * @return the assert-true action
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static AssertTrue of(final String name, final boolean condition) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        return new AssertTrue(name, () -> condition, null);
    }

    /**
     * Creates an assert-true action with a static boolean condition and a failure message.
     *
     * @param name the action name; must not be {@code null} or blank
     * @param condition the condition to evaluate
     * @param message the message included in the failed status when the condition is false;
     *     must not be {@code null} or blank
     * @return the assert-true action
     * @throws NullPointerException if {@code name} or {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code name} or {@code message} is blank
     */
    public static AssertTrue of(final String name, final boolean condition, final String message) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(message, "message must not be null");
        Arguments.requireNonBlank(message, "message must not be blank");
        return new AssertTrue(name, () -> condition, message);
    }

    /**
     * Creates an assert-true action with a lazy boolean supplier.
     *
     * @param name the action name; must not be {@code null} or blank
     * @param supplier the supplier that provides the condition; must not be {@code null}
     * @return the assert-true action
     * @throws NullPointerException if {@code name} or {@code supplier} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static AssertTrue of(final String name, final BooleanSupplier supplier) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(supplier, "supplier must not be null");
        return new AssertTrue(name, supplier, null);
    }

    /**
     * Creates an assert-true action with a lazy boolean supplier and a failure message.
     *
     * @param name the action name; must not be {@code null} or blank
     * @param supplier the supplier that provides the condition; must not be {@code null}
     * @param message the message included in the failed status when the condition is false;
     *     must not be {@code null} or blank
     * @return the assert-true action
     * @throws NullPointerException if {@code name}, {@code supplier}, or {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code name} or {@code message} is blank
     */
    public static AssertTrue of(final String name, final BooleanSupplier supplier, final String message) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(supplier, "supplier must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Arguments.requireNonBlank(message, "message must not be blank");
        return new AssertTrue(name, supplier, message);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public void execute(final ExecutionContext context) {
        Objects.requireNonNull(context, "context must not be null");
        var descriptor = context.descriptor();
        var listener = context.listener();
        listener.onBeforeExecution(descriptor);
        context.setStatus(Status.RUNNING);
        try {
            var mode = descriptor.metadata().mode();
            if (mode != Mode.RUN) {
                context.setStatus(mode.toStatus());
            } else if (conditionSupplier.getAsBoolean()) {
                context.setStatus(Status.PASSED);
            } else if (message != null) {
                context.setStatus(Status.failed(message));
            } else {
                context.setStatus(Status.FAILED);
            }
        } catch (Throwable t) {
            context.setStatus(Status.fromThrowable(t));
        }
        listener.onAfterExecution(descriptor);
    }
}

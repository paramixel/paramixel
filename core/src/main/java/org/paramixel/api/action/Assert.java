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
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.ContextConsumer;
import org.paramixel.api.exception.FailException;

/**
 * Terminal action that evaluates a boolean value against an expected boolean value.
 *
 * <p>Static values are created via {@link #of(String, boolean, boolean)} and
 * {@link #of(String, boolean, boolean, String)}. Dynamic values evaluated lazily
 * on each execution are created via {@link #of(String, boolean, BooleanSupplier)} and
 * {@link #of(String, boolean, BooleanSupplier, String)}.
 *
 * <p>When the actual value equals the expected value, the action passes. When
 * the actual value differs from the expected value, the action fails. If a
 * message is provided, the message is included in the failed status. If the
 * {@code BooleanSupplier} throws, the throwable takes precedence over any
 * custom message.
 *
 * <p>Skipped executions short-circuit without evaluating the actual value.
 */
public final class Assert implements Action {

    private final String displayName;
    private final boolean expected;
    private final BooleanSupplier actualSupplier;
    private final String message;

    private Assert(
            final String displayName,
            final boolean expected,
            final BooleanSupplier actualSupplier,
            final String message) {
        Objects.requireNonNull(displayName, "displayName is null");
        this.displayName = Arguments.requireNonBlank(displayName, "displayName is blank");
        this.expected = expected;
        this.actualSupplier = Objects.requireNonNull(actualSupplier, "actualSupplier is null");
        this.message = message;
    }

    /**
     * Creates an assert action with a static actual value.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @param expected the expected value
     * @param actual the actual value
     * @return the assert action
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static Assert of(final String displayName, final boolean expected, final boolean actual) {
        Objects.requireNonNull(displayName, "displayName is null");
        Arguments.requireNonBlank(displayName, "displayName is blank");
        return new Assert(displayName, expected, () -> actual, null);
    }

    /**
     * Creates an assert action with a static actual value and a failure message.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @param expected the expected value
     * @param actual the actual value
     * @param message the message included in the failed status when values differ; must not be
     *     {@code null} or blank
     * @return the assert action
     * @throws NullPointerException if {@code displayName} or {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} or {@code message} is blank
     */
    public static Assert of(
            final String displayName, final boolean expected, final boolean actual, final String message) {
        Objects.requireNonNull(displayName, "displayName is null");
        Arguments.requireNonBlank(displayName, "displayName is blank");
        Objects.requireNonNull(message, "message is null");
        Arguments.requireNonBlank(message, "message is blank");
        return new Assert(displayName, expected, () -> actual, message);
    }

    /**
     * Creates an assert action with a lazy actual value supplier.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @param expected the expected value
     * @param actualSupplier the supplier that provides the actual value; must not be {@code null}
     * @return the assert action
     * @throws NullPointerException if {@code displayName} or {@code actualSupplier} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static Assert of(final String displayName, final boolean expected, final BooleanSupplier actualSupplier) {
        Objects.requireNonNull(displayName, "displayName is null");
        Arguments.requireNonBlank(displayName, "displayName is blank");
        Objects.requireNonNull(actualSupplier, "actualSupplier is null");
        return new Assert(displayName, expected, actualSupplier, null);
    }

    /**
     * Creates an assert action with a lazy actual value supplier and a failure message.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @param expected the expected value
     * @param actualSupplier the supplier that provides the actual value; must not be {@code null}
     * @param message the message included in the failed status when values differ; must not be
     *     {@code null} or blank
     * @return the assert action
     * @throws NullPointerException if {@code displayName}, {@code actualSupplier}, or {@code message}
     *     is {@code null}
     * @throws IllegalArgumentException if {@code displayName} or {@code message} is blank
     */
    public static Assert of(
            final String displayName,
            final boolean expected,
            final BooleanSupplier actualSupplier,
            final String message) {
        Objects.requireNonNull(displayName, "displayName is null");
        Arguments.requireNonBlank(displayName, "displayName is blank");
        Objects.requireNonNull(actualSupplier, "actualSupplier is null");
        Objects.requireNonNull(message, "message is null");
        Arguments.requireNonBlank(message, "message is blank");
        return new Assert(displayName, expected, actualSupplier, message);
    }

    /**
     * Creates an assert action with a static actual value.
     *
     * @param name the action display name; must not be {@code null} or blank
     * @param expected the expected value
     * @param actual the actual value
     * @return a new assert action; never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static Assert assertThat(final String name, final boolean expected, final boolean actual) {
        return of(name, expected, actual);
    }

    /**
     * Creates an assert action with a static actual value and a failure message.
     *
     * @param name the action display name; must not be {@code null} or blank
     * @param expected the expected value
     * @param actual the actual value
     * @param message the message included in the failed status when values differ; must not be
     *     {@code null} or blank
     * @return a new assert action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code name} or {@code message} is blank
     */
    public static Assert assertThat(
            final String name, final boolean expected, final boolean actual, final String message) {
        return of(name, expected, actual, message);
    }

    /**
     * Creates an assert action with a lazy actual value supplier.
     *
     * @param name the action display name; must not be {@code null} or blank
     * @param expected the expected value
     * @param actualSupplier the supplier that provides the actual value; must not be {@code null}
     * @return a new assert action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code actualSupplier} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static Assert assertThat(final String name, final boolean expected, final BooleanSupplier actualSupplier) {
        return of(name, expected, actualSupplier);
    }

    /**
     * Creates an assert action with a lazy actual value supplier and a failure message.
     *
     * @param name the action display name; must not be {@code null} or blank
     * @param expected the expected value
     * @param actualSupplier the supplier that provides the actual value; must not be {@code null}
     * @param message the message included in the failed status when values differ; must not be
     *     {@code null} or blank
     * @return a new assert action; never {@code null}
     * @throws NullPointerException if {@code name}, {@code actualSupplier}, or {@code message}
     *     is {@code null}
     * @throws IllegalArgumentException if {@code name} or {@code message} is blank
     */
    public static Assert assertThat(
            final String name, final boolean expected, final BooleanSupplier actualSupplier, final String message) {
        return of(name, expected, actualSupplier, message);
    }

    /**
     * Shorthand for {@code assertThat(name, true, actual)}.
     *
     * @param name the action display name; must not be {@code null} or blank
     * @param actual the actual value
     * @return a new assert action; never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static Assert assertTrue(final String name, final boolean actual) {
        return assertThat(name, true, actual);
    }

    /**
     * Shorthand for {@code assertThat(name, true, actualSupplier)}.
     *
     * @param name the action display name; must not be {@code null} or blank
     * @param actualSupplier the supplier that provides the actual value; must not be {@code null}
     * @return a new assert action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code actualSupplier} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static Assert assertTrue(final String name, final BooleanSupplier actualSupplier) {
        return assertThat(name, true, actualSupplier);
    }

    /**
     * Shorthand for {@code assertThat(name, false, actual)}.
     *
     * @param name the action display name; must not be {@code null} or blank
     * @param actual the actual value
     * @return a new assert action; never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static Assert assertFalse(final String name, final boolean actual) {
        return assertThat(name, false, actual);
    }

    /**
     * Shorthand for {@code assertThat(name, false, actualSupplier)}.
     *
     * @param name the action display name; must not be {@code null} or blank
     * @param actualSupplier the supplier that provides the actual value; must not be {@code null}
     * @return a new assert action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code actualSupplier} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static Assert assertFalse(final String name, final BooleanSupplier actualSupplier) {
        return assertThat(name, false, actualSupplier);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the assertion executable.
     *
     * @return the assertion executable; never {@code null}
     */
    public ContextConsumer throwableConsumer() {
        return context -> {
            Objects.requireNonNull(context, "context is null");
            if (actualSupplier.getAsBoolean() != expected) {
                if (message != null) {
                    FailException.fail(message);
                }
                FailException.fail();
            }
        };
    }
}

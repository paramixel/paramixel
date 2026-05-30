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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import nonapi.org.paramixel.FrameworkException;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Status;

/**
 * Leaf action that pauses execution for a fixed or random duration.
 *
 * <p>Fixed-duration delays are created via {@link #of(String, long)} and
 * {@link #of(String, Duration)}. Random-duration delays are created via
 * {@link #random(String, long, long)}, which draws a fresh value from
 * {@link ThreadLocalRandom} on each execution.
 *
 * <p>If the delaying thread is interrupted, the interrupt flag is restored and
 * the action fails with {@link Status#FAILED}. Non-{@link Mode#RUN} modes
 * short-circuit without delaying.
 */
public final class Delay implements Action<Void> {

    private static final String KIND = "Delay";

    private final String name;
    private final LongSupplier millisecondsSupplier;

    private Delay(final String name, final LongSupplier millisecondsSupplier) {
        Objects.requireNonNull(name, "name is null");
        this.name = Arguments.requireNonBlank(name, "name is blank");
        this.millisecondsSupplier = Objects.requireNonNull(millisecondsSupplier, "millisecondsSupplier is null");
    }

    /**
     * Creates a delay action with a fixed duration in milliseconds.
     *
     * @param name the action name; must not be {@code null} or blank
     * @param milliseconds the duration to delay; must not be negative
     * @return the delay action
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code milliseconds} is negative
     */
    public static Delay of(final String name, final long milliseconds) {
        Objects.requireNonNull(name, "name is null");
        Arguments.requireNonBlank(name, "name is blank");
        Arguments.requireNonNegative(milliseconds, "milliseconds is negative");
        return new Delay(name, () -> milliseconds);
    }

    /**
     * Creates a delay action with a fixed duration.
     *
     * @param name the action name; must not be {@code null} or blank
     * @param duration the duration to delay; must not be {@code null} or negative
     * @return the delay action
     * @throws NullPointerException if {@code name} or {@code duration} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code duration} is negative
     */
    public static Delay of(final String name, final Duration duration) {
        Objects.requireNonNull(name, "name is null");
        Arguments.requireNonBlank(name, "name is blank");
        Objects.requireNonNull(duration, "duration is null");
        Arguments.requireNonNegative(duration.toMillis(), "duration is negative");
        return new Delay(name, duration::toMillis);
    }

    /**
     * Creates a delay action that pauses for a random duration between
     * {@code minimumMilliseconds} and {@code maximumMilliseconds} (inclusive)
     * on each execution.
     *
     * @param name the action name; must not be {@code null} or blank
     * @param minimumMilliseconds the minimum duration; must not be negative
     * @param maximumMilliseconds the maximum duration; must not be less than {@code minimumMilliseconds}
     * @return the delay action
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank, {@code minimumMilliseconds} is negative,
     *     or {@code maximumMilliseconds} is less than {@code minimumMilliseconds}
     */
    public static Delay random(final String name, final long minimumMilliseconds, final long maximumMilliseconds) {
        Objects.requireNonNull(name, "name is null");
        Arguments.requireNonBlank(name, "name is blank");
        Arguments.requireNonNegative(minimumMilliseconds, "minimumMilliseconds is negative");
        Arguments.requireTrue(
                maximumMilliseconds >= minimumMilliseconds, "maximumMilliseconds is less than minimumMilliseconds");
        if (maximumMilliseconds < Long.MAX_VALUE) {
            return new Delay(
                    name, () -> ThreadLocalRandom.current().nextLong(minimumMilliseconds, maximumMilliseconds + 1));
        }
        return new Delay(name, () -> ThreadLocalRandom.current().nextLong(minimumMilliseconds, Long.MAX_VALUE));
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
    public void execute(final Context context) {
        Objects.requireNonNull(context, "context is null");
        var descriptor = context.descriptor();
        var listener = context.listener();
        listener.onBeforeExecution(descriptor);
        context.setStatus(Status.RUNNING);
        try {
            var mode = descriptor.metadata().mode();
            if (mode != Mode.RUN) {
                context.setStatus(mode.toStatus());
            } else {
                Thread.sleep(millisecondsSupplier.getAsLong());
                context.setStatus(Status.PASSED);
            }
        } catch (Throwable t) {
            context.setStatus(Status.fromThrowable(FrameworkException.wrap(t)));
        }
        listener.onAfterExecution(descriptor);
    }
}

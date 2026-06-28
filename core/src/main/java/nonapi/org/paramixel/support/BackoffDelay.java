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

package nonapi.org.paramixel.support;

/**
 * Thread-confined multiplicative backoff for managed-blocking work-stealing loops.
 *
 * <p>Computes the next {@code parkNanos} delay for a loop that repeatedly polls a queue and parks
 * when it is momentarily empty. The delay starts at the floor on construction and after each
 * {@link #reset()}, grows by the configured growth factor on every consecutive empty poll, and
 * clamps at the ceiling. This bounds CPU (the empty-poll cadence eventually falls to roughly one
 * poll per ceiling-duration) while keeping first-touch latency at the floor for transient empties.
 *
 * <p><b>Not thread-safe.</b> Intended for single-thread, per-invocation use as a thread-confined
 * local variable; each blocking call should construct its own instance. All state is confined to
 * the owning thread, so no synchronization is required or wanted. This deliberate, contained
 * mutation is the intended design.
 *
 * <p>This class performs no timing, sleeping, or parking itself — it is pure delay-computation
 * logic, which makes it deterministically unit-testable. The caller performs the actual park via
 * {@code LockSupport.parkNanos(long)} with the returned delay.
 */
public final class BackoffDelay {

    private final long floorNanos;
    private final long ceilingNanos;
    private final double growthFactor;

    private long current;

    /**
     * Creates a new backoff with the given bounds and growth. No defaults are provided; callers
     * supply their own named constants.
     *
     * @param floorNanos the initial delay and the value {@link #reset()} returns to; must be
     *     positive
     * @param ceilingNanos the hard upper bound on any delay returned; must be
     *     {@code >= floorNanos}
     * @param growthFactor the multiplicative growth applied per consecutive empty poll; must be
     *     strictly greater than {@code 1.0} (also rejects {@code NaN})
     * @throws IllegalArgumentException if {@code floorNanos <= 0}, if
     *     {@code ceilingNanos < floorNanos}, or if {@code growthFactor <= 1.0} (including
     *     {@code NaN})
     */
    public BackoffDelay(final long floorNanos, final long ceilingNanos, final double growthFactor) {
        Arguments.requireTrue(floorNanos > 0, "floorNanos must be positive, was: " + floorNanos);
        Arguments.requireTrue(
                ceilingNanos >= floorNanos,
                "ceilingNanos must be >= floorNanos (" + floorNanos + "), was: " + ceilingNanos);
        Arguments.requireTrue(growthFactor > 1.0, "growthFactor must be > 1.0, was: " + growthFactor);
        this.floorNanos = floorNanos;
        this.ceilingNanos = ceilingNanos;
        this.growthFactor = growthFactor;
        this.current = floorNanos;
    }

    /**
     * Returns the delay to park for now, then advances the internal delay toward the ceiling for
     * the next call.
     *
     * <p>The first call after construction (and the first call after each {@link #reset()}) returns
     * the floor. Each subsequent call without an intervening {@link #reset()} multiplies the
     * previous delay by the growth factor, clamped at the ceiling.
     *
     * @return the delay in nanoseconds to pass to {@code parkNanos}
     */
    public long nextDelayNanos() {
        long result = current;
        current = grow(current);
        return result;
    }

    /**
     * Resets the delay to the floor.
     *
     * <p>Call this whenever a queued task was successfully stolen, so the next empty-poll park
     * returns to the low-latency floor instead of continuing the backoff ramp.
     */
    public void reset() {
        current = floorNanos;
    }

    /**
     * Computes the next delay after {@code from}, growing by the growth factor and clamping at the
     * ceiling.
     *
     * <p>The multiply is performed in {@code double} purely to detect overflow; for any realistic
     * ceiling (up to ~1e9 ns, well within 2^53) the product is represented exactly and there is no
     * precision or {@code long}-overflow concern. The clamp returns the original {@code ceilingNanos}
     * long unchanged, so the observed sequence is exact even for ceilings above 2^53. {@code NaN}
     * and {@code Infinity} (reachable only with absurd inputs) also clamp to the ceiling.
     *
     * @param from the current delay
     * @return the next delay, clamped at the ceiling
     */
    private long grow(final long from) {
        double next = from * growthFactor;
        if (Double.isNaN(next) || Double.isInfinite(next) || next > ceilingNanos) {
            return ceilingNanos;
        }
        return (long) next;
    }
}

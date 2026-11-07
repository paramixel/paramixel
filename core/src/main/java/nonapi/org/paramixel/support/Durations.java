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

import java.time.Duration;
import org.paramixel.api.exception.PolicyException;

/**
 * Duration arithmetic shared by retry and loop delay policies.
 */
public final class Durations {

    private Durations() {
        // Intentionally empty
    }

    /**
     * Multiplies a duration by a multiplier, clamping non-positive results to zero.
     *
     * <p>A negative multiplier yields {@link Duration#ZERO}; a product that overflows the
     * duration range is reported as a {@link PolicyException} because it indicates a
     * misconfigured policy rather than a transient failure.
     *
     * @param duration the duration to multiply; must not be {@code null}
     * @param multiplier the multiplier applied to the duration
     * @return the multiplied duration, or {@link Duration#ZERO} when the multiplier is negative
     * @throws PolicyException if the multiplication overflows
     */
    public static Duration multiplyOrZero(final Duration duration, final long multiplier) {
        if (multiplier < 0) {
            return Duration.ZERO;
        }
        try {
            var result = duration.multipliedBy(multiplier);
            if (result.isNegative()) {
                return Duration.ZERO;
            }
            return result;
        } catch (ArithmeticException e) {
            throw new PolicyException("duration overflow multiplying by " + multiplier, e);
        }
    }
}

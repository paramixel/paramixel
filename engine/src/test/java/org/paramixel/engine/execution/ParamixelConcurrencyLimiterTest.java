/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class ParamixelConcurrencyLimiterTest {

    @Test
    public void acquiresAndReleasesPermits() throws Exception {
        final ParamixelConcurrencyLimiter limiter = new ParamixelConcurrencyLimiter(1);

        assertThat(limiter.totalSlots()).isEqualTo(2);
        assertThat(limiter.classSlots()).isEqualTo(1);
        assertThat(limiter.argumentSlots()).isEqualTo(1);

        try (ParamixelConcurrencyLimiter.ClassPermit permit = limiter.acquireClassExecution()) {
            assertThat(limiter.classSlotsInUse()).isEqualTo(1);
            assertThat(limiter.totalSlotsInUse()).isEqualTo(1);

            final var argPermit = limiter.tryAcquireArgumentExecution();
            assertThat(argPermit).isPresent();
            try (var p = argPermit.get()) {
                assertThat(limiter.argumentSlotsInUse()).isEqualTo(1);
                assertThat(limiter.totalSlotsInUse()).isEqualTo(2);
            }
        }

        assertThat(limiter.classSlotsInUse()).isZero();
        assertThat(limiter.argumentSlotsInUse()).isZero();
        assertThat(limiter.totalSlotsInUse()).isZero();
    }

    @Test
    public void rejectsInvalidCoreCount() {
        assertThatThrownBy(() -> new ParamixelConcurrencyLimiter(0)).isInstanceOf(IllegalArgumentException.class);
    }
}

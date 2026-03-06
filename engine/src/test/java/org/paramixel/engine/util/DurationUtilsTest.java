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

package org.paramixel.engine.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class DurationUtilsTest {

    @Test
    public void formatMillis_throwsForNegativeInput() {
        assertThatThrownBy(() -> DurationUtils.formatMillis(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void formatMillis_formatsSubSecondAsMilliseconds() {
        assertThat(DurationUtils.formatMillis(0)).isEqualTo("0 ms");
        assertThat(DurationUtils.formatMillis(999)).isEqualTo("999 ms");
    }

    @Test
    public void formatMillis_formatsSubMinuteAsSecondsWithThreeDecimals() {
        assertThat(DurationUtils.formatMillis(1000)).isEqualTo("1.000 s");
        assertThat(DurationUtils.formatMillis(1500)).isEqualTo("1.500 s");
        assertThat(DurationUtils.formatMillis(59999)).isEqualTo("59.999 s");
    }

    @Test
    public void formatMillis_formatsMinuteAndAboveAsMinutesSecondsMillis() {
        assertThat(DurationUtils.formatMillis(60000)).isEqualTo("1 m 0 s 0 ms");
        assertThat(DurationUtils.formatMillis(61001)).isEqualTo("1 m 1 s 1 ms");
    }
}

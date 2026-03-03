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

import java.util.Locale;
import org.junit.jupiter.api.Test;

public class FastIdTest {

    @Test
    public void getId_throwsWhenLengthNonPositive() {
        assertThatThrownBy(() -> FastId.getId(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FastId.getId(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void getId_generatesAlphanumeric_andAvoidsForbiddenWords() {
        for (int i = 0; i < 5_000; i++) {
            final String id = FastId.getId(16);
            assertThat(id).hasSize(16);
            assertThat(id).matches("[0-9A-Za-z]+$");

            final String upper = id.toUpperCase(Locale.ROOT);
            assertThat(upper).doesNotContain("PASS");
            assertThat(upper).doesNotContain("TEST");
            assertThat(upper).doesNotContain("FAIL");
            assertThat(upper).doesNotContain("SKIP");
        }
    }

    @Test
    public void containsForbidden_detectsForbiddenSubstrings() throws Exception {
        final var m = FastId.class.getDeclaredMethod("containsForbidden", char[].class);
        m.setAccessible(true);

        assertThat((boolean) m.invoke(null, (Object) "XPASSY".toCharArray())).isTrue();
        assertThat((boolean) m.invoke(null, (Object) "clean".toCharArray())).isFalse();
    }
}

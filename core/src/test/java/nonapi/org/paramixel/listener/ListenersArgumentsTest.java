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

package nonapi.org.paramixel.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Descriptor;

@DisplayName("Listeners arguments")
class ListenersArgumentsTest {

    @Test
    @DisplayName("formatKind rejects null descriptor")
    void formatKindRejectsNullDescriptor() {
        assertThatThrownBy(() -> Listeners.formatKind((Descriptor) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("descriptor is null");
    }

    @Test
    @DisplayName("formatStatus rejects null status")
    void formatStatusRejectsNullStatus() {
        assertThatThrownBy(() -> Listeners.formatStatus(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status is null");
    }

    @Test
    @DisplayName("formatAnsiStatus rejects null status")
    void formatAnsiStatusRejectsNullStatus() {
        assertThatThrownBy(() -> Listeners.formatAnsiStatus(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status is null");
    }

    @Test
    @DisplayName("formatException rejects null descriptor")
    void formatExceptionRejectsNullDescriptor() {
        assertThatThrownBy(() -> Listeners.formatException((Descriptor) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("descriptor is null");
    }
}

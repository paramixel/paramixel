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

package org.paramixel.core.spi.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Factory;
import org.paramixel.core.Listener;
import org.paramixel.core.support.AnsiColor;

@DisplayName("Listeners")
class ListenersTest {

    @Test
    @DisplayName("defaultListener returns non-null")
    void defaultListenerReturnsNonNull() {
        Listener listener = Factory.defaultListener();
        assertThat(listener).isNotNull();
    }

    @Test
    @DisplayName("defaultListener is a CompositeListener")
    void defaultListenerIsCompositeListener() {
        Listener listener = Factory.defaultListener();
        assertThat(listener).isInstanceOf(SafeListener.class);
    }

    @Test
    @DisplayName("PARAMIXEL prefix contains expected text")
    void paramixelPrefixContainsExpectedText() {
        String prefix = Constants.PARAMIXEL;
        assertThat(prefix).contains("PARAMIXEL");
        assertThat(prefix).contains(AnsiColor.BOLD_BLUE_TEXT.getCode());
    }
}

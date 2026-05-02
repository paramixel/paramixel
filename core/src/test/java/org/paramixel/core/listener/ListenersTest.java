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

package org.paramixel.core.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Listener;
import org.paramixel.core.support.AnsiColor;

@DisplayName("Listeners")
class ListenersTest {

    @Test
    @DisplayName("defaultListener returns non-null")
    void defaultListenerReturnsNonNull() {
        Listener listener = Listeners.defaultListener();
        assertThat(listener).isNotNull();
    }

    @Test
    @DisplayName("treeListener returns non-null")
    void treeListenerReturnsNonNull() {
        Listener listener = Listeners.treeListener();
        assertThat(listener).isNotNull();
    }

    @Test
    @DisplayName("defaultListener is a CompositeListener")
    void defaultListenerIsCompositeListener() {
        Listener listener = Listeners.defaultListener();
        assertThat(listener).isInstanceOf(CompositeListener.class);
    }

    @Test
    @DisplayName("treeListener is a CompositeListener")
    void treeListenerIsCompositeListener() {
        Listener listener = Listeners.treeListener();
        assertThat(listener).isInstanceOf(CompositeListener.class);
    }

    @Test
    @DisplayName("PARAMIXEL prefix contains expected text")
    void paramixelPrefixContainsExpectedText() {
        String prefix = Listeners.PARAMIXEL;
        assertThat(prefix).contains("PARAMIXEL");
        assertThat(prefix).contains(AnsiColor.BOLD_BLUE_TEXT.getCode());
    }
}

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

package org.paramixel.core.internal.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Factory;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.internal.SubpackageAction;
import org.paramixel.core.internal.DefaultResult;
import org.paramixel.core.internal.DefaultStatus;
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
        String prefix = Constants.PARAMIXEL_ANSI;
        assertThat(prefix).contains("PARAMIXEL");
        assertThat(prefix).contains(AnsiColor.BOLD_BLUE_TEXT.getCode());
    }

    @Nested
    @DisplayName("formatKind")
    class FormatKindTests {

        @Test
        @DisplayName("returns simple name for built-in action in org.paramixel.core.action")
        void returnsSimpleNameForBuiltInAction() {
            Noop noop = Noop.of("test");
            assertThat(Listeners.formatKind(noop)).isEqualTo("Noop");
        }

        @Test
        @DisplayName("returns simple name for Direct action in org.paramixel.core.action")
        void returnsSimpleNameForDirectAction() {
            Action direct = Direct.builder("test").runnable(context -> {}).build();
            assertThat(Listeners.formatKind(direct)).isEqualTo("Direct");
        }

        @Test
        @DisplayName("returns fully qualified name for non-framework action")
        void returnsFQCNForNonFrameworkAction() {
            Action customAction = new CustomAction("custom");
            assertThat(Listeners.formatKind(customAction)).isEqualTo(CustomAction.class.getName());
        }

        @Test
        @DisplayName("returns simple name for action in framework action subpackage")
        void returnsSimpleNameForSubpackageAction() {
            SubpackageAction subpackageAction = new SubpackageAction("sub");
            assertThat(Listeners.formatKind(subpackageAction)).isEqualTo("SubpackageAction");
        }

        @Test
        @DisplayName("rejects null action")
        void rejectsNullAction() {
            assertThatThrownBy(() -> Listeners.formatKind(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("action must not be null");
        }
    }

    private static final class CustomAction implements Action {

        private final String name;

        CustomAction(String name) {
            this.name = name;
        }

        @Override
        public String getId() {
            return "custom-" + name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Result run(Context context) {
            var result = new DefaultResult(this);
            result.complete(DefaultStatus.PASS, java.time.Duration.ZERO);
            return result;
        }

        @Override
        public Result skip(Context context) {
            var result = new DefaultResult(this);
            result.complete(DefaultStatus.SKIP, java.time.Duration.ZERO);
            return result;
        }
    }
}

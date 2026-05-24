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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.internal.listener.CompositeListener;
import org.paramixel.api.internal.listener.ReportListener;
import org.paramixel.api.internal.listener.SafeListener;

@DisplayName("Listener.defaultListener arguments")
class FactoryArgumentsTest {

    @Test
    @DisplayName("defaultListener(Configuration) rejects null configuration")
    void defaultListenerRejectsNullConfiguration() {
        assertThatThrownBy(() -> Listener.defaultListener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("configuration must not be null");
    }

    @Test
    @DisplayName("defaultListener(configuration) omits report listener when report file is blank")
    void defaultListenerWithBlankReportFileOmitsReportListener() throws Exception {
        Listener listener =
                Listener.defaultListener(Configuration.of(java.util.Map.of(Configuration.REPORT_FILE, " ")));

        assertThat(listener).isInstanceOf(SafeListener.class);
        Field delegateField = SafeListener.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        Object delegate = delegateField.get(listener);
        Field listenersField = CompositeListener.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Listener> listeners = (List<Listener>) listenersField.get(delegate);
        assertThat(listeners).hasSize(2);
        assertThat(listeners).noneMatch(ReportListener.class::isInstance);
    }

    @Test
    @DisplayName("defaultRunner returns non-null runner")
    void defaultRunnerReturnsNonNull() {
        Runner runner = Runner.defaultRunner();
        assertThat(runner).isNotNull();
    }
}

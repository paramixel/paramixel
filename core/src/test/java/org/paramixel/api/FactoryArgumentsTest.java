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

import java.util.List;
import java.util.Map;
import nonapi.org.paramixel.listener.CompositeListener;
import nonapi.org.paramixel.listener.ReportListener;
import nonapi.org.paramixel.listener.SafeListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Listener.defaultListener arguments")
class FactoryArgumentsTest {

    @Test
    @DisplayName("defaultListener(Configuration) rejects null configuration")
    void defaultListenerRejectsNullConfiguration() {
        assertThatThrownBy(() -> Listener.defaultListener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("configuration is null");
    }

    @Test
    @DisplayName("defaultListener(configuration) omits report listener when report file is blank")
    void defaultListenerWithBlankReportFileOmitsReportListener() throws Exception {
        var listener = Listener.defaultListener(Configuration.of(Map.of(Configuration.REPORT_FILE, " ")));

        assertThat(listener).isInstanceOf(SafeListener.class);
        var delegateField = SafeListener.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        var delegate = delegateField.get(listener);
        var listenersField = CompositeListener.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var listeners = (List<Listener>) listenersField.get(delegate);
        assertThat(listeners).hasSize(2);
        assertThat(listeners).noneMatch(ReportListener.class::isInstance);
    }

    @Test
    @DisplayName("defaultRunner returns non-null runner")
    void defaultRunnerReturnsNonNull() {
        var runner = Runner.defaultRunner();
        assertThat(runner).isNotNull();
    }
}

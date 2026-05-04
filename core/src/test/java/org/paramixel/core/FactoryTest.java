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

package org.paramixel.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.spi.DefaultRunner;
import org.paramixel.core.spi.listener.CompositeListener;
import org.paramixel.core.spi.listener.ReportListener;
import org.paramixel.core.spi.listener.SafeListener;

@DisplayName("Factory")
class FactoryTest {

    @Test
    @DisplayName("defaultRunner returns DefaultRunner")
    void defaultRunnerReturnsDefaultRunner() {
        Runner runner = Factory.defaultRunner();
        assertThat(runner).isInstanceOf(DefaultRunner.class);
    }

    @Test
    @DisplayName("defaultListener returns SafeListener")
    void defaultListenerReturnsSafeListener() {
        Listener listener = Factory.defaultListener();
        assertThat(listener).isInstanceOf(SafeListener.class);
    }

    @Test
    @DisplayName("defaultListener(configuration) omits report listener when report is disabled")
    void defaultListenerWithReportDisabledOmitsReportListener() throws Exception {
        Listener listener = Factory.defaultListener(Map.of(Configuration.REPORT_ENABLED, "false"));

        assertThat(listener).isInstanceOf(SafeListener.class);
        assertThat(extractListeners(listener)).hasSize(2);
        assertThat(extractListeners(listener)).noneMatch(ReportListener.class::isInstance);
    }

    @Test
    @DisplayName("defaultListener(configuration) includes report listener when report is enabled")
    void defaultListenerWithReportEnabledIncludesReportListener() throws Exception {
        Listener listener = Factory.defaultListener(
                Map.of(Configuration.REPORT_ENABLED, "true", Configuration.REPORT_DIRECTORY, "target/custom-report"));

        assertThat(listener).isInstanceOf(SafeListener.class);
        assertThat(extractListeners(listener)).hasSize(3);
        assertThat(extractListeners(listener)).anyMatch(ReportListener.class::isInstance);
    }

    @SuppressWarnings("unchecked")
    private static List<Listener> extractListeners(Listener listener) throws Exception {
        Field delegateField = SafeListener.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        Object delegate = delegateField.get(listener);

        Field listenersField = CompositeListener.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);
        return (List<Listener>) listenersField.get(delegate);
    }
}

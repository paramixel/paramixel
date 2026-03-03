/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;

public class ParamixelTestEngineTest {

    @Test
    public void getIdIsStable() {
        assertThat(new ParamixelTestEngine().getId()).isEqualTo("paramixel");
    }

    @Test
    public void discoverReturnsEngineDescriptor() {
        final ParamixelTestEngine engine = new ParamixelTestEngine();
        final var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(NotATestClass.class))
                .build();

        final UniqueId uniqueId = UniqueId.forEngine("paramixel");
        final TestDescriptor descriptor = engine.discover(request, uniqueId);

        assertThat(descriptor.getUniqueId()).isEqualTo(uniqueId);
        assertThat(descriptor.getChildren()).isEmpty();
    }

    static class NotATestClass {}

    @Test
    public void execute_withNoTests_doesNotFail() {
        final ParamixelTestEngine engine = new ParamixelTestEngine();
        final var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(NotATestClass.class))
                .build();

        final UniqueId uniqueId = UniqueId.forEngine("paramixel");
        final TestDescriptor descriptor = engine.discover(request, uniqueId);

        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean finished = new AtomicBoolean(false);

        final EngineExecutionListener listener = new EngineExecutionListener() {
            @Override
            public void executionStarted(final TestDescriptor testDescriptor) {
                started.set(true);
            }

            @Override
            public void executionFinished(
                    final TestDescriptor testDescriptor, final TestExecutionResult testExecutionResult) {
                finished.set(true);
                assertThat(testExecutionResult.getStatus()).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
            }
        };

        final ExecutionRequest execRequest =
                new ExecutionRequest(descriptor, listener, new org.junit.platform.engine.ConfigurationParameters() {
                    public Optional<String> get(final String key) {
                        return Optional.empty();
                    }

                    public Optional<Boolean> getBoolean(final String key) {
                        return Optional.empty();
                    }

                    public int size() {
                        return 0;
                    }

                    public java.util.Set<String> keySet() {
                        return java.util.Collections.emptySet();
                    }
                });

        engine.execute(execRequest);

        assertThat(started.get()).isTrue();
        assertThat(finished.get()).isTrue();
    }
}

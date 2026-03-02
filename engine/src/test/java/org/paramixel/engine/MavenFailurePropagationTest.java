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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;

public class MavenFailurePropagationTest {

    @Test
    public void failingParamixelRun_failsLauncherSummary_andPrintsTestsFailed_andFailsEngineDescriptor() {
        final String previousExcludedEngines = System.getProperty("junit.platform.excluded.engines");
        System.clearProperty("junit.platform.excluded.engines");

        final PrintStream originalOut = System.out;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

        try {
            final SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
            final EngineStatusListener engineStatusListener = new EngineStatusListener();

            final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectClass(FailingParamixelTest.class))
                    .filters(EngineFilter.includeEngines("paramixel"))
                    .configurationParameter("invokedBy", "maven")
                    .build();

            final Launcher launcher = LauncherFactory.create();
            launcher.registerTestExecutionListeners(summaryListener, engineStatusListener);
            launcher.execute(request);

            assertThat(summaryListener.getSummary().getTotalFailureCount()).isGreaterThan(0);
            assertThat(engineStatusListener.engineResult)
                    .describedAs("Engine descriptor result should be FAILED")
                    .isEqualTo(TestExecutionResult.Status.FAILED);

            final String output = out.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("TESTS FAILED");
        } finally {
            System.setOut(originalOut);
            if (previousExcludedEngines == null) {
                System.clearProperty("junit.platform.excluded.engines");
            } else {
                System.setProperty("junit.platform.excluded.engines", previousExcludedEngines);
            }
        }
    }

    private static final class EngineStatusListener implements TestExecutionListener {

        private volatile TestExecutionResult.Status engineResult;

        @Override
        public void executionFinished(
                final TestIdentifier testIdentifier, final TestExecutionResult testExecutionResult) {
            if (testIdentifier.getUniqueId().equals("[engine:paramixel]")) {
                this.engineResult = testExecutionResult.getStatus();
            }
        }
    }

    @Paramixel.TestClass
    public static class FailingParamixelTest {

        @Paramixel.Test
        public void fails(final ArgumentContext context) {
            throw new RuntimeException("boom");
        }
    }
}

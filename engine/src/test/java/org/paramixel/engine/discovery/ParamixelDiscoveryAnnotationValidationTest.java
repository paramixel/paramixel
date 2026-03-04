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

package org.paramixel.engine.discovery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.descriptor.ParamixelEngineDescriptor;

public class ParamixelDiscoveryAnnotationValidationTest {

    @Test
    public void discoverTests_failsDiscoveryWhenArgumentsCollectorSignatureIsInvalid() {
        final ParamixelDiscovery discovery = new ParamixelDiscovery();
        final TestDescriptor engine = new ParamixelEngineDescriptor(UniqueId.forEngine("paramixel"), "Paramixel");
        final EngineDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(InvalidArgumentsCollectorSignatureTestClass.class))
                .build();

        assertThatThrownBy(() -> discovery.discoverTests(request, engine))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@Paramixel.ArgumentsCollector");
    }

    @Paramixel.TestClass
    static class InvalidArgumentsCollectorSignatureTestClass {

        @Paramixel.ArgumentsCollector
        public void arguments() {
            // INTENTIONALLY EMPTY
        }

        @Paramixel.Test
        public void test(final ArgumentContext context) {
            // INTENTIONALLY EMPTY
        }
    }
}

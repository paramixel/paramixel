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

package org.paramixel.engine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.EngineContext;

/**
 * Tests for {@link EngineContext} and {@link ConcreteEngineContext} behavior.
 */
public class EngineContextTest {

    /**
     * Verifies successful construction with valid inputs.
     */
    @Test
    @DisplayName("EngineContext should be created with valid parameters")
    public void testEngineContextCreation() {
        final Properties config = new Properties();
        config.setProperty("key", "value");

        final EngineContext context = new ConcreteEngineContext("test-engine", config, 4);

        assertThat(context.getEngineId()).isEqualTo("test-engine");
        assertThat(context.getConfigurationValue("key")).isEqualTo("value");
        assertThat(context.getStore()).isNotNull();
    }

    /**
     * Verifies null engine ID is rejected.
     */
    @Test
    @DisplayName("EngineContext should throw exception for null engineId")
    public void testNullEngineIdThrowsException() {
        final Properties config = new Properties();

        assertThatThrownBy(() -> new ConcreteEngineContext(null, config, 4)).isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies null configuration is rejected.
     */
    @Test
    @DisplayName("EngineContext should throw exception for null configuration")
    public void testNullConfigurationThrowsException() {
        assertThatThrownBy(() -> new ConcreteEngineContext("test-engine", null, 4))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies non-positive parallelism is rejected.
     */
    @Test
    @DisplayName("EngineContext should throw exception for non-positive parallelism")
    public void testNonPositiveParallelismThrowsException() {
        final Properties config = new Properties();

        assertThatThrownBy(() -> new ConcreteEngineContext("test-engine", config, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ConcreteEngineContext("test-engine", config, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies defensive copy semantics for configuration properties.
     */
    @Test
    @DisplayName("EngineContext getConfiguration should return a copy")
    public void testConfigurationReturnsCopy() {
        final Properties config = new Properties();
        config.setProperty("key", "value");

        final EngineContext context = new ConcreteEngineContext("test-engine", config, 4);

        config.setProperty("key", "modified");

        assertThat(context.getConfigurationValue("key")).isEqualTo("value");
    }

    @Test
    @DisplayName("EngineContext getConfiguration should return a defensive copy each time")
    public void getConfigurationIsDefensiveAndIndependentPerCall() {
        final Properties config = new Properties();
        config.setProperty("key", "value");
        final EngineContext context = new ConcreteEngineContext("test-engine", config, 1);

        final Properties first = context.getConfiguration();
        first.setProperty("key", "changed");
        first.setProperty("newKey", "newValue");

        assertThat(context.getConfigurationValue("key")).isEqualTo("value");
        assertThat(context.getConfigurationValue("newKey")).isNull();

        final Properties second = context.getConfiguration();
        assertThat(second.getProperty("key")).isEqualTo("value");
        assertThat(second.getProperty("newKey")).isNull();
    }

    @Test
    @DisplayName("ConcreteEngineContext equals/hashCode are based on fields")
    public void equalsAndHashCode_followFieldEquality() {
        final Properties config1 = new Properties();
        config1.setProperty("k", "v");
        final Properties config2 = new Properties();
        config2.setProperty("k", "v");

        final ConcreteEngineContext a = new ConcreteEngineContext("e", config1, 2);
        final ConcreteEngineContext b = new ConcreteEngineContext("e", config2, 2);
        final ConcreteEngineContext c = new ConcreteEngineContext("e", config2, 3);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("not-a-context");
        assertThat(a).isEqualTo(a);
        assertThat(a.toString()).contains("engineId='e'").contains("classParallelism=2");
    }

    /**
     * Verifies default values are returned when configuration keys are absent.
     */
    @Test
    @DisplayName("EngineContext should support default configuration values")
    public void testGetConfigurationValueWithDefault() {
        final Properties config = new Properties();

        final EngineContext context = new ConcreteEngineContext("test-engine", config, 4);

        assertThat(context.getConfigurationValue("missing", "default")).isEqualTo("default");
        assertThat(context.getConfigurationValue("missing")).isNull();
    }
}

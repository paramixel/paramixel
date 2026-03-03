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

package org.paramixel.engine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.junit.jupiter.api.Test;

public class ConcreteEngineContextTest {

    @Test
    public void providesEngineIdConfigurationAndStore() {
        final Properties properties = new Properties();
        properties.setProperty("k", "v");

        final ConcreteEngineContext context = new ConcreteEngineContext("paramixel", properties, 3);

        assertThat(context.getEngineId()).isEqualTo("paramixel");
        assertThat(context.getConfigurationValue("k")).isEqualTo("v");
        assertThat(context.getConfigurationValue("missing", "d")).isEqualTo("d");
        assertThat(context.getClassParallelism()).isEqualTo(3);
        assertThat(context.getStore()).isNotNull();
    }

    @Test
    public void returnsDefensiveCopyOfConfiguration() {
        final Properties properties = new Properties();
        properties.setProperty("k", "v");

        final ConcreteEngineContext context = new ConcreteEngineContext("paramixel", properties, 1);

        final Properties copy = context.getConfiguration();
        copy.setProperty("k", "changed");
        assertThat(context.getConfigurationValue("k")).isEqualTo("v");
    }

    @Test
    public void rejectsNonPositiveParallelism() {
        assertThatThrownBy(() -> new ConcreteEngineContext("paramixel", new Properties(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void equalsAndHashCode_considerAllFields() {
        final Properties props1 = new Properties();
        props1.setProperty("k", "v");
        final Properties props2 = new Properties();
        props2.setProperty("k", "v");
        final Properties props3 = new Properties();
        props3.setProperty("k", "different");

        final ConcreteEngineContext ctx1 = new ConcreteEngineContext("paramixel", props1, 4);
        final ConcreteEngineContext ctx2 = new ConcreteEngineContext("paramixel", props2, 4);
        final ConcreteEngineContext ctx3 = new ConcreteEngineContext("paramixel", props3, 4);
        final ConcreteEngineContext ctx4 = new ConcreteEngineContext("other", props1, 4);
        final ConcreteEngineContext ctx5 = new ConcreteEngineContext("paramixel", props1, 8);

        assertThat(ctx1).isEqualTo(ctx2);
        assertThat(ctx1.hashCode()).isEqualTo(ctx2.hashCode());

        assertThat(ctx1).isNotEqualTo(ctx3);
        assertThat(ctx1).isNotEqualTo(ctx4);
        assertThat(ctx1).isNotEqualTo(ctx5);
        assertThat(ctx1).isNotEqualTo(null);
        assertThat(ctx1).isNotEqualTo("not-a-context");
        assertThat(ctx1).isEqualTo(ctx1);
    }

    @Test
    public void toString_includesEngineIdAndParallelism() {
        final ConcreteEngineContext ctx = new ConcreteEngineContext("paramixel", new Properties(), 4);

        assertThat(ctx.toString()).contains("engineId='paramixel'");
        assertThat(ctx.toString()).contains("classParallelism=4");
    }
}

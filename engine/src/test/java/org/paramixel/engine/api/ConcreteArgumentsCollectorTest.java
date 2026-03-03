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

import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class ConcreteArgumentsCollectorTest {

    @Test
    public void collectsArgumentsInInsertionOrder() {
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteArgumentsCollector collector = new ConcreteArgumentsCollector(engineContext);

        collector.addArgument("a");
        collector.addArguments("b", "c");
        collector.addArguments(List.of("d"));

        assertThat(collector.toArray()).containsExactly("a", "b", "c", "d");
        assertThat(collector.getEngineContext()).isSameAs(engineContext);
    }

    @Test
    public void setParallelismRejectsValuesLessThanOne() {
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteArgumentsCollector collector = new ConcreteArgumentsCollector(engineContext);

        assertThatThrownBy(() -> collector.setParallelism(0)).isInstanceOf(IllegalArgumentException.class);
    }
}

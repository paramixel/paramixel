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

package nonapi.org.paramixel.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;

/**
 * Pins §2.6: the engine reaches a step's executable through an internal accessor, and no public getter exists.
 */
@DisplayName("Step accessor")
class StepAccessorTest {

    @Test
    @DisplayName("the accessor returns the executable and the engine runs it")
    void engineReachesTheBody() {
        var calls = new AtomicInteger();
        var step = Step.step("step", context -> calls.incrementAndGet());

        assertThat(StepAccessors.consumerOf(step)).isNotNull();

        var result = Runner.builder()
                .configuration(Configuration.of(Map.of()))
                .build()
                .run(step);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Step exposes no public accessor for its executable")
    void noPublicAccessor() {
        for (var method : Step.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            assertThat(method.getReturnType())
                    .as("public method %s", method.getName())
                    .isNotEqualTo(org.paramixel.api.ContextConsumer.class);
        }
        assertThatThrownBy(() -> Step.class.getMethod("throwableConsumer")).isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    @DisplayName("the accessor rejects a null step")
    void rejectsNullStep() {
        assertThatThrownBy(() -> StepAccessors.consumerOf(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("step is null");
    }
}

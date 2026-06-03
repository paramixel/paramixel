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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Step;

@DisplayName("ConcreteRunner exception handling")
class ConcreteRunnerExceptionHandlingTest {

    @Test
    @DisplayName("recoverable exception after root discovery marks root as failed with throwable")
    void recoverableExceptionAfterRootDiscoveryMarksRootAsFailedWithThrowable() throws Exception {
        var runner = (ConcreteRunner) Runner.builder().build();
        setIntField(runner, "schedulerQueueCapacity", 0);

        var result = runner.run(Step.of("root-step", context -> {}));
        var root = result.descriptor().orElseThrow();

        assertThat(result.isFailed()).isTrue();
        assertThat(root.isFailed()).isTrue();
        assertThat(root.throwable()).hasValueSatisfying(throwable -> {
            assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
            assertThat(throwable.getMessage()).contains("queueCapacity must be positive");
        });
    }

    private static void setIntField(final Object target, final String fieldName, final int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }
}

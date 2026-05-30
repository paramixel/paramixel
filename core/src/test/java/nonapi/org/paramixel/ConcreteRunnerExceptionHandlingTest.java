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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Context;
import org.paramixel.api.action.Spec;
import org.paramixel.api.action.Step;

@DisplayName("ConcreteRunner exception handling")
class ConcreteRunnerExceptionHandlingTest {

    @Test
    @DisplayName("recoverable exception after root discovery marks root as failed with throwable")
    void recoverableExceptionAfterRootDiscoveryMarksRootAsFailedWithThrowable() throws Exception {
        var runner = (ConcreteRunner) Runner.builder().build();
        setIntField(runner, "schedulerQueueCapacity", 0);

        var result = runner.run(Step.<Context>of("root-step", ctx -> {}));
        var root = result.descriptor().orElseThrow();

        assertThat(result.status().isFailed()).isTrue();
        assertThat(root.metadata().status().isFailed()).isTrue();
        assertThat(root.metadata().throwable()).hasValueSatisfying(throwable -> {
            assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
            assertThat(throwable.getMessage()).contains("queueCapacity must be positive");
        });
    }

    @Test
    @DisplayName("root-less catch path uses fallback message when throwable message is null")
    void rootlessCatchPathUsesFallbackMessageWhenThrowableMessageIsNull() {
        var runner = Runner.builder().build();
        var result = runner.run(new DiscoveryFailureSpec());
        var status = result.status();

        assertThat(result.descriptor()).isEmpty();
        assertThat(status.isFailed()).isTrue();
        assertThat(status.message()).contains("Runner failed");
        assertThat(status.throwable())
                .hasValueSatisfying(throwable -> assertThat(throwable).isInstanceOf(NullPointerException.class));
    }

    private static void setIntField(final Object target, final String fieldName, final int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static final class DiscoveryFailureSpec implements Spec<Void> {

        @Override
        public Action<Void> resolve() {
            return new DiscoveryFailureAction();
        }
    }

    private static final class DiscoveryFailureAction implements Action<Void> {

        @Override
        public String name() {
            return "discovery-failure";
        }

        @Override
        public String kind() {
            return "DiscoveryFailure";
        }

        @Override
        public List<Action<?>> children() {
            throw new NullPointerException();
        }

        @Override
        public void execute(final Context context) {
            // Intentionally empty: discovery fails before execution.
        }
    }
}

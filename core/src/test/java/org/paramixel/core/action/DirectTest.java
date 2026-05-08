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

package org.paramixel.core.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.exception.FailException;
import org.paramixel.core.exception.SkipException;

@DisplayName("Direct")
class DirectTest {

    @Test
    @DisplayName("builder creates passing direct action")
    void builderCreatesPassingDirectAction() {
        AtomicBoolean ran = new AtomicBoolean();
        Direct action =
                Direct.builder("direct").execute(context -> ran.set(true)).build();

        Result result = Runner.builder().build().run(action);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(ran).isTrue();
        assertThat(action.getContextMode()).isEqualTo(Action.ContextMode.ISOLATED);
    }

    @Test
    @DisplayName("builder configures shared context mode")
    void builderConfiguresSharedContextMode() {
        Direct action = Direct.builder("direct")
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> {})
                .build();

        assertThat(action.getContextMode()).isEqualTo(Action.ContextMode.SHARED);
    }

    @Test
    @DisplayName("converts skip and fail exceptions to statuses")
    void convertsSkipAndFailExceptionsToStatuses() {
        Result skip = Runner.builder()
                .build()
                .run(Direct.builder("skip")
                        .execute(context -> {
                            throw SkipException.of("skip");
                        })
                        .build());
        Result fail = Runner.builder()
                .build()
                .run(Direct.builder("fail")
                        .execute(context -> {
                            throw FailException.of("fail");
                        })
                        .build());

        assertThat(skip.getStatus().isSkip()).isTrue();
        assertThat(fail.getStatus().isFailure()).isTrue();
    }

    @Test
    @DisplayName("builder validates arguments and one-shot behavior")
    void builderValidatesArgumentsAndOneShotBehavior() {
        assertThatThrownBy(() -> Direct.builder(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Direct.builder(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Direct.builder("direct").contextMode(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("contextMode must not be null");
        assertThatThrownBy(() -> Direct.builder("direct").execute(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executable must not be null");
        assertThatIllegalStateException()
                .isThrownBy(() -> Direct.builder("direct").build())
                .withMessage("executable must be configured");

        Direct.Builder builder = Direct.builder("direct").execute(context -> {});
        builder.build();
        assertThatIllegalStateException()
                .isThrownBy(() -> builder.execute(context -> {}))
                .withMessage("builder already built");
    }
}

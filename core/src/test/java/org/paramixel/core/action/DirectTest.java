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
import org.opentest4j.AssertionFailedError;
import org.paramixel.core.Action;
import org.paramixel.core.Listener;
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
                Direct.builder("direct").runnable(context -> ran.set(true)).build();

        Result result = Runner.builder().build().run(action);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("converts skip and fail exceptions to statuses")
    void convertsSkipAndFailExceptionsToStatuses() {
        Result skip = Runner.builder()
                .build()
                .run(Direct.builder("skip")
                        .runnable(context -> {
                            throw SkipException.of("skip");
                        })
                        .build());
        Result fail = Runner.builder()
                .build()
                .run(Direct.builder("fail")
                        .runnable(context -> {
                            throw FailException.of("fail");
                        })
                        .build());

        assertThat(skip.getStatus().isSkip()).isTrue();
        assertThat(fail.getStatus().isFailure()).isTrue();
    }

    @Test
    @DisplayName("run rejects null context")
    void executeRejectsNullContext() {
        Direct action = Direct.builder("direct").runnable(context -> {}).build();

        assertThatThrownBy(() -> action.run(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("context must not be null");
    }

    @Test
    @DisplayName("skip rejects null context")
    void skipRejectsNullContext() {
        Direct action = Direct.builder("direct").runnable(context -> {}).build();

        assertThatThrownBy(() -> action.skip(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("context must not be null");
    }

    @Test
    @DisplayName("builder validates arguments and one-shot behavior")
    void builderValidatesArgumentsAndOneShotBehavior() {
        assertThatThrownBy(() -> Direct.builder(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Direct.builder(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Direct.builder("direct").runnable(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("throwableRunnable must not be null");
        assertThatIllegalStateException()
                .isThrownBy(() -> Direct.builder("direct").build())
                .withMessage("throwableRunnable must be configured");

        Direct.Builder builder = Direct.builder("direct").runnable(context -> {});
        builder.build();
        assertThatIllegalStateException()
                .isThrownBy(() -> builder.runnable(context -> {}))
                .withMessage("builder already built");
    }

    @Test
    @DisplayName("AssertionError captured as failure with afterAction callback")
    void assertionErrorCapturedAsFailureWithAfterActionCallback() {
        AtomicBoolean afterActionCalled = new AtomicBoolean(false);
        AtomicBoolean actionThrowableCalled = new AtomicBoolean(false);
        Action action = Direct.builder("assertion")
                .runnable(context -> {
                    throw new AssertionFailedError("expected true but was false");
                })
                .build();
        Listener trackingListener = new Listener() {
            @Override
            public void actionThrowable(Result result, Throwable throwable) {
                actionThrowableCalled.set(true);
            }

            @Override
            public void afterAction(Result result) {
                afterActionCalled.set(true);
            }
        };

        Result result = Runner.builder().listener(trackingListener).build().run(action);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getStatus().getThrowable()).isPresent().containsInstanceOf(AssertionFailedError.class);
        assertThat(afterActionCalled).isTrue();
        assertThat(actionThrowableCalled).isTrue();
    }

    @Test
    @DisplayName("OutOfMemoryError rethrown from execute")
    void outOfMemoryErrorRethrownFromExecute() {
        Action action = Direct.builder("oom")
                .runnable(context -> {
                    throw new OutOfMemoryError("simulated oom");
                })
                .build();

        assertThatThrownBy(() -> Runner.builder().build().run(action))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated oom");
    }

    @Test
    @DisplayName("StackOverflowError rethrown from execute")
    void stackOverflowErrorRethrownFromExecute() {
        Action action = Direct.builder("soe")
                .runnable(context -> {
                    throw new StackOverflowError("simulated stack overflow");
                })
                .build();

        assertThatThrownBy(() -> Runner.builder().build().run(action))
                .isInstanceOf(StackOverflowError.class)
                .hasMessage("simulated stack overflow");
    }
}

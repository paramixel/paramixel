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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Value;

@DisplayName("Parallel")
class ParallelTest {

    @Test
    @DisplayName("builder creates passing parallel action")
    void builderCreatesPassingParallelAction() {
        Parallel parallel = Parallel.builder("parallel")
                .parallelism(2)
                .child(Noop.of("first"))
                .child(Noop.of("second"))
                .build();

        Result result = Runner.builder().build().run(parallel);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(parallel.parallelism()).isEqualTo(2);
        assertThat(parallel.contextMode()).isEqualTo(Action.ContextMode.ISOLATED);
    }

    @Test
    @DisplayName("validates builder arguments")
    void validatesBuilderArguments() {
        assertThatThrownBy(() -> Parallel.builder(null)).isInstanceOf(NullPointerException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> Parallel.builder(" "));
        assertThatThrownBy(() -> Parallel.builder("parallel").contextMode(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("contextMode must not be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Parallel.builder("parallel").parallelism(0))
                .withMessage("parallelism must be positive, was: 0");
        assertThatThrownBy(() -> Parallel.builder("parallel").child(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("child must not be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Parallel.builder("parallel").build())
                .withMessage("children must not be empty");

        Parallel.Builder builder = Parallel.builder("parallel").child(Noop.of("child"));
        builder.build();
        assertThatIllegalStateException()
                .isThrownBy(() -> builder.child(Noop.of("other")))
                .withMessage("builder already built");
    }

    @Test
    @DisplayName("shared children can coordinate through shared store")
    void sharedChildrenCanCoordinateThroughSharedStore() {
        Action setup = Direct.builder("setup")
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> context.getStore().put("count", Value.of(new AtomicInteger())))
                .build();
        Action workers = Parallel.builder("workers")
                .contextMode(Action.ContextMode.SHARED)
                .child(incrementer("first"))
                .child(incrementer("second"))
                .build();
        Action verify = Direct.builder("verify")
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> assertThat(context.getStore()
                                .get("count")
                                .orElseThrow()
                                .cast(AtomicInteger.class)
                                .get())
                        .isEqualTo(2))
                .build();

        Result result = Runner.builder()
                .build()
                .run(Container.builder("container")
                        .child(setup)
                        .child(workers)
                        .child(verify)
                        .build());

        assertThat(result.getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("addChild rejects self as child")
    void addChildRejectsSelfAsChild() {
        Parallel parallel = Parallel.builder("parallel").child(Noop.of("child")).build();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> parallel.addChild(parallel))
                .withMessage("action must not add itself as a child");
    }

    private static Action incrementer(String name) {
        return Direct.builder(name)
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> context.getStore()
                        .get("count")
                        .orElseThrow()
                        .cast(AtomicInteger.class)
                        .incrementAndGet())
                .build();
    }
}

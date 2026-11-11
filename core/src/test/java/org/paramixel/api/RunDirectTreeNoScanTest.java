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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * Pins the {@code run(Action)} contract: a direct tree is the complete definition, so no discovery, no classpath scan
 * and no hook runs.
 *
 * <p>The proof is the descriptor tree: a discovery run whose classpath declares hooks wraps the discovered body in a
 * lifecycle {@code Scope}, whereas {@code run(Action)} binds the supplied action as the root. A classpath scan would
 * have to produce that wrapper, so its absence is observable without declaring a hook here (declaring one would change
 * the shape every other discovery test on this classpath sees).
 */
@DisplayName("run(Action) does no discovery and no hook scan")
class RunDirectTreeNoScanTest {

    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @Test
    @DisplayName("a direct tree runs alone, without the classpath hook")
    void directTreeRunsWithoutHooks() {
        var action = Sequential.sequential("tree")
                .child(Step.step("first", context -> EVENTS.add("first")))
                .child(Step.step("second", context -> EVENTS.add("second")))
                .build();

        var result = Runner.builder()
                .configuration(Configuration.of(Map.of()))
                .build()
                .run(action);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        var root = result.descriptor().orElseThrow();
        assertThat(root.action()).isSameAs(action);
        assertThat(root.displayName()).isEqualTo("tree");
        assertThat(root.before()).isEmpty();
        assertThat(root.after()).isEmpty();
    }

    @Test
    @DisplayName("a direct Parallel tree is validated and executed as given")
    void directParallelTree() {
        var action = Parallel.parallel("docker", "test")
                .child(Step.step("branch", context -> {}))
                .build();

        var result = Runner.builder()
                .configuration(Configuration.of(Map.of("paramixel.parallelism.test", "2")))
                .build()
                .run(action);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.descriptor().orElseThrow().children()).hasSize(1);
    }

    @Test
    @DisplayName("setup and teardown around a direct tree is a Scope")
    void setupAndTeardownIsAScope() {
        var events = new CopyOnWriteArrayList<String>();
        var action = Scope.scope("lifecycle")
                .before(Step.step("setup", context -> events.add("setup")))
                .body(Step.step("body", context -> events.add("body")))
                .after(Step.step("teardown", context -> events.add("teardown")))
                .build();

        Runner.builder().configuration(Configuration.of(Map.of())).build().run(action);

        assertThat(events).containsExactly("setup", "body", "teardown");
    }
}

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Spec;
import org.paramixel.api.selector.Selector;

@DisplayName("Runner arguments")
class RunnerArgumentsTest {

    @Test
    @DisplayName("rejects mutation after build")
    void rejectsMutationAfterBuild() {
        Runner.Builder builder = Runner.builder();

        builder.build();

        assertThatThrownBy(() -> builder.configuration(Configuration.of(java.util.Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("builder already built");
        assertThatThrownBy(() -> builder.listener(new Listener() {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("builder already built");
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("builder already built");
    }

    @Test
    @DisplayName("run(Selector) rejects null selector")
    void runRejectsNullSelector() {
        assertThatThrownBy(() -> Runner.defaultRunner().run((Selector) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selector must not be null");
    }

    @Test
    @DisplayName("runAndReturnExitCode(Selector) rejects null selector")
    void runAndReturnExitCodeRejectsNullSelector() {
        assertThatThrownBy(() -> Runner.defaultRunner().runAndReturnExitCode((Selector) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selector must not be null");
    }

    @Test
    @DisplayName("runAndReturnExitCode(Spec<?>) rejects null spec")
    void runAndReturnExitCodeRejectsNullSpec() {
        assertThatThrownBy(() -> Runner.defaultRunner().runAndReturnExitCode((Spec<?>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("spec must not be null");
    }

    @Test
    @DisplayName("runAndExit(Spec<?>) rejects null spec")
    void runAndExitRejectsNullSpec() {
        assertThatThrownBy(() -> Runner.defaultRunner().runAndExit((Spec<?>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("spec must not be null");
    }

    @Test
    @DisplayName("runAndExit(Selector) rejects null selector")
    void runAndExitRejectsNullSelector() {
        assertThatThrownBy(() -> Runner.defaultRunner().runAndExit((Selector) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("run(Spec<?>) rejects null spec")
    void runRejectsNullSpec() {
        assertThatThrownBy(() -> Runner.defaultRunner().run((Spec<?>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("spec must not be null");
    }

    @Test
    @DisplayName("runAndReturnExitCode(Spec<?>) null spec throws NPE")
    void runAndReturnExitCodeNullSpecThrowsNPE() {
        Runner runner = Runner.builder().build();
        assertThatThrownBy(() -> runner.runAndReturnExitCode((Spec<?>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("spec must not be null");
    }

    @Test
    @DisplayName("runAndReturnExitCode(Selector) null selector throws NPE")
    void nullSelectorThrowsNPE() {
        Runner runner = Runner.builder().build();
        assertThatThrownBy(() -> runner.runAndReturnExitCode((Selector) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selector must not be null");
    }

    @Test
    @DisplayName("runAndExit(Spec<?>) null spec throws NPE")
    void runAndExitNullSpecThrowsNPE() {
        assertThatThrownBy(() -> Runner.builder().build().runAndExit((Spec<?>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("spec must not be null");
    }

    @Test
    @DisplayName("runAndExit(Selector) null selector throws NPE")
    void runAndExitSelectorNullThrowsNPE() {
        assertThatThrownBy(() -> Runner.builder().build().runAndExit((Selector) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selector must not be null");
    }

    @Test
    @DisplayName("Runner.builder rejects null listener")
    void runnerBuilderRejectsNullListener() {
        assertThatThrownBy(() -> Runner.builder().listener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listener must not be null");
    }

    @Test
    @DisplayName("configuration(null) throws NPE")
    void configurationNullThrowsNPE() {
        assertThatThrownBy(() -> Runner.builder().configuration(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("configuration must not be null");
    }

    @Test
    @DisplayName("listener(null) throws NPE")
    void listenerNullThrowsNPE() {
        assertThatThrownBy(() -> Runner.builder().listener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listener must not be null");
    }

    @Test
    @DisplayName("build() twice throws ISE")
    void buildTwiceThrowsISE() {
        Runner.Builder builder = Runner.builder();
        builder.build();
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("builder already built");
    }
}

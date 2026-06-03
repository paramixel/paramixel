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
import org.paramixel.api.action.Action;
import org.paramixel.api.selector.Selector;

@DisplayName("Runner arguments")
class RunnerArgumentsTest {

    @Test
    @DisplayName("run(Selector) rejects null selector")
    void runRejectsNullSelector() {
        assertThatThrownBy(() -> Runner.defaultRunner().run((Selector) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selector is null");
    }

    @Test
    @DisplayName("runAndReturnExitCode(Selector) rejects null selector")
    void runAndReturnExitCodeRejectsNullSelector() {
        assertThatThrownBy(() -> Runner.defaultRunner().runAndReturnExitCode((Selector) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selector is null");
    }

    @Test
    @DisplayName("runAndReturnExitCode(Action) rejects null action")
    void runAndReturnExitCodeRejectsNullAction() {
        assertThatThrownBy(() -> Runner.defaultRunner().runAndReturnExitCode((Action) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action is null");
    }

    @Test
    @DisplayName("runAndExit(Action) rejects null action")
    void runAndExitRejectsNullAction() {
        assertThatThrownBy(() -> Runner.defaultRunner().runAndExit((Action) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action is null");
    }

    @Test
    @DisplayName("runAndExit(Selector) rejects null selector")
    void runAndExitRejectsNullSelector() {
        assertThatThrownBy(() -> Runner.defaultRunner().runAndExit((Selector) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("run(Action) rejects null action")
    void runRejectsNullAction() {
        assertThatThrownBy(() -> Runner.defaultRunner().run((Action) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action is null");
    }

    @Test
    @DisplayName("runAndReturnExitCode(Action) null action throws NPE")
    void runAndReturnExitCodeNullActionThrowsNPE() {
        Runner runner = Runner.builder().build();
        assertThatThrownBy(() -> runner.runAndReturnExitCode((Action) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action is null");
    }

    @Test
    @DisplayName("runAndReturnExitCode(Selector) null selector throws NPE")
    void nullSelectorThrowsNPE() {
        Runner runner = Runner.builder().build();
        assertThatThrownBy(() -> runner.runAndReturnExitCode((Selector) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selector is null");
    }

    @Test
    @DisplayName("runAndExit(Action) null action throws NPE")
    void runAndExitNullActionThrowsNPE() {
        assertThatThrownBy(() -> Runner.builder().build().runAndExit((Action) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action is null");
    }

    @Test
    @DisplayName("runAndExit(Selector) null selector throws NPE")
    void runAndExitSelectorNullThrowsNPE() {
        assertThatThrownBy(() -> Runner.builder().build().runAndExit((Selector) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selector is null");
    }

    @Test
    @DisplayName("Runner.builder rejects null listener")
    void runnerBuilderRejectsNullListener() {
        assertThatThrownBy(() -> Runner.builder().listener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listener is null");
    }

    @Test
    @DisplayName("configuration(null) throws NPE")
    void configurationNullThrowsNPE() {
        assertThatThrownBy(() -> Runner.builder().configuration(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("configuration is null");
    }

    @Test
    @DisplayName("listener(null) throws NPE")
    void listenerNullThrowsNPE() {
        assertThatThrownBy(() -> Runner.builder().listener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listener is null");
    }
}

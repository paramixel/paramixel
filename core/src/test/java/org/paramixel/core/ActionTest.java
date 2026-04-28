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

package org.paramixel.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Executable;
import org.paramixel.core.action.Noop;

@DisplayName("Action")
class ActionTest {

    @Test
    @DisplayName("creates noop actions that complete without doing work")
    void createsNoopActionsThatCompleteWithoutDoingWork() {
        Action action = Noop.of("noop");

        Result result = Runner.builder().build().run(action);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.failure()).isEmpty();
        assertThat(result.action()).isSameAs(action);
        assertThat(result.action()).isInstanceOf(Noop.class);
        assertThat(result.children()).isEmpty();
    }

    @Test
    @DisplayName("creates reusable noop executables")
    void createsReusableNoopExecutables() throws Throwable {
        var noop = Executable.noop();

        noop.execute(null);
        noop.execute(null);
    }
}

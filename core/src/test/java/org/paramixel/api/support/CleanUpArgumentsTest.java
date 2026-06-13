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

package org.paramixel.api.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ThrowingRunnable;

@DisplayName("CleanUp arguments")
class CleanUpArgumentsTest {

    @Test
    @DisplayName("of ThrowingRunnable rejects null")
    void ofThrowingRunnableRejectsNull() {
        assertThatThrownBy(() -> CleanUp.of((ThrowingRunnable) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("throwableRunnable is null");
    }

    @Test
    @DisplayName("of AutoCloseable accepts null")
    void ofAutoCloseableAcceptsNull() {
        var cleanup = CleanUp.of((AutoCloseable) null);

        cleanup.run();
    }

    @Test
    @DisplayName("run throws IllegalStateException on second call")
    void runThrowsIllegalStateExceptionOnSecondCall() {
        var cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        cleanup.run();

        assertThatThrownBy(() -> cleanup.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CleanUp has already run");
    }

    @Test
    @DisplayName("runAndThrow throws IllegalStateException on second call")
    void runAndThrowThrowsIllegalStateExceptionOnSecondCall() throws Throwable {
        var cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        cleanup.runAndThrow();

        assertThatThrownBy(() -> cleanup.runAndThrow())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CleanUp has already run");
    }

    @Test
    @DisplayName("static runAndThrow vararg rejects null array")
    void staticRunAndThrowVarargRejectsNullArray() {
        assertThatThrownBy(() -> CleanUp.runAndThrow((CleanUp[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cleanUps is null");
    }

    @Test
    @DisplayName("static runAndThrow vararg rejects null element")
    void staticRunAndThrowVarargRejectsNullElement() {
        assertThatThrownBy(() -> CleanUp.runAndThrow(CleanUp.of((ThrowingRunnable) () -> {}), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cleanUp is null");
    }

    @Test
    @DisplayName("static runAndThrow collection rejects null collection")
    void staticRunAndThrowCollectionRejectsNullCollection() {
        assertThatThrownBy(() -> CleanUp.runAndThrow((List<CleanUp>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cleanUps is null");
    }

    @Test
    @DisplayName("static runAndThrow collection rejects null element")
    void staticRunAndThrowCollectionRejectsNullElement() {
        assertThatThrownBy(() -> CleanUp.runAndThrow(Arrays.asList(CleanUp.of((ThrowingRunnable) () -> {}), null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cleanUp is null");
    }
}

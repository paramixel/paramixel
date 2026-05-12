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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Direct;
import org.paramixel.core.internal.DefaultContext;
import org.paramixel.core.internal.DefaultResult;
import org.paramixel.core.internal.DefaultStatus;

@DisplayName("DefaultListener")
class DefaultListenerTest {

    @Test
    @DisplayName("prints action kind before and after execution")
    void printsActionKindBeforeAndAfterExecution() {
        Listener listener = Factory.defaultListener();
        Direct.Executable executable = context -> {};
        Action action = Direct.builder("direct").execute(executable).build();
        Context context = new DefaultContext(
                Configuration.defaultProperties(),
                listener,
                (scheduledAction, scheduledContext) ->
                        CompletableFuture.completedFuture(scheduledAction.execute(scheduledContext)));
        DefaultResult result = new DefaultResult(action);
        result.setStatus(DefaultStatus.PASS);
        result.setRunDuration(Duration.ZERO);
        var output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

            listener.beforeAction(result);
            listener.afterAction(result);
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("direct").contains("PASS");
    }
}

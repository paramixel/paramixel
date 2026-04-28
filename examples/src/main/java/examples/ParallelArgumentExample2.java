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

package examples;

import examples.support.Logger;
import java.util.ArrayList;
import java.util.List;
import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.action.Sequential;

public class ParallelArgumentExample2 {

    private static final Logger LOGGER = Logger.createLogger(ParallelArgumentExample2.class);

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        var suiteName = "Parallel argument example 2";

        var argumentActions = new ArrayList<Action>();
        for (int i = 0; i < 5; i++) {
            var argumentValue = "string-" + i;

            Action testAction1 = Direct.of(
                    "test1",
                    context -> LOGGER.info(
                            "test1() argument [%s]",
                            context.action().parent().orElseThrow().name()));

            Action testAction2 = Direct.of(
                    "test2",
                    context -> LOGGER.info(
                            "test2() argument [%s]",
                            context.action().parent().orElseThrow().name()));

            Action testAction3 = Direct.of(
                    "test3",
                    context -> LOGGER.info(
                            "test3() argument [%s]",
                            context.action().parent().orElseThrow().name()));

            Action argumentAction = Sequential.of("method", List.of(testAction1, testAction2, testAction3));

            Action lifecycleAction = Lifecycle.of(
                    argumentValue,
                    context ->
                            LOGGER.info("setUpArgument() [%s]", context.action().name()),
                    argumentAction,
                    context -> LOGGER.info(
                            "tearDownArgument() [%s]", context.action().name()));

            argumentActions.add(lifecycleAction);
        }

        Action arguments = Parallel.of(suiteName, 5, argumentActions);
        return Lifecycle.of(
                suiteName,
                context -> LOGGER.info("setUpAction()"),
                arguments,
                context -> LOGGER.info("tearDownAction()"));
    }

    public static void main(String[] args) {
        Result result = Runner.builder().build().run(actionFactory());
        int exitCode = result.status() == Result.Status.PASS ? 0 : 1;
        System.exit(exitCode);
    }
}

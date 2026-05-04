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

package examples.argument;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.action.Sequential;

public class ArgumentParallelismTest {

    private static final AtomicInteger INVOCATION_COUNT = new AtomicInteger();

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        INVOCATION_COUNT.set(0);

        Action first = Direct.of("arg-0", context -> INVOCATION_COUNT.incrementAndGet());
        Action second = Direct.of("arg-1", context -> INVOCATION_COUNT.incrementAndGet());
        Action third = Direct.of("arg-2", context -> INVOCATION_COUNT.incrementAndGet());
        Action verify = Direct.of(
                "verify", context -> assertThat(INVOCATION_COUNT.get()).isEqualTo(3));

        return Sequential.of(
                "ArgumentParallelismTest",
                List.of(Parallel.of("parallel-arguments", 3, List.of(first, second, third)), verify));
    }
}

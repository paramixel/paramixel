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

package examples.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Listener;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;

public class CustomListenerTest {

    private static final AtomicInteger beforeActionCount = new AtomicInteger();
    private static final AtomicInteger afterActionCount = new AtomicInteger();

    public static void main(String[] args) {
        resetCounts();

        Listener customListener = new Listener() {

            @Override
            public void beforeAction(Result result) {
                beforeActionCount.incrementAndGet();
            }

            @Override
            public void afterAction(Result result) {
                afterActionCount.incrementAndGet();
            }
        };

        Runner runner = Runner.builder().listener(customListener).build();
        Result result = runner.run(actionFactory());

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(beforeActionCount.get()).isGreaterThan(0);
        assertThat(afterActionCount.get()).isEqualTo(beforeActionCount.get());

        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        resetCounts();

        Action first = Direct.builder("first").execute(context -> {}).build();

        Action second = Direct.builder("second").execute(context -> {}).build();

        Action third = Direct.builder("third").execute(context -> {}).build();

        return Container.builder("CustomListenerTest")
                .child(first)
                .child(second)
                .child(third)
                .build();
    }

    private static void resetCounts() {
        beforeActionCount.set(0);
        afterActionCount.set(0);
    }
}

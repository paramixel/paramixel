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

import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;

public class ArgumentCustomTypesTest {

    private enum Shape {
        CIRCLE,
        SQUARE
    }

    private record Sample(String name, int count) {}

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        var sample = new Sample("alpha", 3);

        Action recordTest = recordTest(sample);
        Action enumTest = enumTest();
        Action classTest = classTest(sample);

        var suiteBuilder = Container.builder("ArgumentCustomTypesTest")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build());
        suiteBuilder.child(recordTest);
        suiteBuilder.child(enumTest);
        suiteBuilder.child(classTest);
        return suiteBuilder.build();
    }

    private static Action recordTest(Sample sample) {
        return Direct.builder("record")
                .runnable(context -> {
                    assertThat(sample.name()).isEqualTo("alpha");
                    assertThat(sample.count()).isEqualTo(3);
                })
                .build();
    }

    private static Action enumTest() {
        return Direct.builder("enum")
                .runnable(context -> assertThat(Shape.CIRCLE).isEqualTo(Shape.CIRCLE))
                .build();
    }

    private static Action classTest(Sample sample) {
        return Direct.builder("class")
                .runnable(context -> assertThat(sample).isInstanceOf(Sample.class))
                .build();
    }
}

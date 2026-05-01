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
import org.paramixel.core.Action;
import org.paramixel.core.ConsoleRunner;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Sequential;

public class ArgumentCustomTypesTest {

    private enum Shape {
        CIRCLE,
        SQUARE
    }

    private record Sample(String name, int count) {}

    public static void main(String[] args) {
        ConsoleRunner.runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        var sample = new Sample("alpha", 3);
        return Sequential.of(
                "ArgumentCustomTypesTest",
                List.of(
                        Direct.of("record", context -> {
                            assertThat(sample.name()).isEqualTo("alpha");
                            assertThat(sample.count()).isEqualTo(3);
                        }),
                        Direct.of("enum", context -> assertThat(Shape.CIRCLE).isEqualTo(Shape.CIRCLE)),
                        Direct.of("class", context -> assertThat(sample).isInstanceOf(Sample.class))));
    }
}

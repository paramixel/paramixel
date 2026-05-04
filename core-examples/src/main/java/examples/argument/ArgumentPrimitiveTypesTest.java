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
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Sequential;

public class ArgumentPrimitiveTypesTest {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Sequential.of(
                "ArgumentPrimitiveTypesTest",
                List.of(
                        Direct.of("int", context -> assertThat(7).isEqualTo(7)),
                        Direct.of("long", context -> assertThat(11L).isEqualTo(11L)),
                        Direct.of("double", context -> assertThat(2.5d).isEqualTo(2.5d)),
                        Direct.of("boolean", context -> assertThat(true).isTrue()),
                        Direct.of("char", context -> assertThat('p').isEqualTo('p'))));
    }
}

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
import java.util.Map;
import java.util.Set;
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Sequential;

public class ArgumentCollectionTypesTest {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Sequential.of(
                "ArgumentCollectionTypesTest",
                List.of(
                        Direct.of(
                                "list",
                                context -> assertThat(List.of("a", "b", "c")).containsExactly("a", "b", "c")),
                        Direct.of("set", context -> assertThat(Set.of(1, 2, 3)).containsExactlyInAnyOrder(1, 2, 3)),
                        Direct.of(
                                "map",
                                context ->
                                        assertThat(Map.of("one", 1, "two", 2)).containsEntry("two", 2))));
    }
}

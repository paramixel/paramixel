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

public class ArgumentPrimitiveTypesTest {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action intTest = intTest();
        Action longTest = longTest();
        Action doubleTest = doubleTest();
        Action booleanTest = booleanTest();
        Action charTest = charTest();

        var suiteBuilder = Container.builder("ArgumentPrimitiveTypesTest")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build());
        suiteBuilder.child(intTest);
        suiteBuilder.child(longTest);
        suiteBuilder.child(doubleTest);
        suiteBuilder.child(booleanTest);
        suiteBuilder.child(charTest);
        return suiteBuilder.build();
    }

    private static Action intTest() {
        return Direct.builder("int")
                .execute(context -> assertThat(7).isEqualTo(7))
                .build();
    }

    private static Action longTest() {
        return Direct.builder("long")
                .execute(context -> assertThat(11L).isEqualTo(11L))
                .build();
    }

    private static Action doubleTest() {
        return Direct.builder("double")
                .execute(context -> assertThat(2.5d).isEqualTo(2.5d))
                .build();
    }

    private static Action booleanTest() {
        return Direct.builder("boolean")
                .execute(context -> assertThat(true).isTrue())
                .build();
    }

    private static Action charTest() {
        return Direct.builder("char")
                .execute(context -> assertThat('p').isEqualTo('p'))
                .build();
    }
}

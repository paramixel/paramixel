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

import java.math.BigDecimal;
import java.math.BigInteger;
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;

public class ArgumentStringAndBigNumberTest {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action stringTest = stringTest();
        Action bigIntegerTest = bigIntegerTest();
        Action bigDecimalTest = bigDecimalTest();

        var suiteBuilder = Container.builder("ArgumentStringAndBigNumberTest")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build());
        suiteBuilder.child(stringTest);
        suiteBuilder.child(bigIntegerTest);
        suiteBuilder.child(bigDecimalTest);
        return suiteBuilder.build();
    }

    private static Action stringTest() {
        return Direct.builder("string")
                .execute(context -> assertThat("paramixel").startsWith("param"))
                .build();
    }

    private static Action bigIntegerTest() {
        return Direct.builder("big-integer")
                .execute(context -> assertThat(new BigInteger("42")).isEqualTo(new BigInteger("42")))
                .build();
    }

    private static Action bigDecimalTest() {
        return Direct.builder("big-decimal")
                .execute(context -> assertThat(new BigDecimal("3.14")).isEqualByComparingTo("3.14"))
                .build();
    }
}

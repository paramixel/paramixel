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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Paramixel arguments")
class ParamixelArgumentsTest {

    @Test
    @DisplayName("constructor is private and cannot be invoked normally")
    void constructorIsPrivate() throws NoSuchMethodException {
        Constructor<Paramixel> constructor = Paramixel.class.getDeclaredConstructor();

        assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()))
                .isTrue();
    }

    @Test
    @DisplayName("can be invoked reflectively but produces an instance")
    void canBeInvokedReflectively()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        Constructor<Paramixel> constructor = Paramixel.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        Paramixel instance = constructor.newInstance();

        assertThat(instance).isNotNull();
    }
}

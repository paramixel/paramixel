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

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Paramixel")
class ParamixelTest {

    @Test
    @DisplayName("@Factory has RUNTIME retention")
    void factoryHasRuntimeRetention() {
        Retention retention = Paramixel.Factory.class.getAnnotation(Retention.class);

        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    @DisplayName("@Factory targets METHOD")
    void factoryTargetsMethod() {
        Target target = Paramixel.Factory.class.getAnnotation(Target.class);

        assertThat(target.value()).containsExactly(ElementType.METHOD);
    }

    @Test
    @DisplayName("@Priority default value is 0")
    void priorityDefaultValueIsZero() throws NoSuchMethodException {
        var method = Paramixel.Priority.class.getDeclaredMethod("value");

        assertThat(method.getDefaultValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("@Disabled default value is empty string")
    void disabledDefaultValueIsEmptyString() throws NoSuchMethodException {
        var method = Paramixel.Disabled.class.getDeclaredMethod("value");

        assertThat(method.getDefaultValue()).isEqualTo("");
    }

    @Test
    @DisplayName("@Tag is Repeatable with Tags container")
    void tagIsRepeatableWithTagsContainer() {
        Repeatable repeatable = Paramixel.Tag.class.getAnnotation(Repeatable.class);

        assertThat(repeatable).isNotNull();
        assertThat(repeatable.value()).isEqualTo(Paramixel.Tags.class);
    }

    @Test
    @DisplayName("@Tags value is Tag array")
    void tagsValueIsTagArray() throws NoSuchMethodException {
        var method = Paramixel.Tags.class.getDeclaredMethod("value");

        assertThat(method.getReturnType()).isEqualTo(Paramixel.Tag[].class);
    }

    @Test
    @DisplayName("@Id targets METHOD")
    void idTargetsMethod() {
        Target target = Paramixel.Id.class.getAnnotation(Target.class);

        assertThat(target.value()).containsExactly(ElementType.METHOD);
    }

    @Test
    @DisplayName("@Id has RUNTIME retention")
    void idHasRuntimeRetention() {
        Retention retention = Paramixel.Id.class.getAnnotation(Retention.class);

        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }
}

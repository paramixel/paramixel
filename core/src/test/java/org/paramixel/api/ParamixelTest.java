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
    @DisplayName("@Paranixel.Factory has RUNTIME retention")
    void factoryHasRuntimeRetention() {
        var retention = Paramixel.Factory.class.getAnnotation(Retention.class);

        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    @DisplayName("@Paramixel.Factory targets METHOD")
    void factoryTargetsMethod() {
        var target = Paramixel.Factory.class.getAnnotation(Target.class);

        assertThat(target.value()).containsExactly(ElementType.METHOD);
    }

    @Test
    @DisplayName("@Paramixel.Priority default value is 0")
    void priorityDefaultValueIsZero() throws NoSuchMethodException {
        var method = Paramixel.Priority.class.getDeclaredMethod("value");

        assertThat(method.getDefaultValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("@Paramixel.Disabled default value is empty string")
    void disabledDefaultValueIsEmptyString() throws NoSuchMethodException {
        var method = Paramixel.Disabled.class.getDeclaredMethod("value");

        assertThat(method.getDefaultValue()).isEqualTo("");
    }

    @Test
    @DisplayName("@Paramixel.Tag is Repeatable with Tags container")
    void tagIsRepeatableWithTagsContainer() {
        var repeatable = Paramixel.Tag.class.getAnnotation(Repeatable.class);

        assertThat(repeatable).isNotNull();
        assertThat(repeatable.value()).isEqualTo(Paramixel.Tags.class);
    }

    @Test
    @DisplayName("@Paramixel.Tags value is Tag array")
    void tagsValueIsTagArray() throws NoSuchMethodException {
        var method = Paramixel.Tags.class.getDeclaredMethod("value");

        assertThat(method.getReturnType()).isEqualTo(Paramixel.Tag[].class);
    }

    @Test
    @DisplayName("@Paramixel.Id targets METHOD")
    void idTargetsMethod() {
        var target = Paramixel.Id.class.getAnnotation(Target.class);

        assertThat(target.value()).containsExactly(ElementType.METHOD);
    }

    @Test
    @DisplayName("@Paramixel.Id has RUNTIME retention")
    void idHasRuntimeRetention() {
        var retention = Paramixel.Id.class.getAnnotation(Retention.class);

        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }
}

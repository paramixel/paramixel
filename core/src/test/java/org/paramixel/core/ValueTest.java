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

package org.paramixel.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Value")
class ValueTest {

    @Test
    @DisplayName("of wraps non-null value")
    void ofWrapsNonNullValue() {
        Value value = Value.of("hello");

        assertThat(value.get()).isEqualTo("hello");
    }

    @Test
    @DisplayName("isType returns true when compatible")
    void isTypeReturnsTrueWhenCompatible() {
        Value value = Value.of("hello");

        assertThat(value.isType(String.class)).isTrue();
    }

    @Test
    @DisplayName("isType returns false when incompatible")
    void isTypeReturnsFalseWhenIncompatible() {
        Value value = Value.of("hello");

        assertThat(value.isType(Integer.class)).isFalse();
    }

    @Test
    @DisplayName("cast returns typed value when compatible")
    void toReturnsTypedValueWhenCompatible() {
        Value value = Value.of("hello");

        assertThat(value.cast(String.class)).isEqualTo("hello");
    }

    @Test
    @DisplayName("cast throws when incompatible")
    void toThrowsWhenIncompatible() {
        Value value = Value.of("hello");

        assertThatThrownBy(() -> value.cast(Integer.class)).isInstanceOf(ClassCastException.class);
    }

    @Test
    @DisplayName("of rejects null")
    void ofRejectsNull() {
        assertThatThrownBy(() -> Value.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("isType rejects null type")
    void isTypeRejectsNullType() {
        Value value = Value.of("hello");

        assertThatThrownBy(() -> value.isType(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("cast rejects null type")
    void toRejectsNullType() {
        Value value = Value.of("hello");

        assertThatThrownBy(() -> value.cast(null)).isInstanceOf(NullPointerException.class);
    }
}

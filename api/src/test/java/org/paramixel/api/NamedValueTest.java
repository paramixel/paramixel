/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class NamedValueTest {

    @Test
    public void of_setsNameAndValue() {
        final NamedValue<Integer> nv = NamedValue.of("n", 123);

        assertThat(nv.getName()).isEqualTo("n");
        assertThat(nv.getValue()).isEqualTo(123);
        assertThat(nv.getValue(Integer.class)).isEqualTo(123);
    }

    @Test
    public void getValueTyped_returnsNullWhenValueNull() {
        final NamedValue<Object> nv = NamedValue.of("n", null);

        assertThat(nv.getValue()).isNull();
        assertThat(nv.getValue(String.class)).isNull();
    }

    @Test
    public void getValueTyped_throwsClassCastExceptionWhenNotAssignable() {
        final NamedValue<Object> nv = NamedValue.of("n", 123);

        assertThatThrownBy(() -> nv.getValue(String.class)).isInstanceOf(ClassCastException.class);
    }

    @Test
    public void getValueTyped_throwsNullPointerExceptionWhenTypeNull() {
        final NamedValue<Object> nv = NamedValue.of("n", "v");

        assertThatThrownBy(() -> nv.getValue(null)).isInstanceOf(NullPointerException.class);
    }
}

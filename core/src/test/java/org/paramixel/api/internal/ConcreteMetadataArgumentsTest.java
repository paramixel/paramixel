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

package org.paramixel.api.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConcreteMetadata arguments")
class ConcreteMetadataArgumentsTest {

    @Test
    @DisplayName("constructor rejects null id")
    void constructorRejectsNullId() {
        assertThatThrownBy(() -> new ConcreteMetadata(null, "name", "className", "TypeName"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id must not be null");
    }

    @Test
    @DisplayName("constructor rejects null name")
    void constructorRejectsNullName() {
        assertThatThrownBy(() -> new ConcreteMetadata("id", null, "className", "TypeName"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name must not be null");
    }

    @Test
    @DisplayName("constructor rejects null className")
    void constructorRejectsNullClassName() {
        assertThatThrownBy(() -> new ConcreteMetadata("id", "name", null, "TypeName"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("className must not be null");
    }

    @Test
    @DisplayName("constructor rejects null kind")
    void constructorRejectsNullKind() {
        assertThatThrownBy(() -> new ConcreteMetadata("id", "name", "className", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("kind must not be null");
    }
}

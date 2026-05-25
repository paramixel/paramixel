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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConcreteMetadata")
class ConcreteMetadataTest {

    @Test
    @DisplayName("getId returns id")
    void getIdReturnsId() {
        var metadata = new ConcreteMetadata("id1", "name1", "com.example.Action", "TypeName");

        assertThat(metadata.id()).isEqualTo("id1");
    }

    @Test
    @DisplayName("getName returns name")
    void getNameReturnsName() {
        var metadata = new ConcreteMetadata("id1", "name1", "com.example.Action", "TypeName");

        assertThat(metadata.name()).isEqualTo("name1");
    }

    @Test
    @DisplayName("getClassName returns className")
    void getClassNameReturnsClassName() {
        var metadata = new ConcreteMetadata("id1", "name1", "com.example.Action", "TypeName");

        assertThat(metadata.className()).isEqualTo("com.example.Action");
    }

    @Test
    @DisplayName("getKind returns kind")
    void getKindReturnsKind() {
        var metadata = new ConcreteMetadata("id1", "name1", "com.example.Action", "KindName");

        assertThat(metadata.kind()).isEqualTo("KindName");
    }
}

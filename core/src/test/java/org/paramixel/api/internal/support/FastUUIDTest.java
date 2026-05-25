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

package org.paramixel.api.internal.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FastUUID")
class FastUUIDTest {

    private static final String UUID_V4_PATTERN =
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    @Test
    @DisplayName("generateUuid produces RFC 9562 canonical format")
    void generateUuidProducesRfc9562Format() {
        for (int i = 0; i < 10_000; i++) {
            String uuid = FastUUID.generateUuid();
            assertThat(uuid).matches(UUID_V4_PATTERN);
        }
    }

    @Test
    @DisplayName("generateUuid sets version 4 bits")
    void generateUuidSetsVersion4Bits() {
        for (int i = 0; i < 10_000; i++) {
            String uuid = FastUUID.generateUuid();
            assertThat(uuid.charAt(14)).isEqualTo('4');
        }
    }

    @Test
    @DisplayName("generateUuid sets RFC 4122 variant bits")
    void generateUuidSetsVariantBits() {
        for (int i = 0; i < 10_000; i++) {
            String uuid = FastUUID.generateUuid();
            char variantChar = uuid.charAt(19);
            assertThat(variantChar).isIn('8', '9', 'a', 'b');
        }
    }

    @Test
    @DisplayName("generateUuid is parseable by UUID.fromString")
    void generateUuidIsParseable() {
        for (int i = 0; i < 10_000; i++) {
            String uuid = FastUUID.generateUuid();
            assertThat(UUID.fromString(uuid)).isNotNull();
        }
    }

    @Test
    @DisplayName("generated UUIDs are unique")
    void generatedUuidsAreUnique() {
        Set<String> uuids = new HashSet<>();
        int count = 100_000;

        for (int i = 0; i < count; i++) {
            String uuid = FastUUID.generateUuid();
            assertThat(uuids.add(uuid)).isTrue();
        }
    }
}

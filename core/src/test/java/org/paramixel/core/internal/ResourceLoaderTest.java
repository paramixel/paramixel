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

package org.paramixel.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.junit.jupiter.api.Test;

class ResourceLoaderTest {

    @Test
    void shouldFindExistingResource() {
        InputStream stream = ResourceLoader.getResourceAsStream("version.properties");
        assertThat(stream).isNotNull();
    }

    @Test
    void shouldFindResourceWithNullContextClassLoader() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            InputStream stream = ResourceLoader.getResourceAsStream("version.properties");
            assertThat(stream).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void shouldFallBackToDefiningClassLoaderWhenContextClassLoaderCannotFindResource() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null) {});
            InputStream stream = ResourceLoader.getResourceAsStream("version.properties");
            assertThat(stream).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void shouldReturnNullStreamForNonexistentResource() {
        InputStream stream = ResourceLoader.getResourceAsStream("nonexistent.resource.that.does.not.exist");
        assertThat(stream).isNull();
    }
}

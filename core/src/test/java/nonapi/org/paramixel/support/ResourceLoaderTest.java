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

package nonapi.org.paramixel.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ResourceLoader")
class ResourceLoaderTest {

    @Test
    @DisplayName("finds existing resource")
    void findsExistingResource() {
        InputStream stream = ResourceLoader.getResourceAsStream("version.properties");
        assertThat(stream).isNotNull();
    }

    @Test
    @DisplayName("finds resource when context class loader is null")
    void findsResourceWithNullContextClassLoader() {
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
    @DisplayName("falls back to defining class loader when context class loader cannot find resource")
    void fallsBackToDefiningClassLoader() {
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
    @DisplayName("returns null stream for nonexistent resource")
    void returnsNullStreamForNonexistentResource() {
        InputStream stream = ResourceLoader.getResourceAsStream("nonexistent.resource.that.does.not.exist");
        assertThat(stream).isNull();
    }

    @Nested
    @DisplayName("getResourceAsStream with explicit ClassLoader")
    class GetResourceAsStreamWithClassLoaderTests {

        @Test
        @DisplayName("finds resource through provided class loader")
        void findsResourceThroughProvidedClassLoader() {
            ClassLoader classLoader = getClass().getClassLoader();
            InputStream stream = ResourceLoader.getResourceAsStream("version.properties", classLoader);
            assertThat(stream).isNotNull();
        }

        @Test
        @DisplayName("falls back to defining class loader when provided class loader cannot find resource")
        void fallsBackWhenProvidedClassLoaderCannotFindResource() {
            ClassLoader emptyLoader = new ClassLoader(null) {};
            InputStream stream = ResourceLoader.getResourceAsStream("version.properties", emptyLoader);
            assertThat(stream).isNotNull();
        }

        @Test
        @DisplayName("returns null when no class loader finds the resource")
        void returnsNullWhenNoClassLoaderFindsResource() {
            ClassLoader emptyLoader = new ClassLoader(null) {};
            InputStream stream =
                    ResourceLoader.getResourceAsStream("nonexistent.resource.that.does.not.exist", emptyLoader);
            assertThat(stream).isNull();
        }

        @Test
        @DisplayName("works with null class loader via defining/system fallback")
        void worksWithNullClassLoader() {
            InputStream stream = ResourceLoader.getResourceAsStream("version.properties", (ClassLoader) null);
            assertThat(stream).isNotNull();
        }
    }
}

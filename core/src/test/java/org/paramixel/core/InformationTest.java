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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class InformationTest {

    @Test
    void shouldLoadVersionFromClasspathResource() throws IOException {
        String version = Information.getVersion();

        assertThat(version).isNotBlank();
        assertThat(version).isEqualTo(loadVersionFromResource());
    }

    @Test
    void shouldFindResourceWithNullContextClassLoader() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            InputStream stream = Information.getResourceAsStream("information.properties");
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
            InputStream stream = Information.getResourceAsStream("information.properties");
            assertThat(stream).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void shouldLoadVersionWhenContextClassLoaderIsNull() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            String version = Information.getVersion();
            assertThat(version).isNotBlank();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static String loadVersionFromResource() throws IOException {
        InputStream inputStream =
                Thread.currentThread().getContextClassLoader().getResourceAsStream("information.properties");
        assertThat(inputStream).isNotNull();
        Properties properties = new Properties();
        try (inputStream) {
            properties.load(inputStream);
        }
        return properties.getProperty("version");
    }

    @Test
    void shouldReturnConsistentVersionAcrossCalls() {
        String first = Information.getVersion();
        String second = Information.getVersion();
        assertThat(first).isSameAs(second);
        assertThat(first).isNotBlank();
    }

    @Test
    void shouldReturnNonNullNonBlankVersion() {
        String version = Information.getVersion();
        assertThat(version).isNotNull();
        assertThat(version).isNotBlank();
    }

    @Test
    void shouldReturnNullStreamForNonexistentResource() {
        InputStream stream = Information.getResourceAsStream("nonexistent.resource.that.does.not.exist");
        assertThat(stream).isNull();
    }
}

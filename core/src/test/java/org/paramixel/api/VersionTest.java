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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Version")
class VersionTest {

    @Test
    @DisplayName("loads version from classpath resource")
    void loadsVersionFromClasspathResource() throws IOException {
        var version = Version.version();

        assertThat(version).isNotBlank();
        assertThat(version).isEqualTo(loadVersionFromResource());
    }

    @Test
    @DisplayName("loads version when context class loader is null")
    void loadsVersionWhenContextClassLoaderIsNull() {
        var original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            var version = Version.version();
            assertThat(version).isNotBlank();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    @DisplayName("returns consistent version across calls")
    void returnsConsistentVersionAcrossCalls() {
        var first = Version.version();
        var second = Version.version();
        assertThat(first).isSameAs(second);
        assertThat(first).isNotBlank();
    }

    @Test
    @DisplayName("returns non-null non-blank version")
    void returnsNonNullNonBlankVersion() {
        var version = Version.version();
        assertThat(version).isNotNull();
        assertThat(version).isNotBlank();
    }

    @Nested
    @DisplayName("loadVersion()")
    class LoadVersion {

        @Test
        @DisplayName("returns UNKNOWN when resource provider returns null")
        void returnsUnknownWhenResourceAbsent() {
            var result = Version.loadVersion(name -> null);
            assertThat(result).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("returns UNKNOWN when InputStream throws IOException on load")
        void returnsUnknownWhenIOExceptionOnLoad() {
            var broken = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("simulated read failure");
                }
            };
            var result = Version.loadVersion(name -> broken);
            assertThat(result).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("returns UNKNOWN when version property is missing")
        void returnsUnknownWhenVersionPropertyMissing() {
            var content = "other=value".getBytes(StandardCharsets.ISO_8859_1);
            var result = Version.loadVersion(name -> new ByteArrayInputStream(content));
            assertThat(result).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("returns UNKNOWN when version property is blank")
        void returnsUnknownWhenVersionPropertyBlank() {
            var content = "paramixel.core.version=   ".getBytes(StandardCharsets.ISO_8859_1);
            var result = Version.loadVersion(name -> new ByteArrayInputStream(content));
            assertThat(result).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("returns version string when present and non-blank")
        void returnsVersionWhenPresent() {
            var content = "paramixel.core.version=1.2.3".getBytes(StandardCharsets.ISO_8859_1);
            var result = Version.loadVersion(name -> new ByteArrayInputStream(content));
            assertThat(result).isEqualTo("1.2.3");
        }

        @Test
        @DisplayName("throws NullPointerException when resourceProvider is null")
        void throwsForNullProvider() {
            assertThatThrownBy(() -> Version.loadVersion(null)).isInstanceOf(NullPointerException.class);
        }
    }

    private static String loadVersionFromResource() throws IOException {
        var inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("core-version.properties");
        assertThat(inputStream).isNotNull();
        var properties = new Properties();
        try (inputStream) {
            properties.load(inputStream);
        }
        return properties.getProperty("paramixel.core.version");
    }
}

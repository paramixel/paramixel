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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("License")
class LicenseTest {

    @Test
    @DisplayName("META-INF/LICENSE is present on the classpath")
    void licenseIsPresentOnClasspath() throws IOException {
        var urls = Thread.currentThread().getContextClassLoader().getResources("META-INF/LICENSE");

        assertThat(urls.hasMoreElements()).isTrue();

        var found = false;
        while (urls.hasMoreElements()) {
            var url = urls.nextElement();
            if (url.toString().contains("core")) {
                found = true;
                try (var inputStream = url.openStream()) {
                    var content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    assertThat(content).contains("Apache License");
                    assertThat(content).contains("Version 2.0");
                }
                break;
            }
        }

        assertThat(found).as("META-INF/LICENSE should be found in core module").isTrue();
    }
}

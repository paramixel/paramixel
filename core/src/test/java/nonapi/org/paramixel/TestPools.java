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

package nonapi.org.paramixel;

import java.util.HashMap;
import java.util.Map;
import org.paramixel.api.Configuration;

/**
 * Test helper: builds a {@link Configuration} that contains the concurrency pools the unit tests use (defined as
 * system properties by the surefire configuration in {@code core/pom.xml}) plus any extra entries.
 */
public final class TestPools {

    private TestPools() {
        // Intentionally empty
    }

    /**
     * Returns a configuration with the test pools and the supplied entries.
     *
     * @param extra additional configuration entries, which win over the test pools; must not be {@code null}
     * @return the configuration
     */
    public static Configuration configuration(final Map<String, String> extra) {
        var map = new HashMap<String, String>();
        System.getProperties().forEach((key, value) -> {
            if (String.valueOf(key).startsWith(Configuration.RUNNER_PARALLELISM + ".")) {
                map.put(String.valueOf(key), String.valueOf(value));
            }
        });
        map.putAll(extra);
        return Configuration.of(map);
    }
}

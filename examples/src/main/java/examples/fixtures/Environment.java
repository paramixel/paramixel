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

package examples.fixtures;

import java.util.List;

/**
 * One environment a fixture is parameterized by, standing in for the per-environment fixtures a real suite uses.
 *
 * @param name the environment name used as the fixture display name
 * @param enabled whether the environment is enabled in this run
 */
public record Environment(String name, boolean enabled) {

    /**
     * Returns the environments this example runs against.
     *
     * @return the environments, in declaration order
     */
    public static List<Environment> all() {
        return List.of(
                new Environment("plain", true),
                new Environment("tls", true),
                new Environment("legacy", false),
                new Environment("fips", true));
    }

    /**
     * Returns the environments that are enabled.
     *
     * @return the supported environments
     */
    public static List<Environment> supported() {
        return all().stream().filter(Environment::enabled).toList();
    }
}

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

package examples.support;

import org.testcontainers.containers.Network;

/**
 * Creates Testcontainers {@link Network} instances with eager Docker allocation to
 * prevent race conditions during parallel container startup.
 */
public final class NetworkFactory {

    private NetworkFactory() {
        // Intentionally empty
    }

    /**
     * Creates a new Docker network and eagerly materializes it by calling
     * {@link Network#getId()}, ensuring the network is fully allocated before any
     * container references it.
     *
     * @return a new, eagerly-allocated Testcontainers network
     */
    public static Network createNetwork() {
        var network = Network.newNetwork();
        // Eagerly allocate the Docker network by calling getId().
        // This ensures the network is fully materialized before any
        // container references it, preventing race conditions during
        // parallel container startup.
        network.getId();
        return network;
    }
}

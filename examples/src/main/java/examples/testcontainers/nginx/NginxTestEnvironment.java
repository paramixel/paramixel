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

package examples.testcontainers.nginx;

import examples.support.Resource;
import examples.testcontainers.util.ContainerLogConsumer;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.NginxContainer;
import org.testcontainers.utility.DockerImageName;

public class NginxTestEnvironment implements AutoCloseable {

    private final String dockerImageName;

    private final String argumentName;

    private NginxContainer<?> nginxContainer;

    public NginxTestEnvironment(final String dockerImageName) {
        this.dockerImageName = dockerImageName;
        this.argumentName = dockerImageName.replace("[", "").replace("]", "");
    }

    public String name() {
        return argumentName;
    }

    public void initialize(final Network network) {
        nginxContainer = new NginxContainer<>(DockerImageName.parse(dockerImageName))
                .withNetwork(network)
                .withStartupAttempts(3)
                .withLogConsumer(new ContainerLogConsumer(getClass().getName(), argumentName))
                .withStartupTimeout(Duration.ofSeconds(30));

        try {
            nginxContainer.start();
        } catch (Exception e) {
            nginxContainer.stop();
            throw e;
        }
    }

    public boolean isRunning() {
        return nginxContainer.isRunning();
    }

    public NginxContainer<?> getNginxContainer() {
        return nginxContainer;
    }

    public void close() {
        if (nginxContainer != null) {
            nginxContainer.stop();
            nginxContainer = null;
        }
    }

    public static List<NginxTestEnvironment> createTestEnvironments() throws IOException {
        var nginxTestEnvironments = new ArrayList<NginxTestEnvironment>();

        for (String version : Resource.load(NginxTestEnvironment.class, "/docker-images.txt")) {
            nginxTestEnvironments.add(new NginxTestEnvironment(version));
        }

        return nginxTestEnvironments;
    }
}

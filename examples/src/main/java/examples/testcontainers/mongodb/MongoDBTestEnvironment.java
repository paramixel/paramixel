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

package examples.testcontainers.mongodb;

import examples.support.Resource;
import examples.testcontainers.util.ContainerLogConsumer;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

public class MongoDBTestEnvironment implements AutoCloseable {

    private final String dockerImageName;

    private final String argumentName;

    private MongoDBContainer mongoDBContainer;

    public MongoDBTestEnvironment(final String dockerImageName) {
        this.dockerImageName = dockerImageName;
        this.argumentName = dockerImageName.replace("[", "").replace("]", "");
    }

    public String name() {
        return argumentName;
    }

    public void initialize(final Network network) {
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse(dockerImageName))
                .withNetwork(network)
                .withStartupAttempts(3)
                .withLogConsumer(new ContainerLogConsumer(getClass().getName(), argumentName))
                .withStartupTimeout(Duration.ofSeconds(30));

        try {
            mongoDBContainer.start();
        } catch (Exception e) {
            mongoDBContainer.stop();
            throw e;
        }
    }

    public boolean isRunning() {
        return mongoDBContainer != null && mongoDBContainer.isRunning();
    }

    public String getConnectionString() {
        return mongoDBContainer.getConnectionString();
    }

    public void close() {
        if (mongoDBContainer != null) {
            mongoDBContainer.stop();
            mongoDBContainer = null;
        }
    }

    public static List<MongoDBTestEnvironment> createTestEnvironments() throws IOException {
        var mongoDBTestEnvironments = new ArrayList<MongoDBTestEnvironment>();

        for (String version : Resource.load(MongoDBTestEnvironment.class, "/docker-images.txt")) {
            mongoDBTestEnvironments.add(new MongoDBTestEnvironment(version));
        }

        return mongoDBTestEnvironments;
    }
}

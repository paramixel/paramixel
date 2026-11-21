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

package examples.altcontainers.mongodb;

import java.time.Duration;
import java.util.function.Consumer;
import org.altcontainers.api.ContainerSpec;
import org.altcontainers.api.GenericContainerSpec;
import org.altcontainers.api.Network;
import org.altcontainers.api.OutputFrame;

/**
 * A {@link ContainerSpec} pre-configured for a {@code mongo} image.
 *
 * <p>Altcontainers has no MongoDB module, so the image configuration is stated here
 * explicitly: exposed port 27017, a log-wait readiness condition, a 60-second startup
 * timeout, and 3 startup attempts. No environment variables or custom commands are
 * set — MongoDB image defaults are used, so the container runs a standalone
 * {@code mongod} rather than a single-node replica set.
 *
 * <p>Readiness waits for {@code mongod} to log that it is waiting for connections
 * rather than for port 27017 to accept a TCP connection. {@code mongod} binds the
 * port well before it can serve commands, so a port-based condition hands out a
 * container that resets connections issued against it.
 *
 * <p>Use {@link #builder(String)} to create a {@code MongoDBContainerSpec}:
 *
 * <pre>{@code
 * MongoDBContainerSpec spec = MongoDBContainerSpec.builder("mongo:8.2.5")
 *         .network(network, "mongodb")
 *         .outputConsumer(new ContainerLogConsumer("MongoDB", "mongo:8.2.5"))
 *         .build();
 * Container container = Container.create(spec);
 * }</pre>
 */
public final class MongoDBContainerSpec extends GenericContainerSpec {

    /**
     * The MongoDB client port exposed to the host.
     */
    public static final int MONGODB_PORT = 27017;

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

    private static final int STARTUP_ATTEMPTS = 3;

    /**
     * Matches the {@code mongod} log line emitted once the server accepts commands.
     */
    private static final String READY_LOG_REGEX = ".*\"msg\":\"Waiting for connections\".*";

    private MongoDBContainerSpec(final GenericContainerSpec genericContainerSpec) {
        super(genericContainerSpec);
    }

    /**
     * Creates a new builder for a MongoDB container spec.
     *
     * @param image the Docker image name (e.g. {@code "mongo:8.2.5"})
     * @return a mutable builder pre-configured for MongoDB
     */
    public static Builder builder(final String image) {
        return new Builder(image);
    }

    /**
     * Mutable builder for configuring a {@link MongoDBContainerSpec}.
     *
     * <p>The builder pre-fills MongoDB defaults: port 27017, log-wait readiness
     * condition, 60-second startup timeout, 3 startup attempts. Callers specify
     * only image, network, and optionally an output consumer.
     *
     * <p>Instances are mutable and not thread-safe.
     */
    public static final class Builder {

        private final GenericContainerSpec.Builder delegate;

        private Builder(final String image) {
            this.delegate = ContainerSpec.builder(image)
                    .exposePorts(MONGODB_PORT)
                    .startupTimeout(STARTUP_TIMEOUT)
                    .startupAttempts(STARTUP_ATTEMPTS)
                    .waitForLogMessage(READY_LOG_REGEX);
        }

        /**
         * Joins the container to a Docker network with the given DNS alias.
         *
         * @param network the network to join
         * @param alias DNS alias within the network
         * @return this builder
         */
        public Builder network(final Network network, final String alias) {
            delegate.network(network, alias);
            return this;
        }

        /**
         * Sets the output consumer for container output.
         *
         * @param consumer the output consumer
         * @return this builder
         */
        public Builder outputConsumer(final Consumer<OutputFrame> consumer) {
            delegate.outputListener(consumer);
            return this;
        }

        /**
         * Builds an immutable {@link MongoDBContainerSpec}.
         *
         * @return a new MongoDB container spec
         */
        public MongoDBContainerSpec build() {
            return new MongoDBContainerSpec(delegate.build());
        }
    }
}

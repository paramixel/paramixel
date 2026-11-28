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

package examples.altcontainers.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.altcontainers.api.ContainerSpec;
import org.altcontainers.api.GenericContainerSpec;
import org.altcontainers.api.Network;
import org.altcontainers.api.OutputFrame;

/**
 * A {@link ContainerSpec} pre-configured for an {@code apache/kafka} image running in
 * KRaft mode.
 *
 * <p>Altcontainers has no Kafka module, so the broker configuration is stated here
 * explicitly: KRaft environment variables, a single-node broker/controller quorum, the
 * broker log-wait readiness condition, a 3-minute startup timeout, and 3 startup
 * attempts.
 *
 * <p>The advertised listener cannot be known until Docker assigns the published host
 * port, so the image starts with a command that blocks until a start script appears.
 * The script is written by the {@code prepare} callback, after the container exists and
 * before it starts, and execs the image's normal entry point with
 * {@code KAFKA_ADVERTISED_LISTENERS} pointing at the mapped host port.
 *
 * <p>Use {@link #builder(String)} to create a {@code KafkaContainerSpec}:
 *
 * <pre>{@code
 * KafkaContainerSpec spec = KafkaContainerSpec.builder("apache/kafka:4.3.0")
 *         .network(network, "kafka")
 *         .outputConsumer(new ContainerLogConsumer("Kafka", "apache/kafka:4.3.0"))
 *         .build();
 * Container container = Container.create(spec);
 * }</pre>
 */
public final class KafkaContainerSpec extends GenericContainerSpec {

    /**
     * The Kafka client port exposed to the host.
     */
    public static final int KAFKA_PORT = 9092;

    /**
     * Internal broker-to-broker port (not exposed to the host).
     */
    private static final int BROKER_PORT = 9093;

    /**
     * Internal controller port for the KRaft quorum (not exposed to the host).
     */
    private static final int CONTROLLER_PORT = 9094;

    private static final long MEMORY = 1536L * 1024 * 1024;

    private static final long SHM_SIZE = 256L * 1024 * 1024;

    /**
     * Matches the broker log line emitted when the KRaft quorum has elected and the
     * broker is serving.
     */
    private static final String READY_LOG_REGEX = ".*Transitioning from RECOVERY to RUNNING.*";

    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);

    private static final int STARTUP_ATTEMPTS = 3;

    /**
     * File mode {@code 0777} (octal), written as a hexadecimal literal: readable,
     * writable, and executable by owner, group, and others.
     */
    private static final int START_SCRIPT_MODE = 0x1FF;

    private static final String START_SCRIPT_NAME = "kafka_start.sh";

    private static final String CLUSTER_ID = "4L6g3nShT-eMCtK--X86sw";

    private static Map<String, String> baseEnvironment() {
        final var environment = new LinkedHashMap<String, String>();
        environment.put("CLUSTER_ID", CLUSTER_ID);
        environment.put("KAFKA_NODE_ID", "1");
        environment.put("KAFKA_PROCESS_ROLES", "broker,controller");
        environment.put(
                "KAFKA_LISTENERS",
                "PLAINTEXT://0.0.0.0:" + KAFKA_PORT + ",BROKER://0.0.0.0:" + BROKER_PORT + ",CONTROLLER://0.0.0.0:"
                        + CONTROLLER_PORT);
        environment.put(
                "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "BROKER:PLAINTEXT,PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT");
        environment.put("KAFKA_INTER_BROKER_LISTENER_NAME", "BROKER");
        environment.put("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER");
        environment.put("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:" + CONTROLLER_PORT);
        environment.put("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1");
        environment.put("KAFKA_OFFSETS_TOPIC_NUM_PARTITIONS", "1");
        environment.put("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
        environment.put("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
        environment.put("KAFKA_LOG_FLUSH_INTERVAL_MESSAGES", String.valueOf(Long.MAX_VALUE));
        environment.put("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0");

        return environment;
    }

    private KafkaContainerSpec(final GenericContainerSpec genericContainerSpec) {
        super(genericContainerSpec);
    }

    /**
     * Creates a new builder for a Kafka container spec.
     *
     * @param image the Docker image name (e.g. {@code "apache/kafka:4.3.0"})
     * @return a mutable builder pre-configured for KRaft-mode Kafka
     */
    public static Builder builder(final String image) {
        return new Builder(image);
    }

    /**
     * Mutable builder for configuring a {@link KafkaContainerSpec}.
     *
     * <p>The builder pre-fills Kafka defaults: port 9092, KRaft environment variables,
     * the start-script handshake, log-message readiness condition, 3-minute startup
     * timeout, 3 startup attempts. Callers specify only image, network, and output
     * consumer.
     *
     * <p>Instances are mutable and not thread-safe.
     */
    public static final class Builder {

        private final GenericContainerSpec.Builder delegate;

        private Builder(final String image) {
            this.delegate = ContainerSpec.builder(image)
                    .command(
                            "sh",
                            "-c",
                            "while [ ! -f /tmp/" + START_SCRIPT_NAME + " ]; do sleep 0.1; done; /tmp/"
                                    + START_SCRIPT_NAME)
                    .exposePorts(KAFKA_PORT)
                    .memory(MEMORY)
                    .shmSize(SHM_SIZE)
                    .startupTimeout(STARTUP_TIMEOUT)
                    .startupAttempts(STARTUP_ATTEMPTS)
                    .waitForLogMessage(READY_LOG_REGEX)
                    .prepare(container -> container.copyFileToContainer(
                            "/tmp",
                            START_SCRIPT_NAME,
                            startScript(container.host(), container.hostPort(KAFKA_PORT)),
                            START_SCRIPT_MODE));
        }

        /**
         * Builds the start script, which advertises the mapped host port to clients and
         * the internal broker port to other brokers.
         *
         * @param host the host reachable by clients
         * @param hostPort the host port mapped to {@link #KAFKA_PORT}
         * @return the script content encoded as UTF-8
         */
        private static byte[] startScript(final String host, final int hostPort) {
            final var script = "#!/bin/bash\n"
                    + "export KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://" + host + ":" + hostPort + ",BROKER://localhost:"
                    + BROKER_PORT + "\n"
                    + "exec /etc/kafka/docker/run\n";

            return script.getBytes(StandardCharsets.UTF_8);
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
         * Builds an immutable {@link KafkaContainerSpec}.
         *
         * @return a new Kafka container spec
         */
        public KafkaContainerSpec build() {
            delegate.environment(baseEnvironment());
            return new KafkaContainerSpec(delegate.build());
        }
    }
}

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

import examples.altcontainers.util.ContainerLogConsumer;
import examples.support.Resource;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.altcontainers.api.Container;
import org.altcontainers.api.ContainerException;
import org.altcontainers.api.Network;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;

/**
 * Manages the lifecycle of a Kafka container for parameterized integration tests.
 * Starts a single-node KRaft broker inside a Docker container and provides methods
 * to create topics and retrieve connection details.
 *
 * <p>Supports two initialization modes:
 * <ul>
 *   <li>{@link #initialize()} — creates and owns the Docker network; {@link #close()}
 *       stops both the container and the network.</li>
 *   <li>{@link #initialize(Network)} — attaches to a caller-owned network;
 *       {@link #close()} stops only the container.</li>
 * </ul>
 *
 * <p>After the container starts, the broker is polled until it accepts API requests,
 * and the internal consumer-offsets topic is created so the group coordinator is
 * serving before any client joins a consumer group.
 *
 * <p>The container is stopped silently on failure during initialization.
 */
public class KafkaTestEnvironment implements AutoCloseable {

    /**
     * Bound on the broker API probe that runs after the container reports ready.
     */
    private static final Duration BROKER_READINESS_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    private static final int ADMIN_TIMEOUT_SECONDS = 8;

    private static final long READINESS_POLL_MILLIS = 500L;

    private static final long TOPIC_VISIBILITY_POLL_MILLIS = 50L;

    /**
     * Temporary topic used to prove the broker accepts create requests.
     */
    private static final String PROBE_TOPIC = "_readiness_probe";

    /**
     * Topic holding group coordination state. Kafka creates it lazily on the first
     * group-coordinator request.
     */
    private static final String CONSUMER_OFFSETS_TOPIC = "__consumer_offsets";

    /**
     * Topic configuration applied by the broker when it creates the consumer-offsets
     * topic itself; supplied here so the pre-created topic is equivalent.
     */
    private static final Map<String, String> CONSUMER_OFFSETS_CONFIG = Map.of("cleanup.policy", "compact");

    private final String dockerImageName;

    private final String argumentName;

    private volatile Container container;

    private volatile Network network;

    private volatile boolean ownsNetwork;

    /**
     * Creates a test environment for the given Docker image.
     *
     * @param dockerImageName the Kafka Docker image (e.g. {@code "apache/kafka:4.3.0"})
     */
    public KafkaTestEnvironment(final String dockerImageName) {
        this.dockerImageName = dockerImageName;
        this.argumentName = dockerImageName.replace("[", "").replace("]", "");
    }

    /**
     * Returns the display name derived from the Docker image, with bracket characters removed.
     *
     * @return the argument name used for test identification
     */
    public String name() {
        return argumentName;
    }

    /**
     * Creates a new Docker network and starts the Kafka container on it. The network
     * is owned by this environment; {@link #close()} will stop both the container
     * and the network.
     *
     * @see #initialize(Network)
     */
    public void initialize() {
        initialize(Network.create(), true);
    }

    /**
     * Starts the Kafka container on the given Docker network and waits for the broker
     * to accept API requests. The caller owns the network; {@link #close()} stops only
     * the container.
     *
     * <p>If startup or readiness probing fails, the container is stopped silently
     * before the exception is re-thrown. Restores the thread interrupt flag if the
     * failure was caused by an {@link InterruptedException}.
     *
     * @param network the Docker network to attach the container to
     * @see #initialize()
     */
    public void initialize(final Network network) {
        initialize(network, false);
    }

    private void initialize(final Network network, final boolean ownsNetwork) {
        this.network = Objects.requireNonNull(network, "network is null");
        this.ownsNetwork = ownsNetwork;
        var success = false;
        var interrupted = false;

        try {
            final var spec = KafkaContainerSpec.builder(dockerImageName)
                    .network(network, networkAlias())
                    .outputConsumer(new ContainerLogConsumer(getClass().getSimpleName(), argumentName))
                    .build();

            try {
                container = Container.create(spec);
                awaitBrokerReady();
                awaitGroupCoordinatorReady();
            } catch (Exception e) {
                interrupted = hasInterruptedException(e);
                throw e;
            }

            success = true;
        } finally {
            if (!success) {
                stopQuietly();
                closeNetworkIfOwned();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Polls the broker until it accepts API requests, or {@link #BROKER_READINESS_TIMEOUT}
     * elapses.
     */
    private void awaitBrokerReady() {
        final var deadline = System.nanoTime() + BROKER_READINESS_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            if (isBrokerReady()) {
                deleteProbeTopic();
                return;
            }

            try {
                Thread.sleep(READINESS_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ContainerException("Interrupted while waiting for Kafka broker readiness", e);
            }
        }

        throw new ContainerException("Kafka broker did not accept API requests within " + BROKER_READINESS_TIMEOUT);
    }

    /**
     * Tests whether the broker accepts API requests by creating the probe topic.
     *
     * @return {@code true} if the broker accepted the create request
     */
    private boolean isBrokerReady() {
        try {
            createTopic(PROBE_TOPIC);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteProbeTopic() {
        try (var adminClient = AdminClient.create(adminClientProperties())) {
            adminClient.deleteTopics(List.of(PROBE_TOPIC)).all().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Best-effort cleanup
        }
    }

    /**
     * Creates the consumer-offsets topic so the broker's group coordinator is serving
     * before any client joins a consumer group.
     *
     * <p>The broker creates this topic lazily when the first coordinator request
     * arrives. A client that joins while that creation is still in flight receives
     * {@code NOT_COORDINATOR} and has to rediscover the coordinator; creating the
     * topic up front removes the race. Topics are created with one partition, matching
     * {@code KAFKA_OFFSETS_TOPIC_NUM_PARTITIONS} in {@link KafkaContainerSpec}.
     */
    private void awaitGroupCoordinatorReady() {
        try {
            createTopic(CONSUMER_OFFSETS_TOPIC, CONSUMER_OFFSETS_CONFIG);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to create " + CONSUMER_OFFSETS_TOPIC, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContainerException("Interrupted while preparing the Kafka group coordinator", e);
        }
    }

    /**
     * Returns the {@code bootstrap.servers} URL for connecting Kafka clients to this broker.
     *
     * @return the bootstrap servers string
     * @throws IllegalStateException if called before {@link #initialize()} or {@link #initialize(Network)}
     */
    public String getBootstrapServers() {
        final var current = container;
        if (current == null) {
            throw new IllegalStateException("getBootstrapServers() called before initialize()");
        }

        return current.host() + ":" + current.hostPort(KafkaContainerSpec.KAFKA_PORT);
    }

    /**
     * Returns whether the Kafka container is currently running.
     *
     * @return {@code true} if the container has been started and not yet stopped
     */
    public boolean isRunning() {
        return container != null && container.isRunning();
    }

    /**
     * Returns the Docker network this container is attached to.
     *
     * @return the network, or {@code null} if not yet initialized or already closed
     */
    public Network getNetwork() {
        return network;
    }

    /**
     * Returns the underlying container instance for direct access.
     *
     * @return the container handle, or {@code null} if not yet initialized or already closed
     */
    public Container getContainer() {
        return container;
    }

    /**
     * Creates a Kafka topic with a single partition and replication factor of 1, then
     * waits until clients can use it. Silently succeeds if the topic already exists.
     *
     * <p>Returns only once the broker serves the topic with an elected leader for every
     * partition. A successful create request means the controller accepted the metadata;
     * until the broker's metadata cache applies it, clients still observe
     * {@code UNKNOWN_TOPIC_OR_PARTITION}.
     *
     * @param topic the topic name to create
     * @throws ExecutionException if an asynchronous admin operation fails (other than topic-exists)
     * @throws InterruptedException if the calling thread is interrupted; the interrupt flag is restored
     * @throws IllegalStateException if the create operation times out, or the topic never becomes available
     */
    public void createTopic(final String topic) throws ExecutionException, InterruptedException {
        createTopic(topic, Map.of());
    }

    private void createTopic(final String topic, final Map<String, String> configs)
            throws ExecutionException, InterruptedException {
        try (var adminClient = AdminClient.create(adminClientProperties())) {
            try {
                adminClient
                        .createTopics(List.of(new NewTopic(topic, 1, (short) 1).configs(configs)))
                        .all()
                        .get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new IllegalStateException("Timed out creating topic: " + topic, e);
            } catch (ExecutionException e) {
                if (!isTopicExists(e)) {
                    throw e;
                }
            }

            awaitTopicAvailable(adminClient, topic);
        }
    }

    /**
     * Polls the broker until the topic is served with an elected leader for every
     * partition.
     *
     * @param adminClient the admin client to poll with
     * @param topic the topic to wait for
     * @throws InterruptedException if the calling thread is interrupted; the interrupt flag is restored
     * @throws IllegalStateException if the topic does not become available in time
     */
    private void awaitTopicAvailable(final AdminClient adminClient, final String topic) throws InterruptedException {
        final var deadline =
                System.nanoTime() + Duration.ofSeconds(ADMIN_TIMEOUT_SECONDS).toNanos();

        while (System.nanoTime() < deadline) {
            try {
                if (isTopicAvailable(adminClient, topic)) {
                    return;
                }
            } catch (ExecutionException | TimeoutException e) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("Topic did not become available: " + topic, e);
                }
            }

            Thread.sleep(TOPIC_VISIBILITY_POLL_MILLIS);
        }

        throw new IllegalStateException("Topic did not become available: " + topic);
    }

    /**
     * Returns whether the broker serves the topic with an elected leader for every
     * partition.
     *
     * @param adminClient the admin client to describe the topic with
     * @param topic the topic to check
     * @return {@code true} if every partition has an elected leader
     * @throws ExecutionException if the describe request fails
     * @throws InterruptedException if the calling thread is interrupted; the interrupt flag is restored
     * @throws TimeoutException if the describe request does not complete in time
     */
    private static boolean isTopicAvailable(final AdminClient adminClient, final String topic)
            throws ExecutionException, InterruptedException, TimeoutException {
        final var descriptions =
                adminClient.describeTopics(List.of(topic)).allTopicNames().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        final var description = descriptions.get(topic);

        return description != null
                && description.partitions().stream()
                        .allMatch(partition ->
                                partition.leader() != null && partition.leader().id() >= 0);
    }

    /**
     * Returns whether the failure reports that the topic already exists.
     *
     * @param failure the create-topics failure
     * @return {@code true} if the topic already exists
     */
    private static boolean isTopicExists(final ExecutionException failure) {
        final var cause = failure.getCause();

        return cause instanceof TopicExistsException
                || (cause != null && cause.getCause() instanceof TopicExistsException);
    }

    private Properties adminClientProperties() {
        final var properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(PROBE_TIMEOUT.toMillis()));
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(PROBE_TIMEOUT.toMillis()));

        return properties;
    }

    /**
     * Stops the Kafka container silently, suppressing any exceptions. If this
     * environment owns the Docker network (created via {@link #initialize()}), the
     * network is also closed. Safe to call multiple times or when the container was
     * never started.
     */
    public void close() {
        stopQuietly();
        closeNetworkIfOwned();
    }

    private String networkAlias() {
        return argumentName.replaceAll("[^A-Za-z0-9_.-]", "-");
    }

    private void closeNetworkIfOwned() {
        if (ownsNetwork && network != null) {
            try {
                network.close();
            } catch (Exception ignored) {
                // Intentionally suppress close exception
            } finally {
                network = null;
                ownsNetwork = false;
            }
        }
    }

    private void stopQuietly() {
        if (container != null) {
            try {
                container.close();
            } catch (Exception ignored) {
                // Intentionally suppress stop exception to preserve original cause
            } finally {
                container = null;
            }
        }
    }

    private static boolean hasInterruptedException(final Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException) {
                return true;
            }
        }

        return false;
    }

    /**
     * Creates one {@link KafkaTestEnvironment} per Kafka Docker image listed in the
     * {@code /docker-images.txt} classpath resource.
     *
     * @return list of test environments, one per image version
     * @throws IOException if the resource file cannot be read
     */
    public static List<KafkaTestEnvironment> createTestEnvironments() throws IOException {
        return Resource.load(KafkaTestEnvironment.class, "/docker-images.txt").stream()
                .map(KafkaTestEnvironment::new)
                .toList();
    }
}

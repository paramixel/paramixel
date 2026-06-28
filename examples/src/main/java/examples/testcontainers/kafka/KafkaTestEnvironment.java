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

package examples.testcontainers.kafka;

import examples.support.NetworkFactory;
import examples.support.Resource;
import examples.testcontainers.util.ContainerLogConsumer;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Manages the lifecycle of a Kafka container for parameterized integration tests.
 * Starts a single-node Kafka broker inside a Docker container and provides methods
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
 * <p>The container is stopped silently on failure during initialization.
 */
public class KafkaTestEnvironment implements AutoCloseable {

    private static final Duration INITIALIZE_TIMEOUT = Duration.ofMinutes(3);

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration BROKER_READINESS_TIMEOUT = Duration.ofSeconds(60);

    private static final int ADMIN_TIMEOUT_SECONDS = 8;

    private final String dockerImageName;

    private final String argumentName;

    private volatile KafkaContainer kafkaContainer;

    private volatile Network network;

    private volatile boolean ownsNetwork;

    /**
     * Creates a test environment for the given Docker image.
     *
     * @param dockerImageName the Kafka Docker image (e.g. {@code "apache/kafka:3.7.0"})
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
     * @throws ContainerLaunchException if the container fails to start or the broker does not become ready
     * @see #initialize(Network)
     */
    public void initialize() {
        initialize(NetworkFactory.createNetwork(), true);
    }

    /**
     * Starts the Kafka container on the given Docker network and waits for the broker
     * to become ready. The caller owns the network; {@link #close()} stops only
     * the container.
     *
     * <p>If startup or readiness probing fails, the container is stopped silently
     * before the exception is re-thrown. Restores the thread interrupt flag if the
     * failure was caused by an {@link InterruptedException}.
     *
     * @param network the Testcontainers network to attach the container to
     * @throws ContainerLaunchException if the container fails to start or the broker does not become ready
     * @see #initialize()
     */
    public void initialize(final Network network) {
        initialize(network, false);
    }

    private void initialize(final Network network, final boolean ownsNetwork) {
        this.network = Objects.requireNonNull(network, "network is null");
        this.ownsNetwork = ownsNetwork;
        boolean success = false;
        boolean interrupted = false;

        try {
            final var image = DockerImageName.parse(dockerImageName).asCompatibleSubstituteFor("apache/kafka");

            kafkaContainer = new KafkaContainer(image)
                    .withNetwork(network)
                    .withStartupAttempts(3)
                    .withStartupTimeout(Duration.ofMinutes(3))
                    .withLogConsumer(new ContainerLogConsumer(getClass().getSimpleName(), argumentName))
                    .waitingFor(Wait.forLogMessage(".*Transitioning from RECOVERY to RUNNING.*", 1)
                            .withStartupTimeout(INITIALIZE_TIMEOUT))
                    .withReuse(false);

            try {
                kafkaContainer.start();
                waitForBrokerReadiness();
            } catch (Exception e) {
                captureLogsOnFailure(e);
                if (hasInterruptedException(e)) {
                    interrupted = true;
                }
                throw e;
            }

            success = true;
        } finally {
            if (!success) {
                stopQuietly();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Returns the {@code bootstrap.servers} URL for connecting Kafka clients to this broker.
     *
     * @return the bootstrap servers string
     * @throws IllegalStateException if called before {@link #initialize()} or {@link #initialize(Network)}
     */
    public String getBootstrapServers() {
        return kafkaContainer.getBootstrapServers();
    }

    /**
     * Returns whether the Kafka container is currently running.
     *
     * @return {@code true} if the container has been started and not yet stopped
     */
    public boolean isRunning() {
        return kafkaContainer != null && kafkaContainer.isRunning();
    }

    /**
     * Returns the Docker network this container is attached to.
     *
     * @return the Testcontainers network, or {@code null} if not yet initialized
     */
    public Network getNetwork() {
        return network;
    }

    /**
     * Creates a Kafka topic with a single partition and replication factor of 1.
     * Silently succeeds if the topic already exists.
     *
     * @param topic the topic name to create
     * @throws ExecutionException if an asynchronous admin operation fails (other than topic-exists)
     * @throws InterruptedException if the calling thread is interrupted; the interrupt flag is restored
     */
    public void createTopic(final String topic) throws ExecutionException, InterruptedException {
        var properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(PROBE_TIMEOUT.toMillis()));
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(PROBE_TIMEOUT.toMillis()));

        try (var adminClient = AdminClient.create(properties)) {
            adminClient
                    .createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                    .all()
                    .get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out creating topic: " + topic, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof TopicExistsException
                    || (cause != null && cause.getCause() instanceof TopicExistsException)) {
                return;
            }

            throw e;
        }
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

    private void waitForBrokerReadiness() {
        var properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(PROBE_TIMEOUT.toMillis()));
        properties.put("default.api.timeout.ms", String.valueOf(PROBE_TIMEOUT.toMillis()));
        properties.put("reconnect.backoff.ms", "1000");
        properties.put("reconnect.backoff.max.ms", "2000");
        properties.put("retries", "5");

        var listTopicsOptions = new ListTopicsOptions()
                .timeoutMs((int) PROBE_TIMEOUT.toMillis())
                .listInternal(true);

        var deadlineNanos = System.nanoTime() + BROKER_READINESS_TIMEOUT.toNanos();
        var attempt = 0;
        var maxBackoffMs = 2_000L;
        Exception lastException = null;

        while (System.nanoTime() < deadlineNanos) {
            try (var adminClient = AdminClient.create(properties)) {
                adminClient.describeCluster().nodes().get(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                adminClient.listTopics(listTopicsOptions).names().get(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ContainerLaunchException("Interrupted while waiting for Kafka broker readiness", e);
            } catch (Exception e) {
                lastException = e;
                long delay = Math.min(100L * (1L << Math.min(attempt, 4)), maxBackoffMs);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ee) {
                    Thread.currentThread().interrupt();
                    throw new ContainerLaunchException("Interrupted while waiting for Kafka broker readiness", ee);
                }
                attempt++;
            }
        }

        throw new ContainerLaunchException(
                "Kafka broker failed to become ready within " + BROKER_READINESS_TIMEOUT, lastException);
    }

    private void stopQuietly() {
        if (kafkaContainer != null) {
            try {
                kafkaContainer.stop();
            } catch (Exception ignored) {
            } finally {
                kafkaContainer = null;
            }
        }
    }

    private void captureLogsOnFailure(final Exception originalCause) {
        if (kafkaContainer == null) {
            return;
        }
        try {
            System.err.printf(
                    "Kafka container logs (cause: %s):%n%s%n", originalCause.getMessage(), kafkaContainer.getLogs());
        } catch (Exception ignored) {
            // Intentionally empty
        }
    }

    private static boolean hasInterruptedException(Throwable t) {
        while (t != null) {
            if (t instanceof InterruptedException) {
                return true;
            }
            t = t.getCause();
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

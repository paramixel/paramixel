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

import examples.support.Resource;
import examples.testcontainers.util.ContainerLogConsumer;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

public class KafkaTestEnvironment implements AutoCloseable {

    private static final Duration INITIALIZE_TIMEOUT = Duration.ofMinutes(3);

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration BROKER_READINESS_TIMEOUT = Duration.ofSeconds(60);

    private static final int ADMIN_TIMEOUT_SECONDS = 8;

    private final String dockerImageName;

    private final String argumentName;

    private KafkaContainer kafkaContainer;

    public KafkaTestEnvironment(final String dockerImageName) {
        this.dockerImageName = dockerImageName;
        this.argumentName = dockerImageName.replace("[", "").replace("]", "");
    }

    public String name() {
        return argumentName;
    }

    public void initialize(final Network network) {
        boolean success = false;
        boolean interrupted = false;

        try {
            final DockerImageName image =
                    DockerImageName.parse(dockerImageName).asCompatibleSubstituteFor("apache/kafka");

            kafkaContainer = new KafkaContainer(image)
                    .withNetwork(network)
                    .withStartupAttempts(1)
                    .withLogConsumer(new ContainerLogConsumer(getClass().getName(), argumentName))
                    .waitingFor(Wait.forLogMessage(".*Transitioning from RECOVERY to RUNNING.*", 1)
                            .withStartupTimeout(INITIALIZE_TIMEOUT))
                    .withReuse(false);

            try {
                kafkaContainer.start();
                waitForBrokerReadiness();
            } catch (Exception e) {
                captureLogsOnFailure(e);
                if (e.getCause() instanceof InterruptedException) {
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

    public String getBootstrapServers() {
        return kafkaContainer.getBootstrapServers();
    }

    public boolean isRunning() {
        return kafkaContainer != null && kafkaContainer.isRunning();
    }

    public void createTopic(final String topic) throws ExecutionException, InterruptedException {
        var properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(PROBE_TIMEOUT.toMillis()));
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(PROBE_TIMEOUT.toMillis()));

        try (var adminClient = AdminClient.create(properties)) {
            adminClient
                    .createTopics(Collections.singletonList(new NewTopic(topic, 1, (short) 1)))
                    .all()
                    .get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out creating topic: " + topic, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TopicExistsException
                    || (cause != null && cause.getCause() instanceof TopicExistsException)) {
                return;
            }

            throw e;
        }
    }

    public void close() {
        stopQuietly();
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
        var maxBackoffMs = 2000L;
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
                long delay = Math.min(100L * (1L << attempt), maxBackoffMs);
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

    public static List<KafkaTestEnvironment> createTestEnvironments() throws IOException {
        var kafkaTestEnvironments = new ArrayList<KafkaTestEnvironment>();
        for (String version : Resource.load(KafkaTestEnvironment.class, "/docker-images.txt")) {
            kafkaTestEnvironments.add(new KafkaTestEnvironment(version));
        }
        return kafkaTestEnvironments;
    }
}

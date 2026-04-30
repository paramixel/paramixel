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
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class KafkaTestEnvironment implements AutoCloseable {

    private static final Duration INITIALIZE_TIMEOUT = Duration.ofMinutes(3);

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(8);

    private static final Duration PROBE_INTERVAL = Duration.ofMillis(500);

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

    public void initialize(final Network network) throws Exception {
        boolean interrupted = false;

        try {
            final long deadlineNanos = System.nanoTime() + INITIALIZE_TIMEOUT.toNanos();

            final DockerImageName image =
                    DockerImageName.parse(dockerImageName).asCompatibleSubstituteFor("apache/kafka");

            kafkaContainer = new KafkaContainer(image)
                    .withNetwork(network)
                    .withStartupAttempts(1)
                    .withLogConsumer(new ContainerLogConsumer(getClass().getName(), argumentName))
                    .waitingFor(Wait.forLogMessage(".*[Kk]afka.*[Ss]erver.*started.*\\n", 1)
                            .withStartupTimeout(INITIALIZE_TIMEOUT));

            try {
                kafkaContainer.start();
            } catch (Exception e) {
                captureLogsOnFailure(e);
                stopQuietly();
                throw e;
            }

            long remainingNanos = deadlineNanos - System.nanoTime();

            if (remainingNanos <= 0) {
                stopQuietly();
                throw new IllegalStateException("Kafka container started, but the overall initialize timeout of "
                        + INITIALIZE_TIMEOUT + " was already exhausted.");
            }

            try {
                awaitKafkaReady(kafkaContainer.getBootstrapServers(), Duration.ofNanos(remainingNanos));
            } catch (Exception e) {
                captureLogsOnFailure(e);
                stopQuietly();
                throw e;
            }

        } catch (InterruptedException e) {
            interrupted = true;
            stopQuietly();
            throw e;
        } finally {
            // Restore the interrupt flag if it was cleared anywhere in the try block.
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void awaitKafkaReady(final String bootstrapServers, final Duration timeout) throws Exception {
        final long deadlineNanos = System.nanoTime() + timeout.toNanos();

        // toMillis() is available in Java 8. toSeconds() is Java 9+.
        final long probeTimeoutMs = PROBE_TIMEOUT.toMillis();

        // Safe narrowing: ListTopicsOptions.timeoutMs() takes an int.
        // In practice PROBE_TIMEOUT is well under Integer.MAX_VALUE ms (~24 days),
        // but the clamp makes the contract explicit.
        var listTopicsOptions = new ListTopicsOptions()
                .timeoutMs((int) Math.min(probeTimeoutMs, Integer.MAX_VALUE))
                .listInternal(true);

        Exception lastException = null;

        while (System.nanoTime() < deadlineNanos) {
            // Fresh AdminClient per probe — no stale internal backoff state.
            var props = buildAdminClientProperties(bootstrapServers, probeTimeoutMs);
            try (var adminClient = AdminClient.create(props)) {

                // describeCluster: proves the broker can answer metadata requests.
                adminClient.describeCluster().nodes().get(probeTimeoutMs, TimeUnit.MILLISECONDS);

                // listTopics: flushes out "partially ready" broker states.
                adminClient.listTopics(listTopicsOptions).names().get(probeTimeoutMs, TimeUnit.MILLISECONDS);

                return; // Broker is ready.

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            } catch (Exception e) {
                lastException = e;
            }

            // Back off before next probe — but honour the deadline.
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }

            long sleepMs = Math.min(PROBE_INTERVAL.toMillis(), remainingNanos / 1_000_000L);
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
            }
        }

        var message = "Kafka did not become ready within " + timeout + " (bootstrap.servers=" + bootstrapServers + ")";

        throw lastException != null
                ? new IllegalStateException(message, lastException)
                : new IllegalStateException(message);
    }

    private static Properties buildAdminClientProperties(final String bootstrapServers, final long probeTimeoutMs) {
        var props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);

        // Hard deadline for any single Kafka request.
        props.put("request.timeout.ms", Long.toString(probeTimeoutMs));
        props.put("default.api.timeout.ms", Long.toString(probeTimeoutMs));

        // Disable metadata caching so each fresh AdminClient always fetches
        // current broker state rather than serving a stale "unavailable" entry.
        props.put("metadata.max.age.ms", Long.toString(probeTimeoutMs));

        // Close idle connections quickly so failed probes don't leave sockets open.
        props.put("connections.max.idle.ms", Long.toString(probeTimeoutMs + 1000L));

        // Prevent internal exponential backoff from growing beyond one probe cycle.
        props.put("reconnect.backoff.ms", "100");
        props.put("reconnect.backoff.max.ms", Long.toString(probeTimeoutMs / 2));

        // All retry logic is ours — disable internal retries to avoid double-waiting.
        props.put("retries", "0");

        return props;
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

    public boolean isRunning() {
        return kafkaContainer != null && kafkaContainer.isRunning();
    }

    public String getBootstrapServers() {
        return kafkaContainer.getBootstrapServers();
    }

    public void close() {
        stopQuietly();
    }

    public void createTopic(final String topic) throws ExecutionException, InterruptedException {
        var p = new Properties();
        p.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());

        try (var admin = AdminClient.create(p)) {
            admin.createTopics(Collections.singletonList(new NewTopic(topic, 1, (short) 1)))
                    .all()
                    .get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TopicExistsException
                    || (cause != null && cause.getCause() instanceof TopicExistsException)) {
                return;
            }

            throw e;
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

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

package examples.testcontainers.bufstream;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import examples.support.Resource;
import examples.testcontainers.util.ContainerLogConsumer;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

public class BufstreamTestEnvironment implements AutoCloseable {

    private final String dockerImageName;

    private final String argumentName;

    private GenericContainer<?> genericContainer;

    public BufstreamTestEnvironment(final String dockerImageName) {
        this.dockerImageName = dockerImageName;
        this.argumentName = dockerImageName.replace("[", "").replace("]", "");
    }

    public String name() {
        return argumentName;
    }

    public void initialize(final Network network) throws IOException {
        for (int i = 0; i < 10; i++) {
            int kafkaPort = getFreePort();
            int adminPort = getFreePort();

            String configuration = buildConfiguration(kafkaPort);

            GenericContainer<?> container = null;

            try {
                container = new GenericContainer<>(DockerImageName.parse(dockerImageName))
                        .withNetwork(network)
                        .withNetworkAliases("bufstream-" + randomId())
                        .withExposedPorts(9092, 9089)
                        .withCopyToContainer(Transferable.of(configuration), "/etc/bufstream.yaml")
                        .withCommand("serve", "--inmemory", "-c", "/etc/bufstream.yaml")
                        .withCreateContainerCmdModifier(
                                createContainerCmd -> bindHostPorts(createContainerCmd, kafkaPort, adminPort))
                        .withLogConsumer(new ContainerLogConsumer(getClass().getName(), argumentName))
                        .withStartupAttempts(3)
                        .waitingFor(Wait.forHttp("/-/status").forPort(9089).forStatusCode(200))
                        .withStartupTimeout(Duration.ofSeconds(30));

                container.start();

                genericContainer = container;

                return;
            } catch (Exception e) {
                safeStop(container);

                if (i < 9) {
                    // try again
                    continue;
                }

                throw e;
            }
        }
    }

    public boolean isRunning() {
        return genericContainer.isRunning();
    }

    public String getBootstrapServers() {
        return genericContainer.getHost() + ":" + genericContainer.getMappedPort(9092);
    }

    public void close() {
        if (genericContainer != null) {
            genericContainer.stop();
            genericContainer = null;
        }
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

    public static List<BufstreamTestEnvironment> createTestEnvironments() throws IOException {
        var bufstreamTestEnvironments = new ArrayList<BufstreamTestEnvironment>();

        for (String version : Resource.load(BufstreamTestEnvironment.class, "/docker-images.txt")) {
            bufstreamTestEnvironments.add(new BufstreamTestEnvironment(version));
        }

        return bufstreamTestEnvironments;
    }

    private String buildConfiguration(final int HOST_KAFKA_PORT) {
        if (dockerImageName.contains("0.3.")) {
            return """
                kafka:
                  address: 0.0.0.0:9092
                  public_address: localhost:%d
                  num_partitions: 1
                admin_address: 0.0.0.0:9089
                """.formatted(HOST_KAFKA_PORT);
        }

        return """
            version: v1beta1
            cluster: test
            admin:
              listen_address: 0.0.0.0:9089
            kafka:
              listeners:
                - name: plain
                  listen_address: 0.0.0.0:9092
                  advertise_address: localhost:%d
            metadata:
              etcd: {}
            """.formatted(HOST_KAFKA_PORT);
    }

    private static void bindHostPorts(
            final CreateContainerCmd createContainerCmd, final int HOST_KAFKA_PORT, final int HOST_ADMIN_PORT) {
        var bindings = new Ports();
        bindings.bind(ExposedPort.tcp(9092), Ports.Binding.bindPort(HOST_KAFKA_PORT));
        bindings.bind(ExposedPort.tcp(9089), Ports.Binding.bindPort(HOST_ADMIN_PORT));

        Objects.requireNonNull(createContainerCmd.getHostConfig()).withPortBindings(bindings);
    }

    private static void safeStop(final GenericContainer<?> container) {
        if (container == null) {
            return;
        }

        try {
            container.stop();
        } catch (Exception ignored) {
        }
    }

    private static int getFreePort() throws IOException {
        try (var serverSocket = new ServerSocket(0)) {
            serverSocket.setReuseAddress(true);
            return serverSocket.getLocalPort();
        }
    }

    private static String randomId() {
        int leftLimit = 97; // 'a'
        int rightLimit = 122; // 'z'
        int targetStringLength = 8;

        return ThreadLocalRandom.current()
                .ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}

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

import examples.support.NetworkFactory;
import examples.support.Resource;
import examples.testcontainers.util.ContainerLogConsumer;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.NginxContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Manages the lifecycle of an Nginx container for parameterized integration tests.
 * Starts an Nginx server inside a Docker container and provides access to the
 * raw container for port mapping.
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
public class NginxTestEnvironment implements AutoCloseable {

    private final String dockerImageName;

    private final String argumentName;

    private volatile NginxContainer<?> nginxContainer;

    private volatile Network network;

    private volatile boolean ownsNetwork;

    /**
     * Creates a test environment for the given Docker image.
     *
     * @param dockerImageName the Nginx Docker image (e.g. {@code "nginx:1.25"})
     */
    public NginxTestEnvironment(final String dockerImageName) {
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
     * Creates a new Docker network and starts the Nginx container on it. The network
     * is owned by this environment; {@link #close()} will stop both the container
     * and the network.
     *
     * @see #initialize(Network)
     */
    public void initialize() {
        initialize(NetworkFactory.createNetwork(), true);
    }

    /**
     * Starts the Nginx container on the given Docker network and waits for it to
     * accept connections. The caller owns the network; {@link #close()} stops only
     * the container.
     *
     * <p>If startup fails, the container is stopped silently before the exception
     * is re-thrown.
     *
     * @param network the Testcontainers network to attach the container to
     * @see #initialize()
     */
    public void initialize(final Network network) {
        initialize(network, false);
    }

    private void initialize(final Network network, final boolean ownsNetwork) {
        this.network = Objects.requireNonNull(network, "network must not be null");
        this.ownsNetwork = ownsNetwork;
        nginxContainer = new NginxContainer<>(DockerImageName.parse(dockerImageName))
                .withNetwork(network)
                .withStartupAttempts(3)
                .withLogConsumer(new ContainerLogConsumer(getClass().getSimpleName(), argumentName))
                .withStartupTimeout(Duration.ofSeconds(30))
                .waitingFor(Wait.forListeningPort())
                .withReuse(false);

        try {
            nginxContainer.start();
        } catch (Exception e) {
            stopQuietly();
            throw e;
        }
    }

    /**
     * Returns whether the Nginx container is currently running.
     *
     * @return {@code true} if the container has been started and not yet stopped
     */
    public boolean isRunning() {
        return nginxContainer != null && nginxContainer.isRunning();
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
     * Returns the underlying Nginx container instance for direct access, e.g. to
     * retrieve mapped ports.
     *
     * @return the Testcontainers Nginx container
     */
    public NginxContainer<?> getNginxContainer() {
        return nginxContainer;
    }

    /**
     * Stops the Nginx container silently, suppressing any exceptions. If this
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

    private void stopQuietly() {
        if (nginxContainer != null) {
            try {
                nginxContainer.stop();
            } catch (Exception ignored) {
                // Intentionally suppress stop exception to preserve original cause
            } finally {
                nginxContainer = null;
            }
        }
    }

    /**
     * Creates one {@link NginxTestEnvironment} per Nginx Docker image listed in the
     * {@code /docker-images.txt} classpath resource.
     *
     * @return list of test environments, one per image version
     * @throws IOException if the resource file cannot be read
     */
    public static List<NginxTestEnvironment> createTestEnvironments() throws IOException {
        return Resource.load(NginxTestEnvironment.class, "/docker-images.txt").stream()
                .map(NginxTestEnvironment::new)
                .toList();
    }
}

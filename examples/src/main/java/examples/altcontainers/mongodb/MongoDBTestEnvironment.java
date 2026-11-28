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

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import examples.altcontainers.util.ContainerLogConsumer;
import examples.support.Resource;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.altcontainers.api.Container;
import org.altcontainers.api.Network;
import org.bson.Document;

/**
 * Manages the lifecycle of a MongoDB container for parameterized integration tests.
 * Starts a single-node MongoDB instance inside a Docker container and provides
 * the connection string for client access.
 *
 * <p>Supports two initialization modes:
 * <ul>
 *   <li>{@link #initialize()} — creates and owns the Docker network; {@link #close()}
 *       stops both the container and the network.</li>
 *   <li>{@link #initialize(Network)} — attaches to a caller-owned network;
 *       {@link #close()} stops only the container.</li>
 * </ul>
 *
 * <p>After the container starts, the driver polls the server until it answers
 * commands; {@code mongod} binds its port before it can serve requests, so the
 * container's readiness condition alone is not enough for clients.
 *
 * <p>The container is stopped silently on failure during initialization.
 */
public class MongoDBTestEnvironment implements AutoCloseable {

    /**
     * How long the driver keeps probing for a server that answers commands.
     */
    private static final Duration READY_TIMEOUT = Duration.ofMinutes(2);

    private static final long READY_POLL_MILLIS = 500L;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    private final String dockerImageName;

    private final String argumentName;

    private volatile Container container;

    private volatile Network network;

    private volatile boolean ownsNetwork;

    /**
     * Creates a test environment for the given Docker image.
     *
     * @param dockerImageName the MongoDB Docker image (e.g. {@code "mongo:8.2.5"})
     */
    public MongoDBTestEnvironment(final String dockerImageName) {
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
     * Creates a new Docker network and starts the MongoDB container on it. The network
     * is owned by this environment; {@link #close()} will stop both the container
     * and the network.
     *
     * @see #initialize(Network)
     */
    public void initialize() {
        initialize(Network.create(), true);
    }

    /**
     * Starts the MongoDB container on the given Docker network and waits for the
     * database to accept commands. The caller owns the network; {@link #close()}
     * stops only the container.
     *
     * <p>If startup or readiness probing fails, the container is stopped silently
     * and diagnostics are printed before the exception is re-thrown. The thread
     * interrupt flag is restored if the failure was caused by an
     * {@link InterruptedException}.
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

        final var spec = MongoDBContainerSpec.builder(dockerImageName)
                .network(network, networkAlias())
                .outputConsumer(new ContainerLogConsumer(getClass().getSimpleName(), argumentName))
                .build();

        try {
            container = Container.create(spec);
            awaitServerReady();
        } catch (Exception e) {
            logSetupFailureDiagnostics(e);
            stopQuietly();
            closeNetworkIfOwned();
            throw e;
        }
    }

    /**
     * Polls the server until it answers commands, or {@link #READY_TIMEOUT} elapses.
     */
    private void awaitServerReady() {
        final var deadline = System.nanoTime() + READY_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            if (isServerReady()) {
                return;
            }

            try {
                Thread.sleep(READY_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for MongoDB readiness", e);
            }
        }

        throw new IllegalStateException("MongoDB did not accept commands within " + READY_TIMEOUT);
    }

    /**
     * Probes the server by listing database names with a short server-selection timeout.
     *
     * @return {@code true} if the server answered the command
     */
    private boolean isServerReady() {
        try (var mongoClient = createMongoClient()) {
            mongoClient.listDatabaseNames().first();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private MongoClient createMongoClient() {
        final var settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(getConnectionString()))
                .applyToClusterSettings(
                        builder -> builder.serverSelectionTimeout(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .build();

        return MongoClients.create(settings);
    }

    /**
     * Returns whether the MongoDB container is currently running.
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
     * Returns the MongoDB connection string for connecting clients to this instance.
     *
     * @return the connection string (e.g. {@code "mongodb://localhost:27017"})
     * @throws IllegalStateException if called before {@link #initialize()} or {@link #initialize(Network)}
     */
    public String getConnectionString() {
        final var current = container;
        if (current == null) {
            throw new IllegalStateException("getConnectionString() called before initialize()");
        }

        return "mongodb://" + current.host() + ":" + current.hostPort(MongoDBContainerSpec.MONGODB_PORT);
    }

    /**
     * Stops the MongoDB container silently, suppressing any exceptions. If this
     * environment owns the Docker network (created via {@link #initialize()}), the
     * network is also closed. Safe to call multiple times or when the container was
     * never started.
     */
    public void close() {
        stopQuietly();
        closeNetworkIfOwned();
    }

    /**
     * Logs diagnostic information when the MongoDB container fails to start or become
     * ready, to aid debugging CI flakes.
     *
     * <p>All diagnostic collection is best-effort; exceptions during diagnostics are
     * silently suppressed to avoid masking the original failure. Altcontainers does not
     * expose container logs, so only container state, a driver probe, and the exception
     * details are reported.
     *
     * @param cause the original exception that triggered the failure
     */
    private void logSetupFailureDiagnostics(final Exception cause) {
        System.err.println("=== MongoDB setup failure diagnostics for [" + argumentName + "] ===");

        logContainerState();
        logHelloResponse();
        logExceptionDetails(cause);

        System.err.println("=== End diagnostics for [" + argumentName + "] ===");
    }

    private void logContainerState() {
        try {
            final var current = container;
            if (current != null) {
                System.err.println("Container id: " + current.id());
                System.err.println("Container running: " + current.isRunning());
            }
        } catch (Exception e) {
            System.err.println("Unable to collect container state: " + e.getMessage());
        }
    }

    private void logHelloResponse() {
        try {
            final var current = container;
            if (current != null && current.isRunning()) {
                System.err.println("Attempting 'db.hello()' via driver ...");
                try (var mongoClient = createMongoClient()) {
                    var helloResult = mongoClient.getDatabase("admin").runCommand(new Document("hello", 1));
                    System.err.println("  hello response: " + helloResult.toJson());
                }
            }
        } catch (Exception e) {
            System.err.println("Unable to run hello(): " + e.getMessage());
        }
    }

    private void logExceptionDetails(final Exception cause) {
        System.err.println("Setup exception type: " + cause.getClass().getName());
        System.err.println("Setup exception message: " + cause.getMessage());
        var sw = new StringWriter();
        cause.printStackTrace(new PrintWriter(sw));
        System.err.println("Setup exception stack trace (first 20 lines):");
        final var traceLines = sw.toString().split("\n");
        for (int i = 0; i < Math.min(20, traceLines.length); i++) {
            System.err.println("  " + traceLines[i]);
        }
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

    /**
     * Creates one {@link MongoDBTestEnvironment} per MongoDB Docker image listed in the
     * {@code /docker-images.txt} classpath resource.
     *
     * @return list of test environments, one per image version
     * @throws IOException if the resource file cannot be read
     */
    public static List<MongoDBTestEnvironment> createTestEnvironments() throws IOException {
        return Resource.load(MongoDBTestEnvironment.class, "/docker-images.txt").stream()
                .map(MongoDBTestEnvironment::new)
                .toList();
    }
}

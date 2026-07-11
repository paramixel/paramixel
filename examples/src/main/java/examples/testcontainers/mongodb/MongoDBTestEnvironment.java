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

import com.mongodb.client.MongoClients;
import examples.support.NetworkFactory;
import examples.support.Resource;
import examples.testcontainers.util.ContainerLogConsumer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.bson.Document;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

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
 * <p>The container is stopped silently on failure during initialization.
 */
public class MongoDBTestEnvironment implements AutoCloseable {

    private final String dockerImageName;

    private final String argumentName;

    private volatile MongoDBContainer mongoDBContainer;

    private volatile Network network;

    private volatile boolean ownsNetwork;

    /**
     * Creates a test environment for the given Docker image.
     *
     * @param dockerImageName the MongoDB Docker image (e.g. {@code "mongo:7.0"})
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
        initialize(NetworkFactory.createNetwork(), true);
    }

    /**
     * Starts the MongoDB container on the given Docker network and waits for it to
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
        this.network = Objects.requireNonNull(network, "network is null");
        this.ownsNetwork = ownsNetwork;
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse(dockerImageName))
                .withNetwork(network)
                .withStartupAttempts(3)
                .withLogConsumer(new ContainerLogConsumer(getClass().getSimpleName(), argumentName))
                .withStartupTimeout(Duration.ofSeconds(60))
                .withReuse(false);

        try {
            mongoDBContainer.start();
            verifyWritablePrimary();
        } catch (Exception e) {
            logSetupFailureDiagnostics(e);
            stopQuietly();
            throw e;
        }
    }

    /**
     * Verifies that the MongoDB replica set has a writable primary by connecting
     * with the driver and checking {@code hello.isWritablePrimary}. Retries up to
     * 5 times with 1-second intervals to tolerate brief post-election transitions.
     *
     * <p>The primary election window for a single-node replica set is typically
     * sub-second, so 5 attempts × 1 second covers CI variability without masking
     * real failures.
     *
     * @throws IllegalStateException if the replica set does not become writable
     *                               within the retry window
     */
    private void verifyWritablePrimary() {
        int maxAttempts = 5;
        long sleepMs = 1000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try (var mongoClient = MongoClients.create(mongoDBContainer.getConnectionString())) {
                var database = mongoClient.getDatabase("admin");
                var helloResult = database.runCommand(new Document("hello", 1));

                if (Boolean.TRUE.equals(helloResult.getBoolean("isWritablePrimary"))) {
                    return;
                }

                lastException = new IllegalStateException(
                        "MongoDB not yet writable primary (attempt " + attempt + "/" + maxAttempts + "): "
                                + "isWritablePrimary=" + helloResult.getBoolean("isWritablePrimary")
                                + ", me=" + helloResult.getString("me")
                                + ", setName=" + helloResult.getString("setName"));
            } catch (Exception e) {
                lastException = e;
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for MongoDB readiness", ie);
                }
            }
        }

        throw new IllegalStateException(
                "MongoDB replica set did not become writable primary within " + (maxAttempts * sleepMs / 1000)
                        + " seconds",
                lastException);
    }

    /**
     * Logs diagnostic information when the MongoDB container fails to start or become
     * ready. Attempts to capture container logs, replica set status, and hello output
     * to aid debugging CI flakes.
     *
     * <p>All diagnostic collection is best-effort; exceptions during diagnostics are
     * silently suppressed to avoid masking the original failure.
     *
     * @param cause the original exception that triggered the failure
     */
    private void logSetupFailureDiagnostics(Exception cause) {
        System.err.println("=== MongoDB setup failure diagnostics for [" + argumentName + "] ===");

        logContainerState();
        logReplicaSetDiagnostics();
        logExceptionDetails(cause);

        System.err.println("=== End diagnostics for [" + argumentName + "] ===");
    }

    private void logContainerState() {
        try {
            if (mongoDBContainer != null) {
                System.err.println("Container running: " + mongoDBContainer.isRunning());
                System.err.println("Container id: " + mongoDBContainer.getContainerId());
                if (mongoDBContainer.isRunning()) {
                    System.err.println("Container logs (last 30 lines):");
                    String logs = mongoDBContainer.getLogs();
                    if (logs != null && !logs.isEmpty()) {
                        String[] lines = logs.split("\n");
                        int start = Math.max(0, lines.length - 30);
                        for (int i = start; i < lines.length; i++) {
                            System.err.println("  [log] " + lines[i]);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            System.err.println("Unable to collect container state: " + ignored.getMessage());
        }
    }

    private void logReplicaSetDiagnostics() {
        try {
            if (mongoDBContainer != null && mongoDBContainer.isRunning()) {
                System.err.println("Attempting 'db.hello()' via driver ...");
                try (var mongoClient = MongoClients.create(mongoDBContainer.getConnectionString())) {
                    var helloResult = mongoClient.getDatabase("admin").runCommand(new Document("hello", 1));
                    System.err.println("  hello response: " + helloResult.toJson());
                }
            }
        } catch (Exception ignored) {
            System.err.println("Unable to run hello(): " + ignored.getMessage());
        }

        try {
            if (mongoDBContainer != null && mongoDBContainer.isRunning()) {
                System.err.println("Attempting 'rs.status()' via driver ...");
                try (var mongoClient = MongoClients.create(mongoDBContainer.getConnectionString())) {
                    var rsStatus = mongoClient.getDatabase("admin").runCommand(new Document("replSetGetStatus", 1));
                    System.err.println("  rs.status() response: " + rsStatus.toJson());
                }
            }
        } catch (Exception ignored) {
            System.err.println("Unable to run rs.status(): " + ignored.getMessage());
        }
    }

    private void logExceptionDetails(Exception cause) {
        System.err.println("Setup exception type: " + cause.getClass().getName());
        System.err.println("Setup exception message: " + cause.getMessage());
        var sw = new StringWriter();
        cause.printStackTrace(new PrintWriter(sw));
        System.err.println("Setup exception stack trace (first 20 lines):");
        String[] traceLines = sw.toString().split("\n");
        for (int i = 0; i < Math.min(20, traceLines.length); i++) {
            System.err.println("  " + traceLines[i]);
        }
    }

    /**
     * Returns whether the MongoDB container is currently running.
     *
     * @return {@code true} if the container has been started and not yet stopped
     */
    public boolean isRunning() {
        return mongoDBContainer != null && mongoDBContainer.isRunning();
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
     * Returns the MongoDB connection string for connecting clients to this instance.
     *
     * @return the connection string (e.g. {@code "mongodb://localhost:27017"})
     * @throws IllegalStateException if called before {@link #initialize()} or {@link #initialize(Network)}
     */
    public String getConnectionString() {
        return mongoDBContainer.getConnectionString();
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
        if (mongoDBContainer != null) {
            try {
                mongoDBContainer.stop();
            } catch (Exception ignored) {
                // Intentionally suppress stop exception to preserve original cause
            } finally {
                mongoDBContainer = null;
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

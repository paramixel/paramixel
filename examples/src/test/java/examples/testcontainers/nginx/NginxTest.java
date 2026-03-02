/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import examples.support.Logger;
import examples.testcontainers.util.CleanupExecutor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentSupplierContext;
import org.paramixel.api.Paramixel;
import org.testcontainers.containers.Network;

@Paramixel.TestClass
/**
 * Demonstrates using Paramixel to run Testcontainers-backed Nginx tests across environments.
 */
public class NginxTest {

    /** Logger for lifecycle output. */
    private static final Logger LOGGER = Logger.createLogger(NginxTest.class);

    /** Store key for the shared Testcontainers {@link Network}. */
    private static final String NETWORK = "network";

    /**
     * Supplies {@link NginxTestEnvironment} instances as test arguments.
     *
     * @param argumentSupplierContext the argument supplier context
     * @throws IOException if environment creation fails
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) throws IOException {
        NginxTestEnvironment.createTestEnvironments().forEach(argumentSupplierContext::addArgument);
    }

    /**
     * Initializes the Nginx environment for the current argument.
     *
     * @param context the argument context
     */
    @Paramixel.BeforeAll
    public void initializeTestEnvironment(final @NonNull ArgumentContext context) {
        NginxTestEnvironment testEnvironment = context.getArgument(NginxTestEnvironment.class);
        LOGGER.info("[%s] initialize test environment ...", testEnvironment.getName());

        Network network = Network.newNetwork();
        network.getId();

        context.getClassContext().getStore().put(NETWORK, network);
        testEnvironment.initialize(network);

        assertThat(testEnvironment.isRunning()).isTrue();
    }

    /**
     * Performs a simple HTTP GET against the mapped Nginx port and asserts the welcome page.
     *
     * @param context the argument context
     * @throws Throwable if the request fails
     */
    @Paramixel.Test
    public void testGet(final @NonNull ArgumentContext context) throws Throwable {
        NginxTestEnvironment testEnvironment = context.getArgument(NginxTestEnvironment.class);
        LOGGER.info("[%s] testing testGet() ...", testEnvironment.getName());

        int port = testEnvironment.getNginxContainer().getMappedPort(80);

        String content = doGet("http://localhost:" + port);

        assertThat(content).contains("Welcome to nginx!");
    }

    /**
     * Destroys the Nginx environment and closes the shared network.
     *
     * @param context the argument context
     * @throws Throwable if cleanup fails
     */
    @Paramixel.AfterAll
    public void destroyTestEnvironment(final @NonNull ArgumentContext context) throws Throwable {
        NginxTestEnvironment testEnvironment = context.getArgument(NginxTestEnvironment.class);
        LOGGER.info("[%s] destroy test environment ...", testEnvironment.getName());

        new CleanupExecutor()
                .addTask(testEnvironment::destroy)
                .addTaskIfPresent(
                        () -> context.getClassContext().getStore().remove(NETWORK, Network.class), Network::close)
                .throwIfFailed();
    }

    /**
     * Performs a blocking HTTP GET and returns the response body.
     *
     * @param url URL to fetch
     * @return response body as a string
     * @throws Throwable if the connection or read fails
     */
    private static String doGet(final @NonNull String url) throws Throwable {
        StringBuilder result = new StringBuilder();
        URLConnection connection = URI.create(url).toURL().openConnection();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }
}

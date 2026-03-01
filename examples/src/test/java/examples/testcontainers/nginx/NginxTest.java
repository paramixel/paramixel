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
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;
import org.testcontainers.containers.Network;

@Paramixel.TestClass
public class NginxTest {

    private static final Logger LOGGER = Logger.createLogger(NginxTest.class);

    private static final String NETWORK = "network";

    @Paramixel.ArgumentSupplier(parallelism = Integer.MAX_VALUE)
    public static Stream<NginxTestEnvironment> arguments() throws IOException {
        return NginxTestEnvironment.createTestEnvironments();
    }

    @Paramixel.BeforeAll
    public void initializeTestEnvironment(final @NonNull ArgumentContext argumentContext) {
        NginxTestEnvironment testEnvironment = argumentContext.getArgument(NginxTestEnvironment.class);
        LOGGER.info("[%s] initialize test environment ...", testEnvironment.getName());

        Network network = Network.newNetwork();
        network.getId();

        argumentContext.getClassContext().getStore().put(NETWORK, network);
        testEnvironment.initialize(network);

        assertThat(testEnvironment.isRunning()).isTrue();
    }

    @Paramixel.Test
    public void testGet(final @NonNull ArgumentContext argumentContext) throws Throwable {
        NginxTestEnvironment testEnvironment = argumentContext.getArgument(NginxTestEnvironment.class);
        LOGGER.info("[%s] testing testGet() ...", testEnvironment.getName());

        int port = testEnvironment.getNginxContainer().getMappedPort(80);

        String content = doGet("http://localhost:" + port);

        assertThat(content).contains("Welcome to nginx!");
    }

    @Paramixel.AfterAll
    public void destroyTestEnvironment(final @NonNull ArgumentContext argumentContext) throws Throwable {
        NginxTestEnvironment testEnvironment = argumentContext.getArgument(NginxTestEnvironment.class);
        LOGGER.info("[%s] destroy test environment ...", testEnvironment.getName());

        new CleanupExecutor()
                .addTask(testEnvironment::destroy)
                .addTaskIfPresent(
                        () -> argumentContext.getClassContext().getStore().remove(NETWORK, Network.class),
                        Network::close)
                .throwIfFailed();
    }

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

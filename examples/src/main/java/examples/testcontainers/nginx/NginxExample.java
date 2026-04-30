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

import static org.assertj.core.api.Assertions.assertThat;

import examples.support.Logger;
import examples.support.NetworkFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import org.paramixel.core.Action;
import org.paramixel.core.ConsoleRunner;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.support.Cleanup;
import org.testcontainers.containers.Network;

public class NginxExample {

    private static class TestAttachment {
        public Network network;
        public NginxTestEnvironment environment;
    }

    private static final Logger LOGGER = Logger.createLogger(NginxExample.class);

    public static void main(String[] args) throws Throwable {
        ConsoleRunner.runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() throws Throwable {
        return Parallel.of(
                "NginxExample",
                NginxTestEnvironment.createTestEnvironments().stream()
                        .map(NginxExample::createLifecycleAction)
                        .toList());
    }

    private static Action createLifecycleAction(NginxTestEnvironment environment) {
        Action testAction = Direct.of("get", context -> {
            var lifecycleContext = context.findContext(1).orElseThrow();
            TestAttachment testAttachment = lifecycleContext
                    .getAttachment()
                    .flatMap(a -> a.to(TestAttachment.class))
                    .orElseThrow();

            LOGGER.info("[%s] testing GET ...", testAttachment.environment.name());

            int port = testAttachment.environment.getNginxContainer().getMappedPort(80);
            String content = doGet("http://localhost:" + port);
            assertThat(content).contains("Welcome to nginx!");
        });

        return Lifecycle.of(
                environment.name(),
                Direct.of("before", context -> {
                    LOGGER.info("[%s] initialize test environment ...", environment.name());

                    Network network = NetworkFactory.createNetwork();

                    environment.initialize(network);
                    assertThat(environment.isRunning()).isTrue();

                    TestAttachment testAttachment = new TestAttachment();
                    testAttachment.network = network;
                    testAttachment.environment = environment;

                    context.setAttachment(testAttachment);
                }),
                testAction,
                Direct.of("after", context -> {
                    LOGGER.info("[%s] destroy test environment ...", environment.name());

                    TestAttachment testAttachment = context.removeAttachment()
                            .flatMap(a -> a.to(TestAttachment.class))
                            .orElse(null);

                    if (testAttachment != null) {
                        new Cleanup(Cleanup.Mode.FORWARD)
                                .addCloseable(testAttachment.environment)
                                .addCloseable(testAttachment.network)
                                .runAndThrow();
                    }
                }));
    }

    private static String doGet(final String url) throws Exception {
        var result = new StringBuilder();
        URLConnection connection = URI.create(url).toURL().openConnection();

        try (var reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }
}

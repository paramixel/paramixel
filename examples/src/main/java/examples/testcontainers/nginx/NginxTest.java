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
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.support.Cleanup;
import org.testcontainers.containers.Network;

public class NginxTest {

    private static final String NETWORK = "network";
    private static final String ENVIRONMENT = "environment";
    private static final Logger LOGGER = Logger.createLogger(NginxTest.class);

    public static void main(String[] args) throws Throwable {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() throws Throwable {
        var parallelBuilder = Parallel.builder("NginxExample");
        for (NginxTestEnvironment environment : NginxTestEnvironment.createTestEnvironments()) {
            Action argumentContainer = argument(environment);
            parallelBuilder.child(argumentContainer);
        }
        return parallelBuilder.build();
    }

    private static Action argument(NginxTestEnvironment environment) {
        Action setUp = setUp(environment);
        Action test = test();
        Action tearDown = tearDown(environment);

        return Container.builder(environment.name())
                .before(setUp)
                .child(test)
                .after(tearDown)
                .build();
    }

    private static Action setUp(NginxTestEnvironment environment) {
        return Direct.builder("setUp")
                .runnable(context -> {
                    LOGGER.info("[%s] initialize test environment ...", environment.name());

                    Network network = NetworkFactory.createNetwork();

                    environment.initialize(network);
                    assertThat(environment.isRunning()).isTrue();

                    context.getStore().put(NETWORK, network);
                    context.getStore().put(ENVIRONMENT, environment);
                })
                .build();
    }

    private static Action test() {
        return Direct.builder("get")
                .runnable(context -> {
                    NginxTestEnvironment testEnvironment = context.getAncestor("../")
                            .getStore()
                            .get(ENVIRONMENT, NginxTestEnvironment.class)
                            .orElseThrow();

                    LOGGER.info("[%s] testing GET ...", testEnvironment.name());

                    int port = testEnvironment.getNginxContainer().getMappedPort(80);
                    String content = doGet("http://localhost:" + port);
                    assertThat(content).contains("Welcome to nginx!");
                })
                .build();
    }

    private static Action tearDown(NginxTestEnvironment environment) {
        return Direct.builder("tearDown")
                .runnable(context -> {
                    LOGGER.info("[%s] destroy test environment ...", environment.name());

                    var removedNetwork = context.getStore().remove(NETWORK, Network.class);
                    var removedEnvironment = context.getStore().remove(ENVIRONMENT, NginxTestEnvironment.class);
                    if (removedNetwork.isPresent() && removedEnvironment.isPresent()) {
                        Cleanup.of(Cleanup.Mode.FORWARD)
                                .addCloseable(removedEnvironment.orElseThrow())
                                .addCloseable(removedNetwork.orElseThrow())
                                .runAndThrow();
                    }
                })
                .build();
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

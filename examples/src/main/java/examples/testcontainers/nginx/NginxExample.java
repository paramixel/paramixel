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

import examples.support.CleanupRunner;
import examples.support.Logger;
import examples.support.NetworkFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Parallel;
import org.testcontainers.containers.Network;

public class NginxExample {

    private static final Logger LOGGER = Logger.createLogger(NginxExample.class);

    record Attachment(Network network, NginxTestEnvironment environment) {}

    @Paramixel.ActionFactory
    public static Action actionFactory() throws Throwable {
        var argumentActions = new ArrayList<Action>();

        for (NginxTestEnvironment environment : NginxTestEnvironment.createTestEnvironments()) {
            Action testAction = Direct.of("get", context -> {
                var lifecycleContext = context.parent().orElseThrow();
                Attachment attachment =
                        lifecycleContext.attachment(Attachment.class).orElseThrow();
                LOGGER.info("[%s] testing GET ...", attachment.environment().name());

                int port = attachment.environment().getNginxContainer().getMappedPort(80);
                String content = doGet("http://localhost:" + port);
                assertThat(content).contains("Welcome to nginx!");
            });

            Action lifecycleAction = Lifecycle.of(
                    environment.name(),
                    context -> {
                        LOGGER.info("[%s] initialize test environment ...", environment.name());

                        Network network = NetworkFactory.createNetwork();

                        environment.initialize(network);
                        assertThat(environment.isRunning()).isTrue();

                        context.setAttachment(new Attachment(network, environment));
                    },
                    testAction,
                    context -> {
                        LOGGER.info("[%s] destroy test environment ...", environment.name());

                        new CleanupRunner()
                                .addTask(environment::destroy)
                                .addTask(context.removeAttachment(), attachment -> {
                                    if (attachment instanceof Attachment a && a.network() != null) {
                                        a.network().close();
                                    }
                                })
                                .executeAndThrow();
                    });

            argumentActions.add(lifecycleAction);
        }

        return Parallel.of("NginxExample", argumentActions);
    }

    public static void main(String[] args) throws Throwable {
        Result result = Runner.builder().build().run(actionFactory());
        int exitCode = result.status() == Result.Status.PASS ? 0 : 1;
        System.exit(exitCode);
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

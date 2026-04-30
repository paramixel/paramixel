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

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClients;
import examples.support.Logger;
import examples.support.NetworkFactory;
import org.bson.Document;
import org.paramixel.core.Action;
import org.paramixel.core.ConsoleRunner;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.support.Cleanup;
import org.testcontainers.containers.Network;

public class MongoDBExample {

    private static class TestAttachment {
        public Network network;
        public MongoDBTestEnvironment environment;
    }

    private static final Logger LOGGER = Logger.createLogger(MongoDBExample.class);

    public static void main(String[] args) throws Throwable {
        ConsoleRunner.runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() throws Throwable {
        return Parallel.of(
                "MongoDBExample",
                MongoDBTestEnvironment.createTestEnvironments().stream()
                        .map(MongoDBExample::createLifecycleAction)
                        .toList());
    }

    private static Action createLifecycleAction(MongoDBTestEnvironment environment) {
        Action testAction = Direct.of("test insert and query", context -> {
            var lifecycleContext = context.findContext(1).orElseThrow();
            TestAttachment testAttachment = lifecycleContext
                    .getAttachment()
                    .flatMap(a -> a.to(TestAttachment.class))
                    .orElseThrow();

            LOGGER.info("[%s] testing insert and query ...", testAttachment.environment.name());

            try (var mongoClient = MongoClients.create(testAttachment.environment.getConnectionString())) {
                var database = mongoClient.getDatabase("testdb");
                var collection = database.getCollection("testcol");
                collection.insertOne(new Document("key", "value"));

                Document found = collection.find(new Document("key", "value")).first();

                assertThat(found).isNotNull();
                assertThat(found.getString("key")).isEqualTo("value");
            }
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
                        Cleanup.of(Cleanup.Mode.FORWARD)
                                .addCloseable(testAttachment.environment)
                                .addCloseable(testAttachment.network)
                                .runAndThrow();
                    }
                }));
    }
}

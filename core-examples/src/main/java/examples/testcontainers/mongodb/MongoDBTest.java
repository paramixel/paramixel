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
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Value;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.support.Cleanup;
import org.testcontainers.containers.Network;

public class MongoDBTest {

    private static final String NETWORK = "network";
    private static final String ENVIRONMENT = "environment";
    private static final Logger LOGGER = Logger.createLogger(MongoDBTest.class);

    public static void main(String[] args) throws Throwable {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() throws Throwable {
        return Parallel.of(
                "MongoDBExample",
                MongoDBTestEnvironment.createTestEnvironments().stream()
                        .map(MongoDBTest::createLifecycleAction)
                        .toList());
    }

    private static Action createLifecycleAction(MongoDBTestEnvironment environment) {
        Action testAction = Direct.of("test insert and query", context -> {
            var lifecycleContext = context.findAncestor(1).orElseThrow();
            MongoDBTestEnvironment testEnvironment =
                    lifecycleContext.getStore().get(ENVIRONMENT).orElseThrow().cast(MongoDBTestEnvironment.class);

            LOGGER.info("[%s] testing insert and query ...", testEnvironment.name());

            try (var mongoClient = MongoClients.create(testEnvironment.getConnectionString())) {
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

                    context.getStore().put(NETWORK, Value.of(network));
                    context.getStore().put(ENVIRONMENT, Value.of(environment));
                }),
                testAction,
                Direct.of("after", context -> {
                    LOGGER.info("[%s] destroy test environment ...", environment.name());

                    var removedNetwork = context.getStore().remove(NETWORK);
                    var removedEnvironment = context.getStore().remove(ENVIRONMENT);
                    if (removedNetwork.isPresent() && removedEnvironment.isPresent()) {
                        Network network = removedNetwork.orElseThrow().cast(Network.class);
                        MongoDBTestEnvironment testEnvironment =
                                removedEnvironment.orElseThrow().cast(MongoDBTestEnvironment.class);

                        Cleanup.of(Cleanup.Mode.FORWARD)
                                .addCloseable(testEnvironment)
                                .addCloseable(network)
                                .runAndThrow();
                    }
                }));
    }
}

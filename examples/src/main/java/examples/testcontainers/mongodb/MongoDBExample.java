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
import examples.support.CleanupRunner;
import examples.support.Logger;
import examples.support.NetworkFactory;
import java.util.ArrayList;
import org.bson.Document;
import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Parallel;
import org.testcontainers.containers.Network;

public class MongoDBExample {

    private static final Logger LOGGER = Logger.createLogger(MongoDBExample.class);

    record Attachment(Network network, MongoDBTestEnvironment environment) {}

    @Paramixel.ActionFactory
    public static Action actionFactory() throws Throwable {
        var argumentActions = new ArrayList<Action>();

        for (MongoDBTestEnvironment environment : MongoDBTestEnvironment.createTestEnvironments()) {
            Action testAction = Direct.of("test insert and query", context -> {
                var lifecycleContext = context.parent().orElseThrow();
                Attachment attachment =
                        lifecycleContext.attachment(Attachment.class).orElseThrow();
                LOGGER.info(
                        "[%s] testing insert and query ...",
                        attachment.environment().name());

                try (var mongoClient =
                        MongoClients.create(attachment.environment().getConnectionString())) {
                    var database = mongoClient.getDatabase("testdb");
                    var collection = database.getCollection("testcol");
                    collection.insertOne(new Document("key", "value"));

                    Document found =
                            collection.find(new Document("key", "value")).first();

                    assertThat(found).isNotNull();
                    assertThat(found.getString("key")).isEqualTo("value");
                }
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

        return Parallel.of("MongoDBExample", argumentActions);
    }

    public static void main(String[] args) throws Throwable {
        Result result = Runner.builder().build().run(actionFactory());
        int exitCode = result.status() == Result.Status.PASS ? 0 : 1;
        System.exit(exitCode);
    }
}
